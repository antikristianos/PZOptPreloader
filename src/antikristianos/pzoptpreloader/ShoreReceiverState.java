package antikristianos.pzoptpreloader;

import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import zombie.core.SpriteRenderer;
import zombie.core.textures.TextureDraw;
import zombie.iso.*;

/** A separate per-frame receiver target; never writes the native scene depth. */
public final class ShoreReceiverState {
    private static final int SLOT=7, UNIT=15;
    public static final int PUDDLE_UNIT=31;
    private static volatile boolean gameEnabled,shadowEnabled,needsLate;
    private static WeakReference<Object> world=new WeakReference<>(null);
    private static final ThreadLocal<Pass> GAME_PASS=new ThreadLocal<>();
    private static final ThreadLocal<Floor> FLOOR=ThreadLocal.withInitial(Floor::new);
    private static final Map<Integer,Marker[]> MARKERS=new ConcurrentHashMap<>();
    private static final Map<Integer,int[]> LOCATIONS=new ConcurrentHashMap<>();
    private static final class Floor { boolean active; float owner; int n; final int[] programs=new int[16]; }
    private static int receiver,width,height,receiverFbo;
    private static boolean valid,prepared;
    private static Pass renderPass;
    private static boolean warned;
    private static final FrameCommands[] COMMANDS={new FrameCommands(),new FrameCommands(),new FrameCommands()};
    private static final Map<Integer,Dimensions> DIMENSIONS=new HashMap<>();
    private static final float[] CLEAR={1,0,0,1};
    private static final int[] PUDDLE_VIEWPORT=new int[4];
    private ShoreReceiverState() { }
    private static final class Access {
        static final Access INSTANCE=new Access();
        final Method enabled;
        final Field shadows,characters,vehicles,players,floorChunks;
        Access(){try{
            Class<?> config=Class.forName("pzopt.Config");enabled=Class.forName("pzopt.Overrides").getMethod("enabled");
            shadows=config.getField("SUN_SHADOWS");characters=config.getField("SUN_SHADOW_CHARACTERS");vehicles=config.getField("SUN_SHADOW_VEHICLES");
            players=zombie.iso.fboRenderChunk.FBORenderCell.class.getDeclaredField("perPlayerData");players.setAccessible(true);
            Class<?> pp=Class.forName("zombie.iso.fboRenderChunk.FBORenderCell$PerPlayerData");floorChunks=pp.getDeclaredField("chunksWithTranslucentFloor");floorChunks.setAccessible(true);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Shore shadow receiver API unavailable",error);}}
        boolean enabled(){try{return (Boolean)enabled.invoke(null)&&shadows.getBoolean(null)&&(characters.getBoolean(null)||vehicles.getBoolean(null));}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    }
    public static void prepareFrame(List<IsoChunk> chunks,int player){
        shadowEnabled=Access.INSTANCE.enabled();gameEnabled=shadowEnabled||PuddleFootprintState.enabled();needsLate=false;
        if(!gameEnabled||chunks==null||!IsoWater.getInstance().getShaderEnable())return;
        for(int i=0;i<chunks.size();i++){IsoChunk chunk=chunks.get(i);if(chunk!=null&&!chunk.getRenderLevels(player).getCachedSquares_WaterShore(0).isEmpty()){needsLate=true;break;}}
    }
    @SuppressWarnings("unchecked")
    public static void preparePlayerFrame(int player){
        try{Access a=Access.INSTANCE;Object[] players=(Object[])a.players.get(zombie.iso.fboRenderChunk.FBORenderCell.instance);
            prepareFrame(player<0||player>=players.length||players[player]==null?null:(List<IsoChunk>)a.floorChunks.get(players[player]),player);
        }catch(IllegalAccessException error){throw new IllegalStateException("Native shoreline frame list unavailable",error);}
    }
    public static boolean needsLateReceiver(){return shadowEnabled&&needsLate;}
    public static boolean allowRecordedFloor(IsoChunk chunk,int player,int level){return !gameEnabled||level!=0||chunk==null||chunk.getRenderLevels(player).getCachedSquares_WaterShore(0).isEmpty();}
    public static void startFrame(){
        shadowEnabled=Access.INSTANCE.enabled();gameEnabled=shadowEnabled||PuddleFootprintState.enabled();
        Object next=IsoWorld.instance.currentCell;boolean changed=world.get()!=next;
        if(changed)world=new WeakReference<>(next);
        FrameStart packet=commands().frameStart();packet.enabled=gameEnabled;packet.changed=changed;SpriteRenderer.instance.drawGeneric(packet);
    }
    public static void reset(){
        gameEnabled=needsLate=false;world.clear();GAME_PASS.remove();FLOOR.remove();MARKERS.clear();LOCATIONS.clear();
        if(SpriteRenderer.instance!=null)SpriteRenderer.instance.drawGeneric(RELEASE);
    }
    public static void shaderReload(){LOCATIONS.clear();}
    public static void startPass(int level){
        if(level==0&&commands().nextFrame==0){preparePlayerFrame(IsoCamera.frameState.playerIndex);startFrame();}
        if(level!=0||!gameEnabled||!needsLate)return;
        Pass previous=GAME_PASS.get();if(previous!=null)endPass(level);
        Pass pass=commands().pass();pass.hasShore=false;GAME_PASS.set(pass);SpriteRenderer.instance.drawGeneric(pass.begin);
    }
    public static void endPass(int level){
        if(level!=0)return;Pass pass=GAME_PASS.get();GAME_PASS.remove();
        if(pass!=null)SpriteRenderer.instance.drawGeneric(pass.end);
    }
    public static void begin(IsoObject object){
        Floor floor=FLOOR.get();floor.active=false;floor.n=0;Pass pass=GAME_PASS.get();
        if(pass==null||!gameEnabled||object==null||object.square==null||object.square.z!=0||object.getSprite()==null||!object.getSprite().solidfloor||!IsoWater.getInstance().getShaderEnable())return;
        IsoWaterGeometry water=object.square.getWater();if(water==null||!water.isbShore())return;
        floor.active=true;floor.owner=PuddleFootprintState.supported(object)?PuddleFootprintState.owner(object.square):0;pass.hasShore=true;
    }
    public static boolean active(){return FLOOR.get().active;}
    public static void tileStarted(int program){
        Floor floor=FLOOR.get();if(!floor.active)return;
        for(int i=0;i<floor.n;i++)if(floor.programs[i]==program)return;
        if(floor.n==floor.programs.length)throw new IllegalStateException("Unexpected shore tile program fanout");
        floor.programs[floor.n++]=program;Marker marker=commands().marker();marker.program=program;marker.on=true;marker.owner=floor.owner;SpriteRenderer.instance.drawGeneric(marker);
    }
    public static void end(){
        Floor floor=FLOOR.get();if(!floor.active)return;floor.active=false;
        for(int i=0;i<floor.n;i++)SpriteRenderer.instance.drawGeneric(markers(floor.programs[i])[0]);floor.n=0;
    }
    private static Marker[] markers(int program){return MARKERS.computeIfAbsent(program,p->new Marker[]{new Marker(p,false),new Marker(p,true)});}
    private static final class Marker extends TextureDraw.GenericDrawer {
        int program;boolean on;float owner;
        Marker(int program,boolean on){this.program=program;this.on=on;}
        @Override public void render(){
            int previous=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(previous!=program)GL20.glUseProgram(program);
            int[] u=locations(program);
            if(u[0]>=0)GL20.glUniform1f(u[0],on&&renderPass!=null&&renderPass.active?1:0);
            if(u[3]>=0)GL20.glUniform1f(u[3],on?owner:0);
            if(previous!=program)GL20.glUseProgram(previous);
        }
    }
    private static final class FrameStart extends TextureDraw.GenericDrawer {
        boolean enabled,changed;
        @Override public void render(){
            if(renderPass!=null)renderPass.restore();valid=false;prepared=false;
            if(changed||!enabled)releaseRender();
        }
    }
    private static final TextureDraw.GenericDrawer RELEASE=new TextureDraw.GenericDrawer(){@Override public void render(){if(renderPass!=null)renderPass.restore();releaseRender();}};
    private static void releaseRender(){if(receiver!=0)GL11.glDeleteTextures(receiver);receiver=0;width=height=receiverFbo=0;valid=prepared=false;LOCATIONS.clear();DIMENSIONS.clear();}
    private static void allocate(int w,int h){
        if(receiver!=0&&width==w&&height==h&&GL11.glIsTexture(receiver))return;
        if(receiver!=0)GL11.glDeleteTextures(receiver);receiver=GL11.glGenTextures();width=w;height=h;prepared=false;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+UNIT);int prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{GL11.glBindTexture(GL11.GL_TEXTURE_2D,receiver);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGB32F,w,h,0,GL11.GL_RGB,GL11.GL_FLOAT,(ByteBuffer)null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        }finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,prior);GL13.glActiveTexture(active);}
    }
    private static FrameCommands commands(){
        int index=Math.floorMod(SpriteRenderer.instance.getMainStateIndex(),COMMANDS.length);
        // Native hand-off stamps each reused render state even while world updates are paused.
        FrameCommands commands=COMMANDS[index];long generation=SpriteRenderer.instance.states.getPopulating().time;
        if(commands.generation!=generation){commands.generation=generation;commands.nextFrame=commands.nextPass=commands.nextMarker=0;}return commands;
    }
    private static final class FrameCommands {
        long generation=Long.MIN_VALUE;int nextFrame,nextPass,nextMarker;
        final ArrayList<FrameStart> frames=new ArrayList<>();final ArrayList<Pass> passes=new ArrayList<>();
        final ArrayList<Marker> markers=new ArrayList<>();
        FrameStart frameStart(){if(nextFrame==frames.size())frames.add(new FrameStart());return frames.get(nextFrame++);}
        Pass pass(){if(nextPass==passes.size())passes.add(new Pass());return passes.get(nextPass++);}
        Marker marker(){if(nextMarker==markers.size())markers.add(new Marker(0,false));return markers.get(nextMarker++);}
    }
    private static final class Dimensions {int object,type,w,h,vw,vh,age;}
    private static Dimensions dimensions(int fbo,int[] viewport){
        Dimensions size=DIMENSIONS.computeIfAbsent(fbo,k->new Dimensions());
        // Native allocation metadata follows resize/split-screen immediately without a GL round trip.
        var state=SpriteRenderer.instance.states.getRendering();
        if(state!=null&&state.fbo!=null&&state.fbo.getBufferId()==fbo&&state.fbo.getTexture()!=null){
            size.w=state.fbo.getTexture().getWidthHW();size.h=state.fbo.getTexture().getHeightHW();return size;
        }
        int type=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int object=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if(size.object==object&&size.type==type&&size.w>0&&size.vw==viewport[2]&&size.vh==viewport[3]&&++size.age<120)return size;
        size.object=object;size.type=type;size.vw=viewport[2];size.vh=viewport[3];size.age=0;
        if(type==GL11.GL_TEXTURE){
            int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+UNIT);int prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try{GL11.glBindTexture(GL11.GL_TEXTURE_2D,object);size.w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);size.h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);}
            finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,prior);GL13.glActiveTexture(active);}
        }else if(type==GL30.GL_RENDERBUFFER){
            int prior=GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);try{GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,object);size.w=GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_WIDTH);size.h=GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_HEIGHT);}
            finally{GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,prior);}
        }else throw new IllegalStateException("Unsupported native scene colour attachment");
        return size;
    }
    private static final class Pass {
        volatile boolean hasShore;
        boolean active;int fbo;
        final int[] buffers=new int[8],outputs=new int[8],viewport=new int[4];
        final ByteBuffer mask=BufferUtils.createByteBuffer(4);
        final TextureDraw.GenericDrawer begin=new TextureDraw.GenericDrawer(){@Override public void render(){capture();}};
        final TextureDraw.GenericDrawer end=new TextureDraw.GenericDrawer(){@Override public void render(){restore();}};
        void capture(){try{captureUnsafe();}catch(RuntimeException error){valid=false;restore();warn(error.toString());}}
        private void captureUnsafe(){
            if(!hasShore||!GL.getCapabilities().OpenGL30||GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)<=UNIT||GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS)<8||GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS)<8)return;
            fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);if(fbo==0)return;
            int attachment=GL30.GL_COLOR_ATTACHMENT0+SLOT;
            if(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)!=GL11.GL_NONE){warn("attachment 7 is already in use");return;}
            GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);Dimensions size=dimensions(fbo,viewport);int w=size.w,h=size.h;if(w<=0||h<=0)return;
            allocate(w,h);for(int i=0;i<8;i++)buffers[i]=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i);
            GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,SLOT,mask);
            System.arraycopy(buffers,0,outputs,0,8);outputs[SLOT]=attachment;
            active=true;renderPass=this;
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL11.GL_TEXTURE_2D,receiver,0);GL20.glDrawBuffers(outputs);
            try{
                if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("incomplete receiver framebuffer");
                GL30.glColorMaski(SLOT,true,true,true,true);
                if(!prepared){boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);if(scissor)GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    try{GL30.glClearBufferfv(GL11.GL_COLOR,SLOT,CLEAR);}finally{if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);}prepared=true;}
                receiverFbo=fbo;valid=true;
            }catch(RuntimeException error){valid=false;restore();warn(error.toString());}
        }
        void restore(){
            if(!active)return;int current=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            if(current!=fbo)GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            GL20.glDrawBuffers(buffers);GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+SLOT,GL11.GL_TEXTURE_2D,0,0);
            GL30.glColorMaski(SLOT,mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);
            if(current!=fbo)GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,current);active=false;if(renderPass==this)renderPass=null;
        }
    }
    private static void warn(String message){if(!warned){warned=true;System.err.println("[PZOptPreloader] shore shadow receiver unavailable; native depth retained: "+message);}}
    private static final class ShadowScope {int texture,unit;boolean usable;}
    private static final ArrayList<ShadowScope> SHADOWS=new ArrayList<>();
    private static int shadowDepth;
    public static void shadowBegin(){
        beginTexture(UNIT);
    }
    private static void beginTexture(int unit){
        if(shadowDepth==SHADOWS.size())SHADOWS.add(new ShadowScope());ShadowScope state=SHADOWS.get(shadowDepth++);state.unit=unit;state.usable=false;state.texture=-1;if(!valid||receiver==0||!GL.getCapabilities().OpenGL30||GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS)<=unit)return;int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        state.texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);state.usable=valid&&receiver!=0&&receiverFbo==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if(state.usable)GL11.glBindTexture(GL11.GL_TEXTURE_2D,receiver);GL13.glActiveTexture(active);
    }
    public static void shadowProgram(int program){
        if(program==0||shadowDepth==0)return;
        int[] u=locations(program);
        if(u[1]>=0)GL20.glUniform1i(u[1],UNIT);if(u[2]>=0)GL20.glUniform1f(u[2],SHADOWS.get(shadowDepth-1).usable?1:0);
    }
    public static void shadowEnd(){
        if(shadowDepth==0)return;ShadowScope state=SHADOWS.get(--shadowDepth);if(state.texture<0)return;int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+state.unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,state.texture);GL13.glActiveTexture(active);
    }
    private static int[] locations(int program){return LOCATIONS.computeIfAbsent(program,p->new int[]{GL20.glGetUniformLocation(p,"pzShoreReceiverCapture"),GL20.glGetUniformLocation(p,"pzShoreReceiver"),GL20.glGetUniformLocation(p,"pzShoreReceiverEnabled"),GL20.glGetUniformLocation(p,"pzShoreReceiverOwner")});}
    public static boolean puddleBegin(){beginTexture(PUDDLE_UNIT);if(!SHADOWS.get(shadowDepth-1).usable){shadowEnd();return false;}return true;}
    public static void puddleUniforms(int inverse,int viewport){
        if(inverse>=0)GL20.glUniform2f(inverse,1f/width,1f/height);
        if(viewport>=0){GL11.glGetIntegerv(GL11.GL_VIEWPORT,PUDDLE_VIEWPORT);GL20.glUniform4f(viewport,PUDDLE_VIEWPORT[0],PUDDLE_VIEWPORT[1],PUDDLE_VIEWPORT[2],PUDDLE_VIEWPORT[3]);}
    }
    public static void puddleEnd(){shadowEnd();}
}
