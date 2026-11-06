package antikristianos.pzoptpreloader;

import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import zombie.core.ShaderHelper;
import zombie.core.skinnedmodel.*;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.core.textures.TextureDraw;

/** Render-thread owned, current-frame source borders. The presented target and matrices are never resized. */
final class OffscreenReflectionGpu {
    static final int COLOUR_UNIT=25,DEPTH_UNIT=26;
    private static final Target[] TARGETS=new Target[4];
    private static final Map<Integer,Uniforms> PROGRAMS=new HashMap<>();
    private static final Set<Integer> TOUCHED=new HashSet<>();
    private static final IntBuffer INTS=BufferUtils.createIntBuffer(4);
    private static Target current;
    private static Object frame;
    private static boolean source,chunk,finished;
    private static int resolveProgram;
    private static final ImageBinding HASH_BINDING=new ImageBinding(),TILE_BINDING=new ImageBinding();
    private static volatile long shaderGeneration;private static long cachedGeneration;
    static long allocations,chunkCopies,actorCopies,resolves;
    private OffscreenReflectionGpu(){ }
    private static final class Target {
        int fbo,colour,depth,w,h,pad,top;
        final float[] vp=new float[4],mapping=new float[6];
        void release(){if(fbo!=0)GL30.glDeleteFramebuffers(fbo);if(colour!=0)GL11.glDeleteTextures(colour);if(depth!=0)GL11.glDeleteTextures(depth);fbo=colour=depth=0;}
        void ensure(int width,int height,int side,int above){
            if(fbo!=0&&w==width&&h==height&&pad==side&&top==above)return;
            release();w=width;h=height;pad=side;top=above;
            if(w>GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)||h>GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE))throw new IllegalStateException("Reflection source exceeds GPU target limit");
            colour=texture(w,h,GL30.GL_RGBA16F,GL11.GL_RGBA);depth=texture(w,h,GL30.GL_DEPTH_COMPONENT32F,GL11.GL_DEPTH_COMPONENT);
            fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,colour,0);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Reflection source framebuffer incomplete");allocations++;
        }
    }
    private static int texture(int w,int h,int internal,int format){int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,w,h,0,format,GL11.GL_FLOAT,(ByteBuffer)null);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);return id;}
    static void begin(int player){
        current=null;frame=null;chunk=false;finished=false;
        try{
            if(!OffscreenReflectionState.enabled())return;OffscreenReflectionAccess a=OffscreenReflectionAccess.get();Object f=a.renderFrame();if(!a.on(f)||!a.hasSources(f))return;
            INTS.clear();GL11.glGetIntegerv(GL11.GL_VIEWPORT,INTS);
            Target t=TARGETS[Math.floorMod(player,4)];if(t==null)TARGETS[Math.floorMod(player,4)]=t=new Target();
            for(int i=0;i<4;i++)t.vp[i]=INTS.get(i);if(t.vp[2]<1||t.vp[3]<1)return;
            a.mapping(f,t.vp,t.mapping);
            int top=(int)Math.ceil(a.reach(f,t.vp))+64;
            // Distortion is bounded by the original receiver's configured pixel-per-level displacement.
            int pad=(int)Math.ceil(Math.max(8f,Math.abs(1f/t.mapping[2])*a.integer(a.distortPct)/100f))+4;
            int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0);int tex=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_SCISSOR_BIT|GL11.GL_ENABLE_BIT);
            try{t.ensure((int)t.vp[2]+2*pad,(int)t.vp[3]+top,pad,top);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,t.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);GL11.glClearColor(0,0,0,0);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);}
            finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL11.glPopAttrib();GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);GL13.glActiveTexture(active);}
            current=t;frame=f;
        }catch(Throwable error){OffscreenReflectionState.fail(error);}
    }
    static void chunkDraw(TextureDraw draw){chunk=false;if(current!=null&&!finished&&draw!=null&&draw.tex1!=null)try{chunk=OffscreenReflectionAccess.get().relevant(frame,draw.tex1);}catch(Throwable e){OffscreenReflectionState.fail(e);}}
    static void compositeElements(int mode,int start,int end,int count,int type,long offset){
        if(!chunk||current==null||source||finished)return;
        int program=OffscreenReflectionAccess.get().program();if(program<=0||uniforms(program).clip<0)return;
        int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_ENABLE_BIT|GL11.GL_VIEWPORT_BIT|GL11.GL_SCISSOR_BIT);
        try{enter();GL11.glDisable(GL11.GL_BLEND);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL12.glDrawRangeElements(mode,start,end,count,type,offset);chunkCopies++;}
        catch(Throwable error){OffscreenReflectionState.fail(error);}finally{leave(program);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL11.glPopAttrib();}
    }
    static void cameraBegin(){if(source&&current!=null)GL11.glViewport(0,0,current.w,current.h);}
    private static void enter(){source=true;TOUCHED.clear();GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,current.fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);cameraBegin();GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);programBound(OffscreenReflectionAccess.get().program());}
    private static void leave(int program){source=false;for(int p:TOUCHED){ShaderHelper.glUseProgramObjectARB(p);Uniforms u=uniforms(p);GL20.glUniform4f(u.clip,1,1,0,0);GL20.glUniform1f(u.pass,0);GL20.glUniform2f(u.origin,0,0);}TOUCHED.clear();ShaderHelper.glUseProgramObjectARB(program);}
    static void programBound(int program){
        if(!source||current==null||program<=0)return;Uniforms u=uniforms(program);Target t=current;TOUCHED.add(program);
        GL20.glUniform4f(u.clip,t.vp[2]/t.w,t.vp[3]/t.h,-(float)t.top/t.h,1);GL20.glUniform1f(u.pass,1);GL20.glUniform4f(u.view,t.vp[0],t.vp[1],t.vp[2],t.vp[3]);GL20.glUniform2f(u.origin,t.vp[0]-t.pad,t.vp[1]);
    }
    static void renderActor(ModelSlotRenderData data,ModelCameraRenderData camera){
        if(current==null||finished)return;
        OffscreenReflectionAccess access=OffscreenReflectionAccess.get();var core=zombie.core.Core.getInstance();
        int projectionDepth=access.matrixDepth(core.projectionMatrixStack),modelDepth=access.matrixDepth(core.modelViewMatrixStack);
        int attributes=GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH),clientAttributes=GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH);
        int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),program=access.program();ModelCamera previous=ModelCamera.instance;
        ModelCamera own=data.character!=null?zombie.core.opengl.CharacterModelCamera.instance:zombie.vehicles.VehicleModelCamera.instance;
        float angle=own.useAngle,x=own.x,y=own.y,z=own.z,slotDepth=data.modelSlot.model.targetDepth;boolean iso=own.useWorldIso,vehicle=own.inVehicle,mask=own.depthMask;
        var models=data.modelData;if(modelDepths.length<models.size())modelDepths=new float[models.size()];for(int i=0;i<models.size();i++)modelDepths[i]=models.get(i).modelInstance==null?0:models.get(i).modelInstance.targetDepth;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        try{if(!data.checkReady())return;enter();camera.render();cameraBegin();GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);access.renderModel(data);actorCopies++;}
        catch(Throwable error){OffscreenReflectionState.fail(error);}
        finally{
            access.restoreMatrix(core.projectionMatrixStack,projectionDepth);access.restoreMatrix(core.modelViewMatrixStack,modelDepth);
            leave(program);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            while(GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH)>clientAttributes)GL11.glPopClientAttrib();
            while(GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH)>attributes)GL11.glPopAttrib();
            ModelCamera.instance=previous;own.useAngle=angle;own.x=x;own.y=y;own.z=z;own.useWorldIso=iso;own.inVehicle=vehicle;own.depthMask=mask;
            data.modelSlot.model.targetDepth=slotDepth;for(int i=0;i<models.size();i++)if(models.get(i).modelInstance!=null)models.get(i).modelInstance.targetDepth=modelDepths[i];
        }
    }
    private static float[] modelDepths=new float[0];
    static void finish(){
        chunk=false;if(current==null||finished)return;finished=true;
        int previous=OffscreenReflectionAccess.get().program(),fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int colour=bound(COLOUR_UNIT),depth=bound(DEPTH_UNIT),water=bound(15);HASH_BINDING.capture(6);TILE_BINDING.capture(5);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try{
            if(resolveProgram==0)resolveProgram=program(scatterFragment());
            Target t=current;OffscreenReflectionAccess a=OffscreenReflectionAccess.get();
            // Mirrors can release image 6 before puddles; explicitly borrow the current native PPR resources.
            GL42.glBindImageTexture(6,a.integer(a.hash),0,false,0,GL15.GL_READ_WRITE,GL30.GL_R32UI);GL42.glBindImageTexture(5,a.integer(a.tile),0,false,0,GL15.GL_READ_WRITE,GL30.GL_R32UI);bind(15,a.integer(a.map));
            ShaderHelper.glUseProgramObjectARB(resolveProgram);Uniforms u=uniforms(resolveProgram);
            GL20.glUniform4f(u.view,t.vp[0],t.vp[1],t.vp[2],t.vp[3]);GL20.glUniform1f(u.pass,1);GL20.glUniform2f(u.origin,t.vp[0]-t.pad,t.vp[1]);
            set4("pzSsrMapA",t.mapping[0],t.mapping[1],t.mapping[2],t.mapping[3]);set4("pzSsrMapC",t.mapping[4],t.mapping[5],0,1);set4("pzSsrO",Math.floorMod(a.originX(frame),a.integer(a.mapSize)),Math.floorMod(a.originY(frame),a.integer(a.mapSize)),a.integer(a.mapSize),a.epoch(frame));
            set1i("pzSsrHash",6);set1i("pzSsrTiles",5);set1i("pzSsrFrame",a.stamp(frame));set1i("pzSsrWater",15);set1i("sourceColour",COLOUR_UNIT);set1i("sourceDepth",DEPTH_UNIT);set1f("pzSsrMapMin",.4f);set1f("pzSsrReach",a.reach(frame,t.vp));
            bind(COLOUR_UNIT,t.colour);bind(DEPTH_UNIT,t.depth);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL11.glViewport((int)t.vp[0],(int)t.vp[1],(int)t.vp[2],(int)t.vp[3]);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glColorMask(false,false,false,false);GL11.glDepthMask(false);
            // One fragment per source texel; no framebuffer feedback: only image atomics are written.
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,t.fbo);GL11.glDrawBuffer(GL11.GL_NONE);GL11.glViewport(0,0,t.w,t.h);borders(t);GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT|GL42.GL_TEXTURE_FETCH_BARRIER_BIT);resolves++;
        }catch(Throwable error){OffscreenReflectionState.fail(error);}finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL11.glPopAttrib();HASH_BINDING.restore(6);TILE_BINDING.restore(5);bind(15,water);bind(COLOUR_UNIT,colour);bind(DEPTH_UNIT,depth);GL13.glActiveTexture(active);ShaderHelper.glUseProgramObjectARB(previous);}
    }
    static String scatterFragment(){return "#version 150 compatibility\n#extension GL_ARB_shader_image_load_store : require\nuniform vec4 "+OffscreenReflectionShaders.VIEW+";\nuniform float "+OffscreenReflectionShaders.PASS+";\nuniform vec2 "+OffscreenReflectionShaders.ORIGIN+";\nuniform sampler2D sourceColour;uniform sampler2D sourceDepth;\n"+OffscreenReflectionShaders.guardScatter(OffscreenReflectionAccess.get().scatter())+"\nvoid main(){vec2 p=gl_FragCoord.xy+"+OffscreenReflectionShaders.ORIGIN+";if(all(greaterThanEqual(p,"+OffscreenReflectionShaders.VIEW+".xy))&&all(lessThan(p,"+OffscreenReflectionShaders.VIEW+".xy+"+OffscreenReflectionShaders.VIEW+".zw)))discard;ivec2 q=ivec2(gl_FragCoord.xy);vec4 c=texelFetch(sourceColour,q,0);if(c.a<0.5)discard;float d=texelFetch(sourceDepth,q,0).r;pzSsrScatter(c.rgb/max(c.a,0.001),d,p);gl_FragColor=vec4(0);}\n";}
    private static void borders(Target t){
        float y=2*t.vp[3]/t.h-1,left=2f*t.pad/t.w-1,right=2f*(t.pad+t.vp[2])/t.w-1;
        GL11.glBegin(GL11.GL_QUADS);rectangle(-1,y,1,1);rectangle(-1,-1,left,y);rectangle(right,-1,1,y);GL11.glEnd();
    }
    private static void rectangle(float x,float y,float r,float t){GL11.glVertex2f(x,y);GL11.glVertex2f(r,y);GL11.glVertex2f(r,t);GL11.glVertex2f(x,t);}
    static void surfaceUniforms(){
        try{int p=OffscreenReflectionAccess.get().program();if(p<=0)return;Uniforms u=uniforms(p);if(u.size<0)return;Target t=current;
            if(t==null||!finished||!OffscreenReflectionState.enabled()){GL20.glUniform4f(u.size,0,0,0,0);return;}
            GL20.glUniform4f(u.view,t.vp[0],t.vp[1],t.vp[2],t.vp[3]);GL20.glUniform4f(u.size,t.pad,t.top,t.w,t.h);GL20.glUniform1i(u.colour,COLOUR_UNIT);int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);bind(COLOUR_UNIT,t.colour);GL13.glActiveTexture(active);
        }catch(Throwable error){OffscreenReflectionState.fail(error);}
    }
    private record Uniforms(int clip,int pass,int origin,int view,int size,int colour){ }
    static void shadersChanged(){shaderGeneration++;}
    private static Uniforms uniforms(int p){long generation=shaderGeneration;if(cachedGeneration!=generation){PROGRAMS.clear();EXTRA.clear();cachedGeneration=generation;}return PROGRAMS.computeIfAbsent(p,id->new Uniforms(loc(id,OffscreenReflectionShaders.CLIP),loc(id,OffscreenReflectionShaders.PASS),loc(id,OffscreenReflectionShaders.ORIGIN),loc(id,OffscreenReflectionShaders.VIEW),loc(id,OffscreenReflectionShaders.SIZE),loc(id,OffscreenReflectionShaders.COLOUR)));}
    private static final Map<Integer,Map<String,Integer>> EXTRA=new HashMap<>();
    private static int loc(int p,String n){return GL20.glGetUniformLocation(p,n);}
    private static int loc(String n){return EXTRA.computeIfAbsent(resolveProgram,p->new HashMap<>()).computeIfAbsent(n,k->loc(resolveProgram,k));}
    private static void set1i(String n,int x){GL20.glUniform1i(loc(n),x);}private static void set1f(String n,float x){GL20.glUniform1f(loc(n),x);}private static void set4(String n,float x,float y,float z,float w){GL20.glUniform4f(loc(n),x,y,z,w);}
    private static final class ImageBinding {
        int name,level,layered,layer,access,format;
        void capture(int unit){name=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_NAME,unit);level=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LEVEL,unit);layered=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYERED,unit);layer=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYER,unit);access=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_ACCESS,unit);format=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_FORMAT,unit);}
        void restore(int unit){GL42.glBindImageTexture(unit,name,level,layered!=0,layer,access,format);}
    }
    private static int bound(int unit){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}private static void bind(int unit,int texture){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);}
    private static int program(String fragment){int v=shader(GL20.GL_VERTEX_SHADER,"#version 150 compatibility\nvoid main(){gl_Position=gl_Vertex;}"),f=shader(GL20.GL_FRAGMENT_SHADER,fragment),p=GL20.glCreateProgram();try{GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));return p;}finally{GL20.glDeleteShader(v);GL20.glDeleteShader(f);}}
    private static int shader(int kind,String text){int s=GL20.glCreateShader(kind);GL20.glShaderSource(s,text);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0){String log=GL20.glGetShaderInfoLog(s);GL20.glDeleteShader(s);throw new IllegalStateException(log);}return s;}
    static void clear(){for(Target t:TARGETS)if(t!=null)t.release();Arrays.fill(TARGETS,null);current=null;frame=null;chunk=source=finished=false;PROGRAMS.clear();EXTRA.clear();TOUCHED.clear();if(resolveProgram!=0)GL20.glDeleteProgram(resolveProgram);resolveProgram=0;}
}
