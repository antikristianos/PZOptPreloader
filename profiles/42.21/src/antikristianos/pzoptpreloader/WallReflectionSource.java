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
    private static boolean sourceCpu,sourceGpu;
    private static volatile boolean failed;
    private static Source renderingSource, pendingComposite;
    private static final Map<Texture,Source> SOURCES=new IdentityHashMap<>();
    private static final Map<Integer,Source> BY_COLOUR=new HashMap<>();
    private static final Map<Integer,Source> BY_DEPTH=new HashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Texture,Boolean> WANTED=new java.util.concurrent.ConcurrentHashMap<>(); // render thread
    private static final Map<Integer,Boolean> COMPOSITES=new HashMap<>();
    private static final FrameCommands[] COMMANDS={new FrameCommands(),new FrameCommands(),new FrameCommands()};
    private static long captures,sourceDraws,compositeDraws,nativeBegins,nativeFrees,rebuilds;
    private static final java.util.concurrent.ConcurrentLinkedQueue<Source> REBUILD=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static long drainFrame=Long.MIN_VALUE;
    private WallReflectionSource() { }

    /** Only calls made by the explicit physical-source pass bypass camera cutaway. */
    public static int cutaway(int value){return sourceCpu?0:value;}
    public static boolean translucentTree(boolean value){return !sourceCpu&&value;}
    public static boolean sourcePass(){return sourceCpu;}
    public static long captures(){return captures;}
    public static long nativeBegins(){return nativeBegins;}
    public static long nativeFrees(){return nativeFrees;}
    public static long sourceDraws(){return sourceDraws;}
    public static long compositeDraws(){return compositeDraws;}

    /** Invoked before native endRenderChunkLevel, while the native bake coordinates remain active. */
    public static void captureLevel(boolean clearDirty,Object cell,IsoChunk chunk,int level){
        if(failed||!clearDirty||sourceCpu||chunk==null||!FBORenderChunkManager.instance.isCaching()||!enabled())return;
        FBORenderChunk owner=FBORenderChunkManager.instance.renderChunk;
        if(owner==null||owner.chunk!=chunk||level!=owner.getTopLevel()||!WANTED.containsKey(owner.depth))return;
        int player=IsoCamera.frameState.playerIndex;
        Capture command=commands().next();command.owner=owner;command.depth=owner.depth;command.colour=owner.tex;
        command.chunk=chunk;command.width=owner.w;command.height=owner.h;command.candidates=false;
        SpriteRenderer.instance.drawGeneric(command.begin);
        boolean previousCircle=IsoGridSquare.circleStencil,previousLowLight=IsoObject.lowLightingQualityHack;
        IsoObject previousRendered=IsoObject.lastRendered,previousPreviousRendered=IsoObject.lastRenderedRendered;
        sourceCpu=true;
        try{
            for(int sourceLevel=owner.getMinLevel();sourceLevel<=owner.getTopLevel();sourceLevel++)
            for(int y=0;y<8;y++)for(int x=0;x<8;x++){
                IsoGridSquare square=chunk.getGridSquare(x,y,sourceLevel);if(square==null)continue;
                List<IsoObject> objects=square.getObjects();
                for(int i=0;i<objects.size();i++){
                    IsoObject object=objects.get(i);if(!physical(object))continue;command.candidates=true;
                    ObjectRenderInfo info=object.getRenderInfo(player);
                    float alpha=object.getAlpha(player),target=object.getTargetAlpha(player),sx=object.sx,sy=object.sy;
                    float targetInfo=info.targetAlpha,renderAlpha=info.renderAlpha;
                    float rx=info.renderX,ry=info.renderY,rw=info.renderWidth,rh=info.renderHeight,rsx=info.renderScaleX,rsy=info.renderScaleY;
                    boolean cut=info.cutaway,outline=info.cutawayOutline,noPicking=object.noPicking;
                    boolean treeFlag=object instanceof IsoTree tree&&tree.renderFlag;
                    try{
                        info.targetAlpha=1.0f;info.cutaway=false;info.cutawayOutline=false;object.setAlphaAndTarget(player,1.0f);
                        Access.render(cell,object,wall(object));captures++;
                    }finally{
                        object.setAlpha(player,alpha);object.setTargetAlpha(player,target);object.sx=sx;object.sy=sy;
                        info.targetAlpha=targetInfo;info.renderAlpha=renderAlpha;info.renderX=rx;info.renderY=ry;
                        info.renderWidth=rw;info.renderHeight=rh;info.renderScaleX=rsx;info.renderScaleY=rsy;
                        info.cutaway=cut;info.cutawayOutline=outline;object.noPicking=noPicking;
                        if(object instanceof IsoTree tree)tree.renderFlag=treeFlag;
                    }
                }
            }
        }catch(Throwable error){fail(error);}finally{
            sourceCpu=false;IsoGridSquare.circleStencil=previousCircle;IsoObject.lowLightingQualityHack=previousLowLight;
            IsoObject.lastRendered=previousRendered;IsoObject.lastRenderedRendered=previousPreviousRendered;
            SpriteRenderer.instance.drawGeneric(command.end);
        }
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
        int index=SpriteRenderer.instance.getMainStateIndex();FrameCommands commands=COMMANDS[Math.floorMod(index,COMMANDS.length)];
        long frame=SpriteRenderer.instance.states.getPopulating().time;
        if(commands.frame!=frame){commands.frame=frame;commands.next=0;}return commands;
    }
    private static final class FrameCommands {
        final ArrayList<Capture> captures=new ArrayList<>();long frame=Long.MIN_VALUE;int next;
        Capture next(){if(next==captures.size())captures.add(new Capture());return captures.get(next++);}
    }
    private static final class Capture {
        FBORenderChunk owner;Texture depth,colour;IsoChunk chunk;int width,height;boolean candidates;
        final TextureDraw.GenericDrawer begin=new TextureDraw.GenericDrawer(){@Override public void render(){
            sourceGpu=true;pendingComposite=null;
            try{renderingSource=failed?null:source(Capture.this);if(renderingSource!=null){
                renderingSource.nativeFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                if(!candidates){if(renderingSource.fbo!=0)renderingSource.dropImages();renderingSource.valid=true;renderingSource.hasPixels=false;}
                else prepare(renderingSource);
            }}catch(Throwable error){renderingSource=null;fail(error);}
        }};
        final TextureDraw.GenericDrawer end=new TextureDraw.GenericDrawer(){@Override public void render(){Source completed=renderingSource;renderingSource=null;sourceGpu=false;if(completed!=null&&completed.valid){completed.rebuildQueued=false;if(completed.hasPixels)try{WallReflectionLighting.finish(completed.depth.getID(),completed.fbo,completed.depthId);}catch(Throwable error){completed.valid=false;fail(error);}}}};
    }
    private static final class Source {
        final FBORenderChunk owner;final Texture depth,colour;final IsoChunk chunk;final int w,h,storageW,storageH,originalColour;
        int fbo,colourId,depthId,nativeFbo;boolean clear=true,valid,removed,hasPixels;volatile boolean rebuildQueued;
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
        void dropImages(){if(fbo!=0)GL30.glDeleteFramebuffers(fbo);if(colourId!=0)GL11.glDeleteTextures(colourId);if(depthId!=0)GL11.glDeleteTextures(depthId);fbo=colourId=depthId=0;valid=false;hasPixels=false;clear=true;}
        void release(){dropImages();removed=true;WANTED.remove(depth);if(BY_COLOUR.get(originalColour)==this)BY_COLOUR.remove(originalColour);if(BY_DEPTH.get(depth.getID())==this)BY_DEPTH.remove(depth.getID());WallReflectionLighting.remove(depth.getID());}
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
        if(OffscreenReflectionRepair.nativeReady())OffscreenReflectionState.clear();
        SpriteRenderer.instance.drawGeneric(new TextureDraw.GenericDrawer(){@Override public void render(){
            for(Source source:SOURCES.values())source.release();SOURCES.clear();BY_COLOUR.clear();BY_DEPTH.clear();WANTED.clear();REBUILD.clear();WallReflectionLighting.clear();PhysicalShadowSources.clear();COMPOSITES.clear();pendingComposite=renderingSource=null;
        }});
    }
    /** After Ssr.chunkDraw set the current real native program's mapping and frame. */
    public static void composite(TextureDraw draw){
        pendingComposite=null;
        if(failed||draw==null||draw.tex1==null||!enabled())return;
        Source source=SOURCES.get(draw.tex1);if(source!=null){if(source.valid){if(source.hasPixels&&Access.near(draw.tex1))pendingComposite=source;}else request(source);}
    }
    public static int shadowTexture(int nativeDepth){Source source=BY_DEPTH.get(nativeDepth);return source!=null&&source.valid&&source.hasPixels&&!source.removed?source.depthId:0;}
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
        GL12.glDrawRangeElements(mode,start,end,count,type,offset);
        if(OffscreenReflectionRepair.nativeReady())OffscreenReflectionState.compositeElements(mode,start,end,count,type,offset);
        source=pendingComposite;pendingComposite=null;if(source==null)return;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if(!COMPOSITES.computeIfAbsent(program,p->GL20.glGetUniformLocation(p,"pzSsrMapC")>=0&&GL20.glGetUniformLocation(p,"chunkDepth")>=0))return;
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_ENABLE_BIT);
        try{
            GL11.glColorMask(false,false,false,false);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source.colourId);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source.depthId);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL12.glDrawRangeElements(mode,start,end,count,type,offset);compositeDraws++;
            if(OffscreenReflectionRepair.nativeReady())OffscreenReflectionState.compositeElements(mode,start,end,count,type,offset);
        }catch(Throwable error){source.valid=false;fail(error);}finally{
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source.colour.getID());
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source.depth.getID());GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glPopAttrib();
        }
    }
    private static void request(Source source){
        if(!source.rebuildQueued&&!source.removed){WANTED.put(source.depth,Boolean.TRUE);source.rebuildQueued=true;REBUILD.add(source);}
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
        Source source=BY_COLOUR.get(originalColour);if(source==null||!source.valid||!source.hasPixels||source.fbo==0)return;
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
            source.clear=false;source.valid=true;source.hasPixels=false;
        }finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glPopAttrib();}
    }    private static boolean enabled(){try{return !failed&&(Access.active()||Access.shadowActive());}catch(Throwable error){fail(error);return false;}}
    private static void fail(Throwable error){if(!failed)System.err.println("[PZOptPreloader] Physical wall reflections disabled: "+error);failed=true;}
    private static final class Access {
        static final Access INSTANCE=new Access();
        final MethodHandle active,render,renderOther,shadowEnabled;final Field frame,near,view,on,sun;
        Access(){try{
            MethodHandles.Lookup lookup=MethodHandles.lookup();Class<?> ssr=Class.forName("pzopt.Ssr"),frameClass=Class.forName("pzopt.Ssr$Frame"),viewClass=Class.forName("pzopt.Ssr$View");
            Method a=ssr.getDeclaredMethod("active");active=lookup.unreflect(a);
            Method r=FBORenderCell.class.getDeclaredMethod("renderMinusFloor_DoorOrWall",IsoObject.class);r.setAccessible(true);render=lookup.unreflect(r).asType(MethodType.methodType(void.class,Object.class,IsoObject.class));
            Method other=FBORenderCell.class.getDeclaredMethod("renderMinusFloor_NotDoorOrWall",IsoObject.class);other.setAccessible(true);renderOther=lookup.unreflect(other).asType(MethodType.methodType(void.class,Object.class,IsoObject.class));
            Method shadows=Class.forName("pzopt.ChunkAo").getDeclaredMethod("enabled");shadows.setAccessible(true);shadowEnabled=lookup.unreflect(shadows);sun=field(Class.forName("pzopt.Config"),"SUN_SHADOWS");
            frame=field(ssr,"renderFrame");near=field(frameClass,"nearWater");view=field(frameClass,"view");on=field(viewClass,"on");
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Physical wall input API unavailable",error);}}
        static Field field(Class<?> type,String name)throws ReflectiveOperationException{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
        static boolean active(){try{return (boolean)INSTANCE.active.invokeExact();}catch(Throwable error){throw new IllegalStateException("Physical wall active query",error);}}
        static boolean shadowActive(){try{return INSTANCE.sun.getBoolean(null)&&(boolean)INSTANCE.shadowEnabled.invokeExact();}catch(Throwable error){throw new IllegalStateException("Physical shadow active query",error);}}
        static void render(Object cell,IsoObject object,boolean wall){try{if(wall)INSTANCE.render.invokeExact(cell,object);else INSTANCE.renderOther.invokeExact(cell,object);}catch(Throwable error){throw new IllegalStateException("Physical object bake",error);}}
        static boolean near(Texture texture){try{Object f=INSTANCE.frame.get(null);return f!=null&&INSTANCE.on.getBoolean(INSTANCE.view.get(f))&&((Map<?,?>)INSTANCE.near.get(f)).containsKey(texture);}catch(IllegalAccessException error){throw new IllegalStateException(error);}}
    }
}
