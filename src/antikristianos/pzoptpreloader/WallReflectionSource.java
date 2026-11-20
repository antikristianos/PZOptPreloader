package antikristianos.pzoptpreloader;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.opengl.*;
import zombie.core.SpriteRenderer;
import zombie.core.textures.*;
import zombie.iso.*;
import zombie.iso.fboRenderChunk.*;
import zombie.iso.objects.*;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.SpriteDetails.IsoObjectType;

/** A physical wall layer built only while the corresponding native chunk is rebuilt. */
public final class WallReflectionSource {
    private static boolean sourceGpu;
    private static final ThreadLocal<CaptureContext> CPU=ThreadLocal.withInitial(CaptureContext::new);
    private static final java.util.concurrent.atomic.AtomicInteger ACTIVE_CPU=new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean failed;
    private static Source renderingSource, pendingComposite;
    private static final Map<Texture,Source> SOURCES=new IdentityHashMap<>();
    private static final Map<Integer,Source> BY_COLOUR=new HashMap<>();
    private static final Map<Integer,Source> BY_DEPTH=new HashMap<>();
    private record TreeMaterial(Texture trunk,Texture foliage,int r,int g,int b){ }
    private static final class TreeHolders {
        TreeMaterial material;final Set<Source> sources=Collections.newSetFromMap(new IdentityHashMap<>());
    }
    private static final Map<IsoTree,TreeHolders> TREE_HOLDERS=new IdentityHashMap<>(); // render thread only
    private static final java.util.concurrent.ConcurrentHashMap<Texture,Boolean> WANTED=new java.util.concurrent.ConcurrentHashMap<>(); // render thread
    private record CompositeUniforms(boolean composite,int on){ }
    private static final Map<Integer,CompositeUniforms> COMPOSITES=new HashMap<>();
    private static long captures,sourceDraws,compositeDraws,nativeBegins,nativeFrees,rebuilds;
    private static final java.util.concurrent.ConcurrentLinkedQueue<Source> REBUILD=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static long drainFrame=Long.MIN_VALUE;
    private WallReflectionSource() { }

    /** Only calls made by the explicit physical-source pass bypass camera cutaway. */
    public static int cutaway(int value){return sourcePass()?0:value;}
    public static boolean translucentTree(boolean value){return !sourcePass()&&value;}
    public static boolean sourcePass(){return ACTIVE_CPU.get()!=0&&CPU.get().active;}
    /** Physical geometry is not evidence that its mirror receiver is camera-visible. */
    public static void mirrorWallDrawn(IsoObject object,int cutaway){if(!sourcePass())Access.mirrorWallDrawn(object,cutaway);}
    private static final class CaptureContext {
        boolean active;IsoObject object;int player;ObjectRenderInfo info;Capture capture;
        FBORenderChunk treeOwner,capturedOwner;
        final Set<IsoTree> neighbourTrees=Collections.newSetFromMap(new IdentityHashMap<>());
        Object treeRect,textureRect;
        final FrameCommands[] commands={new FrameCommands(),new FrameCommands(),new FrameCommands()};
    }
    /** Reuse the native bounded neighbour visit, including candidates rejected only by camera fading. */
    public static void beginCachedTrees(IsoChunk chunk){
        CaptureContext c=CPU.get();c.neighbourTrees.clear();c.treeOwner=c.capturedOwner=null;
        FBORenderChunk owner=FBORenderChunkManager.instance.renderChunk;
        if(!failed&&owner!=null&&owner.chunk==chunk&&WANTED.containsKey(owner.depth))c.treeOwner=owner;
    }
    public static void recordCachedTree(IsoTree tree){
        CaptureContext c=CPU.get();FBORenderChunk owner=c.treeOwner;
        if(owner!=null&&physical(tree)&&tree.square!=null&&tree.square.chunk!=owner.chunk
            &&tree.square.z>=owner.getMinLevel()&&tree.square.z<=owner.getTopLevel())c.neighbourTrees.add(tree);
    }
    /** Capture before the unchanged cached drawer writes its sorting depths, not after it. */
    public static TextureDraw queueCachedTrees(SpriteRenderer renderer,TextureDraw.GenericDrawer drawer,Object cell,IsoChunk chunk){
        CaptureContext c=CPU.get();FBORenderChunk owner=c.treeOwner;
        if(owner!=null&&owner==FBORenderChunkManager.instance.renderChunk){
            captureLevel(true,cell,chunk,owner.getTopLevel());c.capturedOwner=owner;
        }
        return renderer.drawGeneric(drawer);
    }
    /** Only the current producer sees opaque values; native object arrays remain untouched. */
    public static boolean localAlpha(IsoObject object,int player){if(ACTIVE_CPU.get()==0)return false;CaptureContext c=CPU.get();return c.active&&c.object==object&&c.player==player;}
    public static float alpha(float original,IsoObject object,int player){return localAlpha(object,player)?1.0f:original;}
    public static ObjectRenderInfo renderInfo(ObjectRenderInfo original,IsoObject object,int player){return localAlpha(object,player)?CPU.get().info:original;}
    public static long captures(){return captures;}
    public static long nativeBegins(){return nativeBegins;}
    public static long nativeFrees(){return nativeFrees;}
    public static long sourceDraws(){return sourceDraws;}
    public static long compositeDraws(){return compositeDraws;}

    /** Invoked before native endRenderChunkLevel, while the native bake coordinates remain active. */
    public static void captureLevel(boolean clearDirty,Object cell,IsoChunk chunk,int level){
        if(failed||!clearDirty||sourcePass()||chunk==null||!FBORenderChunkManager.instance.isCaching()||!enabled())return;
        FBORenderChunk owner=FBORenderChunkManager.instance.renderChunk;
        if(owner==null||owner.chunk!=chunk||level!=owner.getTopLevel()||!WANTED.containsKey(owner.depth))return;
        CaptureContext context=CPU.get();
        if(context.capturedOwner==owner){context.capturedOwner=null;return;}
        int player=IsoCamera.frameState.playerIndex;
        Capture command=commands().next();command.owner=owner;command.depth=owner.depth;command.colour=owner.tex;
        command.chunk=chunk;command.width=owner.w;command.height=owner.h;command.candidates=false;command.textures.ids.clear();command.treeMaterials.clear();
        SpriteRenderer.instance.drawGeneric(command.begin);
        boolean previousCircle=IsoGridSquare.circleStencil,previousLowLight=IsoObject.lowLightingQualityHack;
        IsoObject previousRendered=IsoObject.lastRendered,previousPreviousRendered=IsoObject.lastRenderedRendered;
        context.active=true;context.capture=command;ACTIVE_CPU.incrementAndGet();
        FBORenderTrees sourceTrees=null;
        try{
            for(int sourceLevel=owner.getMinLevel();sourceLevel<=owner.getTopLevel();sourceLevel++)
            for(int y=0;y<8;y++)for(int x=0;x<8;x++){
                IsoGridSquare square=chunk.getGridSquare(x,y,sourceLevel);if(square==null)continue;
                List<IsoObject> objects=square.getObjects();
                for(int i=0;i<objects.size();i++){
                    IsoObject object=objects.get(i);if(!physical(object))continue;command.candidates=true;
                    if(object instanceof IsoTree&&sourceTrees==null){sourceTrees=FBORenderTrees.alloc();sourceTrees.init();}
                    captureObject(context,sourceTrees,cell,object,player);
                }
            }
            if(context.treeOwner==owner)for(IsoTree tree:context.neighbourTrees){
                if(!physical(tree)||!Access.overlaps(cell,owner,tree,context))continue;
                command.candidates=true;if(sourceTrees==null){sourceTrees=FBORenderTrees.alloc();sourceTrees.init();}
                captureObject(context,sourceTrees,cell,tree,player);
            }
        }catch(Throwable error){fail(error);}finally{
            context.treeOwner=null;context.neighbourTrees.clear();
            context.object=null;context.info=null;context.capture=null;context.active=false;ACTIVE_CPU.decrementAndGet();IsoGridSquare.circleStencil=previousCircle;IsoObject.lowLightingQualityHack=previousLowLight;
            IsoObject.lastRendered=previousRendered;IsoObject.lastRenderedRendered=previousPreviousRendered;
            try{if(sourceTrees!=null){command.trees=sourceTrees;SpriteRenderer.instance.drawGeneric(command.tree);}}
            finally{SpriteRenderer.instance.drawGeneric(command.end);}
        }
    }
    private static void captureObject(CaptureContext context,FBORenderTrees trees,Object cell,IsoObject object,int player){
        ObjectRenderInfo info=object.getRenderInfo(player);
        float sx=object.sx,sy=object.sy;boolean noPicking=object.noPicking;
        boolean treeFlag=object instanceof IsoTree tree&&tree.renderFlag;
        try{
            ObjectRenderInfo local=new ObjectRenderInfo(object);local.layer=info.layer;
            local.renderX=info.renderX;local.renderY=info.renderY;local.renderWidth=info.renderWidth;local.renderHeight=info.renderHeight;
            local.renderScaleX=info.renderScaleX;local.renderScaleY=info.renderScaleY;
            local.targetAlpha=1.0f;local.renderAlpha=1.0f;
            context.info=local;context.object=object;context.player=player;
            if(object instanceof IsoTree){
                captureTree(trees,cell,object,player,context.capture.textures);
            }else Access.render(cell,object,wall(object));
            captures++;
        }finally{
            context.object=null;context.info=null;object.sx=sx;object.sy=sy;object.noPicking=noPicking;
            if(object instanceof IsoTree tree)tree.renderFlag=treeFlag;
        }
    }
    /** Native full-tree textures, in a private batch inside the physical FBO, without camera fade/stencil. */
    private static void captureTree(FBORenderTrees batch,Object cell,IsoObject object,int player,TreeTextureState textures){
        IsoSprite sprite=object.getSprite();Texture trunk=sprite.getTextureForCurrentFrame(object.getDir(),object),foliage=null;
        var attached=object.getAttachedAnimSprite();
        if(attached!=null&&!attached.isEmpty()){
            IsoSprite extra=attached.get(0).parentSprite;
            if(extra!=null)foliage=extra.getTextureForCurrentFrame(object.getDir(),object);
        }
        if(trunk==null&&foliage==null)return;
        textures.add(trunk);textures.add(foliage);
        ColorInfo light=Access.light(cell,player,object.square);
        CPU.get().capture.treeMaterials.put((IsoTree)object,new TreeMaterial(trunk,foliage,
            Float.floatToIntBits(light.r),Float.floatToIntBits(light.g),Float.floatToIntBits(light.b)));
        batch.addTree(trunk,foliage,object.square.x,object.square.y,object.square.z,
            light.r,light.g,light.b,1.0f,object.getObjectRenderEffectsToApply(),false,1.0f,false,1.0f);
    }
    private static boolean wall(IsoObject object){
        if(object==null||!object.getDoRender()||object.isSpriteInvisible())return false;
        IsoSprite sprite=object.getSprite();if(sprite==null)return false;
        IsoObjectType type=sprite.getTileType();
        return sprite.cutN||sprite.cutW||object.isWallSE()||type==IsoObjectType.doorFrN||type==IsoObjectType.doorFrW
            ||type==IsoObjectType.doorN||type==IsoObjectType.doorW||object instanceof IsoDoor
            ||object instanceof IsoThumpable thump&&thump.isDoor();
    }
    private static boolean physical(IsoObject object){
        return object!=null&&object.getDoRender()&&!object.isSpriteInvisible()&&object.getSprite()!=null&&!object.getSprite().solidfloor;
    }
    private static FrameCommands commands(){
        int index=SpriteRenderer.instance.getMainStateIndex();FrameCommands[] frames=CPU.get().commands;FrameCommands commands=frames[Math.floorMod(index,frames.length)];
        long frame=SpriteRenderer.instance.states.getPopulating().time;
        if(commands.frame!=frame){commands.frame=frame;commands.next=0;}return commands;
    }
    private static final class FrameCommands {
        final ArrayList<Capture> captures=new ArrayList<>();long frame=Long.MIN_VALUE;int next;
        Capture next(){if(next==captures.size())captures.add(new Capture());return captures.get(next++);}
    }
    private static final class Capture {
        FBORenderChunk owner;Texture depth,colour;IsoChunk chunk;int width,height;boolean candidates;
        FBORenderTrees trees;
        final TreeTextureState textures=new TreeTextureState();
        final Map<IsoTree,TreeMaterial> treeMaterials=new IdentityHashMap<>();
        int drawFbo,readFbo;boolean bound;final int[] viewport=new int[4];
        final TextureDraw.GenericDrawer tree=new TextureDraw.GenericDrawer(){
            @Override public void render(){
                Source source=renderingSource;if(!sourceGpu||source==null||failed||trees==null)return;
                int previous=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                FBORenderChunk previousChunk=FBORenderChunkManager.instance.renderThreadCurrent;
                GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_ENABLE_BIT|GL11.GL_VIEWPORT_BIT|GL11.GL_SCISSOR_BIT|GL11.GL_STENCIL_BUFFER_BIT);
                try{
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,source.fbo);GL11.glViewport(0,0,source.w,source.h);
                    GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glEnable(GL11.GL_BLEND);
                    GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
                    FBORenderChunkManager.instance.renderThreadCurrent=source.owner;textures.save();
                    try{trees.render();}finally{textures.restore();}
                }finally{FBORenderChunkManager.instance.renderThreadCurrent=previousChunk;GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,previous);GL11.glPopAttrib();}
            }
            @Override public void postRender(){if(trees!=null){trees.postRender();trees=null;}textures.ids.clear();}
        };
        final TextureDraw.GenericDrawer begin=new TextureDraw.GenericDrawer(){@Override public void render(){
            sourceGpu=true;pendingComposite=null;
            drawFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);bound=false;
            try{renderingSource=failed?null:source(Capture.this);if(renderingSource!=null){
                renderingSource.nativeFbo=drawFbo;
                if(!candidates){if(renderingSource.fbo!=0)renderingSource.dropImages();renderingSource.valid=true;renderingSource.hasPixels=false;}
                else {prepare(renderingSource);renderingSource.hasTrees|=trees!=null;GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,renderingSource.fbo);GL11.glViewport(0,0,width,height);bound=true;}
            }}catch(Throwable error){renderingSource=null;fail(error);}
        }};
        final TextureDraw.GenericDrawer end=new TextureDraw.GenericDrawer(){@Override public void render(){
            Source completed=renderingSource;renderingSource=null;sourceGpu=false;
            if(bound){if(completed!=null&&completed.valid)completed.hasPixels=true;GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,drawFbo);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);bound=false;}
            if(completed!=null&&completed.valid){completed.rebuildQueued=false;updateTreeMaterials(completed,treeMaterials);if(completed.hasPixels)try{WallReflectionLighting.finish(completed.depth.getID(),completed.fbo,completed.depthId);}catch(Throwable error){completed.valid=false;fail(error);}}
        }@Override public void postRender(){treeMaterials.clear();}};
    }
    /** Native full-tree drawing selects nearest filtering; keep that choice private to this source. */
    private static final class TreeTextureState {
        final ArrayList<TextureID> ids=new ArrayList<>();int[] filters=new int[0];int active,bound,last;
        void add(Texture texture){if(texture!=null&&texture.getTextureId()!=null&&!ids.contains(texture.getTextureId()))ids.add(texture.getTextureId());}
        void save(){
            if(filters.length<ids.size()*4)filters=new int[ids.size()*4];
            active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);last=Texture.lastTextureID;
            for(int n=0;n<ids.size();n++){
                TextureID id=ids.get(n);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id.getID());int i=n*4;
                filters[i]=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER);
                filters[i+1]=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER);
                filters[i+2]=Access.minFilter(id);filters[i+3]=Access.magFilter(id);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,bound);GL13.glActiveTexture(active);
        }
        void restore(){
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            for(int n=0;n<ids.size();n++){
                TextureID id=ids.get(n);int i=n*4;id.setMinFilter(filters[i+2]);id.setMagFilter(filters[i+3]);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,id.getID());
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,filters[i]);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,filters[i+1]);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,bound);Texture.lastTextureID=last;GL13.glActiveTexture(active);
        }
    }
    private static final class Source {
        final FBORenderChunk owner;final Texture depth,colour;final IsoChunk chunk;final int w,h,storageW,storageH,originalColour;
        int fbo,colourId,depthId,nativeFbo;boolean clear=true,valid,removed,hasPixels,hasTrees;volatile boolean rebuildQueued;
        final Set<IsoTree> treeMaterials=Collections.newSetFromMap(new IdentityHashMap<>());
        Source(Capture capture){owner=capture.owner;depth=capture.depth;colour=capture.colour;chunk=capture.chunk;w=capture.width;h=capture.height;storageW=colour.getWidthHW();storageH=colour.getHeightHW();originalColour=colour.getID();BY_COLOUR.put(originalColour,this);BY_DEPTH.put(depth.getID(),this);}
        void ensure(){
            if(fbo!=0)return;
            int previous=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try{
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth.getID());
                int depthFormat=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
                colourId=texture(storageW,storageH,GL11.GL_RGBA8,GL11.GL_RGBA);depthId=texture(storageW,storageH,depthFormat,GL11.GL_DEPTH_COMPONENT);
                fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,colourId,0);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depthId,0);
                if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("Physical wall source framebuffer incomplete");
            }finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,previous);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL13.glActiveTexture(active);}
        }
        void dropImages(){TreeReflectionLighting.remove(depth.getID());if(fbo!=0)GL30.glDeleteFramebuffers(fbo);if(colourId!=0)GL11.glDeleteTextures(colourId);if(depthId!=0)GL11.glDeleteTextures(depthId);fbo=colourId=depthId=0;valid=false;hasPixels=false;clear=true;}
        void release(){updateTreeMaterials(this,Collections.emptyMap());dropImages();removed=true;WANTED.remove(depth);if(BY_COLOUR.get(originalColour)==this)BY_COLOUR.remove(originalColour);if(BY_DEPTH.get(depth.getID())==this)BY_DEPTH.remove(depth.getID());WallReflectionLighting.remove(depth.getID());}
    }
    private static int texture(int w,int h,int internal,int format){
        int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,format,format==GL11.GL_DEPTH_COMPONENT?GL11.GL_FLOAT:GL11.GL_UNSIGNED_BYTE,(java.nio.ByteBuffer)null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);return texture;
    }
    private static Source source(Capture capture){
        Source s=SOURCES.get(capture.depth);
        if(s==null||s.owner!=capture.owner||s.chunk!=capture.chunk||s.w!=capture.width||s.h!=capture.height){
            if(s!=null)s.release();s=new Source(capture);SOURCES.put(capture.depth,s);
        }
        return s;
    }
    /** Native beginRenderThread(true) clears the same physical layer; camera-only changes rebuild its full source too. */
    public static void beginBake(FBORenderChunk owner,boolean clear){
        nativeBegins++;if(!clear||owner==null||owner.depth==null)return;
        Capture capture=new Capture();capture.owner=owner;capture.depth=owner.depth;capture.colour=owner.tex;capture.chunk=owner.chunk;capture.width=owner.w;capture.height=owner.h;
        Source source=source(capture);source.clear=true;source.valid=false;source.hasPixels=false;
    }
    /** Native chunk destruction is queued, preserving the already submitted render-state ownership. */
    public static void free(FBORenderChunk owner){
        nativeFrees++;if(owner==null)return;Texture key=owner.depth;
        SpriteRenderer.instance.drawGeneric(new TextureDraw.GenericDrawer(){@Override public void render(){Source source=SOURCES.remove(key);if(source!=null)source.release();}});
    }
    public static void clear(){
        CaptureContext c=CPU.get();c.treeOwner=c.capturedOwner=null;c.neighbourTrees.clear();
        StandingObjectCutaway.clear();
        if(OffscreenReflectionRepair.nativeReady())OffscreenReflectionState.clear();
        SpriteRenderer.instance.drawGeneric(new TextureDraw.GenericDrawer(){@Override public void render(){
            for(Source source:SOURCES.values())source.release();SOURCES.clear();BY_COLOUR.clear();BY_DEPTH.clear();TREE_HOLDERS.clear();WANTED.clear();REBUILD.clear();WallReflectionLighting.clear();PhysicalShadowSources.clear();COMPOSITES.clear();pendingComposite=renderingSource=null;
        }});
    }
    /** After Ssr.chunkDraw set the current real native program's mapping and frame. */
    public static void composite(TextureDraw draw){
        pendingComposite=null;
        if(failed||draw==null||draw.tex1==null||!enabled())return;
        Source source=SOURCES.get(draw.tex1);if(source!=null){if(source.valid){if(source.hasPixels&&Access.near(draw.tex1))pendingComposite=source;}else request(source);}
    }
    public static int shadowTexture(int nativeDepth){Source source=BY_DEPTH.get(nativeDepth);return source!=null&&source.valid&&source.hasPixels&&!source.removed?source.depthId:0;}
    record TreeLightingTarget(int framebuffer,int colour,int depth){ }
    static TreeLightingTarget treeLightingTarget(int nativeDepth){Source s=BY_DEPTH.get(nativeDepth);return s!=null&&s.valid&&s.hasPixels&&s.hasTrees&&!s.removed&&s.fbo!=0?new TreeLightingTarget(s.fbo,s.colourId,s.depthId):null;}
    /** Replaces the single native RingBuffer GL call, using its exact live vertex/index data. */
    public static void drawElements(int mode,int start,int end,int count,int type,long offset){
        Source source=renderingSource;
        if(sourceGpu){
            if(source==null||failed)return;
            int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            try{source.ensure();}catch(Throwable error){source.valid=false;fail(error);return;}
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_ENABLE_BIT|GL11.GL_VIEWPORT_BIT|GL11.GL_SCISSOR_BIT);
            try{
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,source.fbo);GL11.glViewport(0,0,source.w,source.h);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDepthMask(true);
                GL11.glColorMask(true,true,true,true);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL12.glDrawRangeElements(mode,start,end,count,type,offset);source.valid=true;source.hasPixels=true;sourceDraws++;
            }catch(Throwable error){source.valid=false;fail(error);}finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL11.glPopAttrib();}
            return;
        }
        source=pendingComposite;
        int program=source==null?0:GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        CompositeUniforms uniforms=source==null?null:COMPOSITES.computeIfAbsent(program,p->new CompositeUniforms(
            GL20.glGetUniformLocation(p,"pzSsrMapC")>=0&&GL20.glGetUniformLocation(p,"chunkDepth")>=0,
            GL20.glGetUniformLocation(p,OffscreenReflectionShaders.PHYSICAL_MASK_ON)));
        // A complete tree-containing physical snapshot owns this chunk's PPR geometry, including soft edges.
        boolean physicalOwner=source!=null&&source.hasTrees&&uniforms.composite&&uniforms.on>=0;
        if(physicalOwner)GL20.glUniform1f(uniforms.on,1);
        try{
            GL12.glDrawRangeElements(mode,start,end,count,type,offset);
            if(!physicalOwner&&OffscreenReflectionRepair.nativeReady())OffscreenReflectionState.compositeElements(mode,start,end,count,type,offset);
        }finally{if(physicalOwner)GL20.glUniform1f(uniforms.on,0);}
        source=pendingComposite;pendingComposite=null;if(source==null||!uniforms.composite)return;
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_ENABLE_BIT);
        TreeReflectionLighting.Replay shade=null;
        try{
            if(source.hasTrees)shade=TreeReflectionLighting.beginReplay(source.depth.getID());
            GL11.glColorMask(false,false,false,false);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_DEPTH_TEST);
            if(source.hasTrees&&uniforms.on>=0)GL20.glUniform1f(uniforms.on,2);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source.colourId);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source.depthId);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL12.glDrawRangeElements(mode,start,end,count,type,offset);compositeDraws++;
            if(OffscreenReflectionRepair.nativeReady())OffscreenReflectionGpu.compositeElements(mode,start,end,count,type,offset,source.hasTrees);
        }catch(Throwable error){source.valid=false;fail(error);}finally{
            if(shade!=null)shade.close();
            if(uniforms.on>=0)GL20.glUniform1f(uniforms.on,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source.colour.getID());
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source.depth.getID());GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glPopAttrib();
        }
    }
    private static void request(Source source){
        if(!source.rebuildQueued&&!source.removed){WANTED.put(source.depth,Boolean.TRUE);source.rebuildQueued=true;REBUILD.add(source);}
    }
    /** Native neighbour copies also depend on the tree's actual material/light, never camera alpha. */
    private static void updateTreeMaterials(Source source,Map<IsoTree,TreeMaterial> materials){
        for(Iterator<IsoTree> it=source.treeMaterials.iterator();it.hasNext();){
            IsoTree tree=it.next();if(materials.containsKey(tree))continue;
            TreeHolders holders=TREE_HOLDERS.get(tree);if(holders!=null){holders.sources.remove(source);if(holders.sources.isEmpty())TREE_HOLDERS.remove(tree);}it.remove();
        }
        for(var item:materials.entrySet()){
            TreeHolders holders=TREE_HOLDERS.computeIfAbsent(item.getKey(),tree->new TreeHolders());
            if(!item.getValue().equals(holders.material)){
                holders.material=item.getValue();
                for(Source other:holders.sources)if(other!=source)request(other);
            }
            holders.sources.add(source);source.treeMaterials.add(item.getKey());
        }
    }
    /** Drain only this cache's missing-source requests, never scan the world or continuously invalidate. */
    public static void drainRebuilds(){
        long frame=SpriteRenderer.instance.states.getPopulating().time;if(frame==drainFrame)return;drainFrame=frame;
        for(int count=0;count<256;count++){
            Source source=REBUILD.poll();if(source==null)break;
            if(source.removed||source.owner.chunk!=source.chunk||source.owner.depth!=source.depth){source.rebuildQueued=false;continue;}
            FBORenderLevels levels=source.owner.getRenderLevels();if(levels==null){source.rebuildQueued=false;continue;}
            levels.invalidateLevel(source.owner.getMinLevel(),FBORenderChunk.DIRTY_OBJECT_MODIFY);rebuilds++;
        }
    }
    public static long rebuilds(){return rebuilds;}
    /** Same installed AO ratio/multiply draw, into the physical layer after its original colour draw. */
    public static void mirrorLighting(int mode,int first,int count,int originalColour){
        if(sourceGpu||failed)return;
        Source source=BY_COLOUR.get(originalColour);if(source==null||!source.valid||!source.hasPixels||source.fbo==0||source.hasTrees)return;
        int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);if(fbo!=source.nativeFbo)return;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);int depth=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,source.depthId);GL13.glActiveTexture(active);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,source.fbo);
        try{GL11.glDrawArrays(mode,first,count);}finally{
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL13.glActiveTexture(active);
        }
    }
    private static void prepare(Source source){
        source.ensure();if(!source.clear)return;
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_ENABLE_BIT|GL11.GL_SCISSOR_BIT);
        try{
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,source.fbo);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);GL11.glClearColor(0,0,0,0);GL11.glClearDepth(1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);
            GL30.glBlitFramebuffer(0,0,source.w,source.h,0,0,source.w,source.h,GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            source.clear=false;source.valid=true;source.hasPixels=false;source.hasTrees=false;
        }finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glPopAttrib();}
    }    private static boolean enabled(){try{return !failed&&(Access.active()||Access.shadowActive());}catch(Throwable error){fail(error);return false;}}
    private static void fail(Throwable error){if(!failed)System.err.println("[PZOptPreloader] Physical wall reflections disabled: "+error);failed=true;}
    static void lightingFailed(Throwable error){fail(error);}
    private static final class Access {
        static final Access INSTANCE=new Access();
        final MethodHandle active,render,renderOther,light,shadowEnabled,mirrorWallDrawn,treeRect,ownRect,rectOverlaps,minFilter,magFilter;
        final Constructor<?> rectConstructor;final Field frame,near,view,on,sun;
        Access(){try{
            MethodHandles.Lookup lookup=MethodHandles.lookup();Class<?> ssr=Class.forName("pzopt.Ssr"),frameClass=Class.forName("pzopt.Ssr$Frame"),viewClass=Class.forName("pzopt.Ssr$View");
            minFilter=lookup.unreflectGetter(field(TextureID.class,"glMinFilter"));magFilter=lookup.unreflectGetter(field(TextureID.class,"glMagFilter"));
            mirrorWallDrawn=lookup.unreflect(Class.forName("pzopt.Mirrors").getMethod("wallDrawn",IsoObject.class,int.class));
            Method a=ssr.getDeclaredMethod("active");active=lookup.unreflect(a);
            Method r=FBORenderCell.class.getDeclaredMethod("renderMinusFloor_DoorOrWall",IsoObject.class);r.setAccessible(true);render=lookup.unreflect(r).asType(MethodType.methodType(void.class,Object.class,IsoObject.class));
            Method other=FBORenderCell.class.getDeclaredMethod("renderMinusFloor_NotDoorOrWall",IsoObject.class);other.setAccessible(true);renderOther=lookup.unreflect(other).asType(MethodType.methodType(void.class,Object.class,IsoObject.class));
            Method lighting=FBORenderCell.class.getDeclaredMethod("sanitizeLightInfo",int.class,IsoGridSquare.class);lighting.setAccessible(true);light=lookup.unreflect(lighting).asType(MethodType.methodType(ColorInfo.class,Object.class,int.class,IsoGridSquare.class));
            Class<?> bake=Class.forName("pzopt.TreeBake"),rect=Class.forName("pzopt.TreeBake$Rect");rectConstructor=rect.getDeclaredConstructor();rectConstructor.setAccessible(true);
            Method tr=FBORenderCell.class.getDeclaredMethod("pzoptTreeRect",IsoTree.class,int.class,int.class,float.class,float.class,rect);tr.setAccessible(true);
            treeRect=lookup.unreflect(tr).asType(MethodType.methodType(boolean.class,Object.class,IsoTree.class,int.class,int.class,float.class,float.class,Object.class));
            ownRect=lookup.unreflect(bake.getMethod("ownTextureRect",float.class,float.class,float.class,rect)).asType(MethodType.methodType(void.class,float.class,float.class,float.class,Object.class));
            rectOverlaps=lookup.unreflect(bake.getMethod("overlaps",rect,rect)).asType(MethodType.methodType(boolean.class,Object.class,Object.class));
            Method shadows=Class.forName("pzopt.ChunkAo").getDeclaredMethod("enabled");shadows.setAccessible(true);shadowEnabled=lookup.unreflect(shadows);sun=field(Class.forName("pzopt.Config"),"SUN_SHADOWS");
            frame=field(ssr,"renderFrame");near=field(frameClass,"nearWater");view=field(frameClass,"view");on=field(viewClass,"on");
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Physical wall input API unavailable",error);}}
        static Field field(Class<?> type,String name)throws ReflectiveOperationException{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
        static boolean active(){try{return (boolean)INSTANCE.active.invokeExact();}catch(Throwable error){throw new IllegalStateException("Physical wall active query",error);}}
        static void mirrorWallDrawn(IsoObject object,int cutaway){try{INSTANCE.mirrorWallDrawn.invokeExact(object,cutaway);}catch(Throwable error){throw new IllegalStateException("Camera mirror cutaway",error);}}
        static boolean shadowActive(){try{return INSTANCE.sun.getBoolean(null)&&(boolean)INSTANCE.shadowEnabled.invokeExact();}catch(Throwable error){throw new IllegalStateException("Physical shadow active query",error);}}
        static void render(Object cell,IsoObject object,boolean wall){try{if(wall)INSTANCE.render.invokeExact(cell,object);else INSTANCE.renderOther.invokeExact(cell,object);}catch(Throwable error){throw new IllegalStateException("Physical object bake",error);}}
        static ColorInfo light(Object cell,int player,IsoGridSquare square){try{return (ColorInfo)INSTANCE.light.invokeExact(cell,player,square);}catch(Throwable error){throw new IllegalStateException("Physical tree lighting",error);}}
        static int minFilter(TextureID id){try{return (int)INSTANCE.minFilter.invokeExact(id);}catch(Throwable error){throw new IllegalStateException("Physical tree min filter",error);}}
        static int magFilter(TextureID id){try{return (int)INSTANCE.magFilter.invokeExact(id);}catch(Throwable error){throw new IllegalStateException("Physical tree mag filter",error);}}
        static boolean overlaps(Object cell,FBORenderChunk owner,IsoTree tree,CaptureContext c){try{
            if(c.treeRect==null){c.treeRect=INSTANCE.rectConstructor.newInstance();c.textureRect=INSTANCE.rectConstructor.newInstance();}
            float scale=owner.highRes?2f:1f,goX=FBORenderChunkManager.instance.getXOffset(),yoff=FBORenderChunkManager.instance.getYOffset();
            INSTANCE.ownRect.invokeExact(goX,owner.w/scale,owner.h/scale,c.textureRect);
            return (boolean)INSTANCE.treeRect.invokeExact(cell,tree,owner.chunk.wx,owner.chunk.wy,goX,yoff,c.treeRect)
                &&(boolean)INSTANCE.rectOverlaps.invokeExact(c.treeRect,c.textureRect);
        }catch(Throwable error){throw new IllegalStateException("Physical neighbour tree bounds",error);}}
        static boolean near(Texture texture){try{Object f=INSTANCE.frame.get(null);return f!=null&&INSTANCE.on.getBoolean(INSTANCE.view.get(f))&&((Map<?,?>)INSTANCE.near.get(f)).containsKey(texture);}catch(IllegalAccessException error){throw new IllegalStateException(error);}}
    }
}
