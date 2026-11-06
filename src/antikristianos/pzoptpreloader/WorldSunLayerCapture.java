package antikristianos.pzoptpreloader;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import zombie.core.SpriteRenderer;
import zombie.core.sprite.SpriteRenderState;
import zombie.core.textures.TextureDraw;

/** Native static draws retain AO-only main colour; the sun result is published by a later separate draw. */
public final class WorldSunLayerCapture {
    private static final Map<Integer,Target> TARGETS=new HashMap<>();
    private static final Map<Integer,int[]> LOCATIONS=new HashMap<>();
    private static final int[] NO_LOCATIONS={-1,-1,-1};
    private static final ByteBuffer COLOUR_MASK=BufferUtils.createByteBuffer(4);
    private static long shaderEpoch=Long.MIN_VALUE;
    private static int shoreDepth,dataDepth;
    public static long nativeDraws,dataDraws,applies;
    private WorldSunLayerCapture(){ }
    private record Attachment(int kind,int name,int format,int width,int height) { }
    private static Attachment attachment(int point){
        int kind=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,point,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if(kind==GL11.GL_NONE)return new Attachment(kind,0,0,0,0);
        int name=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,point,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if(kind==GL11.GL_TEXTURE){
            int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try{GL11.glBindTexture(GL11.GL_TEXTURE_2D,name);return new Attachment(kind,name,
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT));}
            finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);GL13.glActiveTexture(active);}
        }
        if(kind==GL30.GL_RENDERBUFFER){
            int old=GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
            try{GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,name);return new Attachment(kind,name,
                GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_INTERNAL_FORMAT),
                GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_WIDTH),
                GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_HEIGHT));}
            finally{GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,old);}
        }
        throw new IllegalStateException("Unsupported native world attachment type "+kind);
    }
    private static boolean combinedDepth(int format){return format==GL30.GL_DEPTH24_STENCIL8||format==GL30.GL_DEPTH32F_STENCIL8;}
    private static int renderbuffer(int attachment,Attachment input){
        if(input.name==0)return 0;
        int old=GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING),id=GL30.glGenRenderbuffers();
        try{GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,id);GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,input.format,input.width,input.height);
            GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_RENDERBUFFER,id);return id;}
        catch(Throwable error){GL30.glDeleteRenderbuffers(id);throw error;}
        finally{GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,old);}
    }
    private static final class Target {
        final int original;int fbo,colour,depth,stencil;Attachment originalColour,originalDepth,originalStencil;
        SpriteRenderState state;long time;boolean pending;
        Target(int id){original=id;}
        void release(){if(fbo!=0)GL30.glDeleteFramebuffers(fbo);if(colour!=0)GL30.glDeleteRenderbuffers(colour);
            if(depth!=0)GL30.glDeleteRenderbuffers(depth);if(stencil!=0)GL30.glDeleteRenderbuffers(stencil);
            fbo=colour=depth=stencil=0;pending=false;}
        void ensure(){
            Attachment c=attachment(GL30.GL_COLOR_ATTACHMENT0),d=attachment(GL30.GL_DEPTH_ATTACHMENT),s=attachment(GL30.GL_STENCIL_ATTACHMENT);
            if(c.name==0||c.width<=0||c.height<=0)throw new IllegalStateException("Native world colour unavailable");
            if(fbo!=0&&c.equals(originalColour)&&d.equals(originalDepth)&&s.equals(originalStencil))return;
            release();originalColour=c;originalDepth=d;originalStencil=s;fbo=GL30.glGenFramebuffers();
            int previous=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            try{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);colour=renderbuffer(GL30.GL_COLOR_ATTACHMENT0,c);
                depth=renderbuffer(combinedDepth(d.format)?GL30.GL_DEPTH_STENCIL_ATTACHMENT:GL30.GL_DEPTH_ATTACHMENT,d);
                if(s.name!=0&&!(s.kind==d.kind&&s.name==d.name&&combinedDepth(d.format)))stencil=renderbuffer(GL30.GL_STENCIL_ATTACHMENT,s);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("Separate native-format world-sun target incomplete");
            }catch(Throwable error){release();throw error;}finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,previous);}
        }
        void prefix(){
            int mask=GL11.GL_COLOR_BUFFER_BIT;if(originalDepth.name!=0)mask|=GL11.GL_DEPTH_BUFFER_BIT;if(originalStencil.name!=0)mask|=GL11.GL_STENCIL_BUFFER_BIT;
            blit(original,fbo,originalColour.width,originalColour.height,mask);pending=true;
            state=SpriteRenderer.instance.states.getRendering();time=state==null?0:state.time;
        }
    }
    private static void blit(int source,int target,int width,int height,int mask){
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);int sourceRead=0;int[] targetBuffers=null;
        try{GL11.glDisable(GL11.GL_SCISSOR_TEST);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source);sourceRead=GL11.glGetInteger(GL11.GL_READ_BUFFER);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target);
            targetBuffers=new int[GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS)];
            for(int i=0;i<targetBuffers.length;i++)targetBuffers[i]=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0,0,width,height,0,0,width,height,mask,GL11.GL_NEAREST);
        }finally{if(targetBuffers!=null){IntBuffer values=BufferUtils.createIntBuffer(targetBuffers.length);values.put(targetBuffers).flip();GL20.glDrawBuffers(values);}
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source);GL11.glReadBuffer(sourceRead);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
            if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);}
    }
    private static int[] locations(int program){
        if(shaderEpoch!=WorldSunLayerShaders.generation){LOCATIONS.clear();shaderEpoch=WorldSunLayerShaders.generation;}
        return LOCATIONS.computeIfAbsent(program,p->new int[]{GL20.glGetUniformLocation(p,"pzWorldData"),GL20.glGetUniformLocation(p,"pzShoreEnabled"),GL20.glGetUniformLocation(p,"pzWorldSourceReady")});
    }
    /** Exact native wind/geometry/program and blending run on two independent colour/depth destinations. */
    public static void drawElements(int mode,int start,int end,int count,int type,long offset){
        if(!WorldSunLayerShaders.enabled()){WallReflectionSource.drawElements(mode,start,end,count,type,offset);return;}
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] locations=program==0?NO_LOCATIONS:locations(program);int location=locations[0];
        if(location<0||shoreDepth==0&&locations[1]>=0){
            Target pending=TARGETS.get(fbo);
            if(pending!=null&&pending.pending){
                GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,0,COLOUR_MASK);
                if(COLOUR_MASK.get(0)!=0||COLOUR_MASK.get(1)!=0||COLOUR_MASK.get(2)!=0||COLOUR_MASK.get(3)!=0)apply();
            }
            WallReflectionSource.drawElements(mode,start,end,count,type,offset);return;
        }
        if(fbo==0){
            float[] previous=new float[1];GL20.glGetUniformfv(program,location,previous);
            int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE1);
            int depth=shoreDepth!=0?shoreDepth:GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL13.glActiveTexture(active);
            GL20.glUniform1f(location,1);dataDepth++;
            try(Receiver source=new Receiver(depth,depth,shoreDepth!=0)){WallReflectionSource.drawElements(mode,start,end,count,type,offset);}
            finally{dataDepth--;GL20.glUseProgram(program);GL20.glUniform1f(location,previous[0]);}
            return;
        }
        Target target=TARGETS.computeIfAbsent(fbo,Target::new);target.ensure();
        SpriteRenderState current=SpriteRenderer.instance.states.getRendering();long now=current==null?0:current.time;
        if(!target.pending||target.state!=current||target.time!=now)target.prefix();
        float[] previous=new float[1];GL20.glGetUniformfv(program,location,previous);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int originalDepth=shoreDepth!=0?shoreDepth:GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL13.glActiveTexture(active);
        try{
            GL20.glUniform1f(location,0);GL12.glDrawRangeElements(mode,start,end,count,type,offset);nativeDraws++;
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target.fbo);GL20.glUniform1f(location,1);dataDepth++;
            try(Receiver source=new Receiver(originalDepth,originalDepth,shoreDepth!=0)){
                WallReflectionSource.drawElements(mode,start,end,count,type,offset);dataDraws++;
            }finally{dataDepth--;}
        }catch(Throwable error){target.pending=false;throw error;}
        finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL20.glUseProgram(program);GL20.glUniform1f(location,previous[0]);}
    }
    /** Native producer boundary after static chunks and again after late shore; no sun update uses wind data. */
    public static void queueApply(){if(WorldSunLayerShaders.enabled())SpriteRenderer.instance.drawGeneric(new TextureDraw.GenericDrawer(){@Override public void render(){apply();}});}
    public static void apply(){
        SpriteRenderState current=SpriteRenderer.instance.states.getRendering();long time=current==null?0:current.time;
        for(Iterator<Target> iterator=TARGETS.values().iterator();iterator.hasNext();){Target t=iterator.next();
            if(t.state!=current||t.time!=time||!GL30.glIsFramebuffer(t.original)){t.release();iterator.remove();continue;}
            if(!t.pending)continue;
            int bound=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            Attachment c;try{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,t.original);c=attachment(GL30.GL_COLOR_ATTACHMENT0);}
            finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,bound);}
            if(!c.equals(t.originalColour)){t.release();iterator.remove();continue;}
            blit(t.fbo,t.original,c.width,c.height,GL11.GL_COLOR_BUFFER_BIT);t.pending=false;applies++;
        }
    }
    public static void shoreBegin(int depth){shoreDepth=depth;}
    public static void shoreEnd(){shoreDepth=0;}
    /** Colour-masked physical SSR replay borrows only its own sun-coloured source, never wind bindings. */
    public static Receiver receiver(int originalDepth,int receiverDepth){return new Receiver(originalDepth,receiverDepth);}
    public static final class Receiver implements AutoCloseable {
        final int active,program,ready;final float[] oldReady=new float[1];int original;boolean changed;
        Receiver(int originalDepth,int receiverDepth){this(originalDepth,receiverDepth,false);}
        Receiver(int originalDepth,int receiverDepth,boolean shore){
            active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);program=dataDepth==0?0:GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            ready=program==0?-1:locations(program)[2];if(dataDepth==0)return;
            if(ready>=0)GL20.glGetUniformfv(program,ready,oldReady);
            if(shore){
                WorldSunLayerSources.requestFamily(originalDepth);
                if(ready>=0)GL20.glUniform1f(ready,WorldSunLayerState.grouped(originalDepth)?-1:1);
                return;
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);original=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try{int selected=receiverDepth,own=WorldSunLayerSources.texture(receiverDepth,original);
                if(own==0&&receiverDepth!=originalDepth){selected=originalDepth;own=WorldSunLayerSources.texture(originalDepth,original);}
                if(own!=0){WorldSunLayerSources.matchSampling(original,own);GL11.glBindTexture(GL11.GL_TEXTURE_2D,own);changed=true;}
                if(ready>=0)GL20.glUniform1f(ready,own!=0&&WorldSunLayerSources.currentLatest(selected)?1:0);}
            catch(Throwable error){GL11.glBindTexture(GL11.GL_TEXTURE_2D,original);if(ready>=0)GL20.glUniform1f(ready,oldReady[0]);throw error;}
            finally{GL13.glActiveTexture(active);}
        }
        public void close(){
            if(changed){GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,original);}GL13.glActiveTexture(active);
            if(ready>=0){int now=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(now!=program)GL20.glUseProgram(program);
                GL20.glUniform1f(ready,oldReady[0]);if(now!=program)GL20.glUseProgram(now);}
        }
    }
    public static void clear(){for(Target t:TARGETS.values())t.release();TARGETS.clear();LOCATIONS.clear();shaderEpoch=Long.MIN_VALUE;shoreDepth=dataDepth=0;}
}
