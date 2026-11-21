package antikristianos.pzoptpreloader;

import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import zombie.core.ShaderHelper;
import zombie.core.SpriteRenderer;
import zombie.core.textures.Texture;

/** Native AO/sun kernels and scratch, with separate kept terms for the full physical tree geometry. */
final class TreeReflectionLighting {
    private static final Map<Integer,Entry> ENTRIES=new HashMap<>();
    private static boolean applying;
    private static int nextIndex=-1,vao;
    private static final Map<Integer,Replay> REPLAYS=new HashMap<>();
    private static final class Entry {
        final Object gl,input,job;final int index;int physicalDepth;
        Entry(Object gl)throws ReflectiveOperationException{this.gl=gl;index=nextIndex--;input=API.INSTANCE.constructor.newInstance();job=API.INSTANCE.constructor.newInstance();}
    }
    static boolean applying(){return applying;}
    static void shadersChanged(){REPLAYS.clear();}
    /** Cloud/step correction must consult this geometry's kept term as well. */
    static Replay beginReplay(int nativeDepth){
        Entry entry=ENTRIES.get(nativeDepth);var target=WallReflectionSource.treeLightingTarget(nativeDepth);if(entry==null||target==null)return null;
        try{
            int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);Replay scope=REPLAYS.computeIfAbsent(program,Replay::new);
            if(scope.exposure<0||scope.current.lo<0&&API.INSTANCE.bindless.getBoolean(null)&&!GL.getCapabilities().GL_ARB_gpu_shader_int64)return null;
            long handle=(long)API.INSTANCE.cloudHandle.invoke(null,target.depth());int texture=(int)API.INSTANCE.cloudTerm.invoke(null,target.depth());
            if(handle<=0&&texture<=0)return null;
            scope.begin(handle,texture,target.depth());return scope;
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Physical tree cloud term",error);}
    }
    static final class Replay {
        final int program,exposure,fade;final Handle current,old;
        final FloatBuffer e=BufferUtils.createFloatBuffer(4),f=BufferUtils.createFloatBuffer(4);final long[] transition=new long[2];boolean oldBound;
        Replay(int p){program=p;exposure=GL20.glGetUniformLocation(p,"pzCloudE");fade=GL20.glGetUniformLocation(p,"pzStepFade");current=new Handle(p,"pzCloudTerm");old=new Handle(p,"pzStepOld");}
        void begin(long handle,int texture,int depth)throws ReflectiveOperationException {
            e.clear();GL20.glGetUniformfv(program,exposure,e);if(fade>=0){f.clear();GL20.glGetUniformfv(program,fade,f);}
            current.bind(handle,texture);GL20.glUniform4f(exposure,-1,e.get(1),e.get(2),e.get(3));oldBound=false;
            if(fade>=0){float weight=(float)API.INSTANCE.stepFade.invoke(null,depth,transition);if(weight>0&&transition[0]>0){old.bind(transition[0],0);oldBound=true;}else weight=0;GL20.glUniform4f(fade,weight,0,0,0);}
        }
        void close(){if(oldBound)old.restore();current.restore();GL20.glUniform4f(exposure,e.get(0),e.get(1),e.get(2),e.get(3));if(fade>=0)GL20.glUniform4f(fade,f.get(0),f.get(1),f.get(2),f.get(3));}
    }
    private static final class Handle {
        final int program,lo,hi,sampler;final LongBuffer value=BufferUtils.createLongBuffer(1);int savedLo,savedHi,unit,texture;long savedHandle;boolean integer,boundTexture;
        Handle(int p,String name){program=p;lo=GL20.glGetUniformLocation(p,name+"Lo");hi=GL20.glGetUniformLocation(p,name+"Hi");sampler=GL20.glGetUniformLocation(p,name);}
        void bind(long handle,int tex)throws IllegalAccessException {
            integer=lo>=0&&hi>=0;boundTexture=false;
            if(integer){savedLo=GL20.glGetUniformi(program,lo);savedHi=GL20.glGetUniformi(program,hi);GL20.glUniform1i(lo,(int)handle);GL20.glUniform1i(hi,(int)(handle>>>32));}
            else if(sampler>=0&&API.INSTANCE.bindless.getBoolean(null)){value.clear();ARBGPUShaderInt64.glGetUniformui64vARB(program,sampler,value);savedHandle=value.get(0);ARBBindlessTexture.glUniformHandleui64ARB(sampler,handle);}
            else if(sampler>=0&&tex>0){unit=GL20.glGetUniformi(program,sampler);int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);GL13.glActiveTexture(active);boundTexture=true;}
        }
        void restore(){if(integer){GL20.glUniform1i(lo,savedLo);GL20.glUniform1i(hi,savedHi);}else if(boundTexture){int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL13.glActiveTexture(active);}else if(sampler>=0)ARBBindlessTexture.glUniformHandleui64ARB(sampler,savedHandle);}
    }
    static void computed(Object gl,Object job){
        if(applying)return;
        try{
            int depth=((int[])API.INSTANCE.src.get(job))[0];var target=WallReflectionSource.treeLightingTarget(depth);if(target==null)return;
            Entry e=ENTRIES.get(depth);if(e!=null&&e.gl!=gl){remove(depth);e=null;}
            if(e==null){e=new Entry(gl);ENTRIES.put(depth,e);}
            API.INSTANCE.copy(job,e.input);apply(e,target,false);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Physical tree lighting inputs",error);}
    }
    /** A newly recaptured full image receives its own term once, never the camera's kept multiply. */
    static boolean reapply(int nativeDepth){
        if(RuntimeState.payload==null||!NativeSource.is(RuntimeState.payload))return false;
        var target=WallReflectionSource.treeLightingTarget(nativeDepth);if(target==null)return false;
        Entry entry=ENTRIES.get(nativeDepth);if(entry!=null)try{apply(entry,target,true);}catch(ReflectiveOperationException error){throw new IllegalStateException("Physical tree lighting refresh",error);}return true;
    }
    private static void apply(Entry entry,WallReflectionSource.TreeLightingTarget target,boolean fresh)throws ReflectiveOperationException {
        API a=API.INSTANCE;a.copy(entry.input,entry.job);
        a.index.setInt(entry.job,entry.index);a.key.setLong(entry.job,((long)target.depth()<<32)^(target.colour()&0xffffffffL));
        // COMPUTE outside the native no-sync in-bake path: that path borrows the camera framebuffer ID.
        a.kind.setInt(entry.job,1);a.fresh.setBoolean(entry.job,fresh);a.stage.setBoolean(entry.job,false);
        a.fbo.setInt(entry.job,target.framebuffer());a.colour.setInt(entry.job,target.colour());a.mipmaps.setBoolean(entry.job,false);a.mipLevels.setInt(entry.job,0);
        int[] inputs=(int[])a.src.get(entry.job);int n=a.n.getInt(entry.job);
        for(int i=0;i<n;i++){int physical=WallReflectionSource.shadowTexture(inputs[i]);if(physical!=0)inputs[i]=physical;}
        inputs[0]=target.depth();entry.physicalDepth=target.depth();
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),buffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),last=Texture.lastTextureID;
        int[] textures=new int[12];for(int i=0;i<textures.length;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);applying=true;
        try{
            if(vao==0)vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            if(fresh){GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target.framebuffer());a.multiply.invoke(entry.gl,entry.job);}else a.compute.invoke(entry.gl,entry.job);
        }finally{
            applying=false;GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glPopAttrib();
            GL30.glBindVertexArray(oldVao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);ShaderHelper.glUseProgramObjectARB(program);
            for(int i=0;i<textures.length;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);}GL13.glActiveTexture(active);Texture.lastTextureID=last;
            SpriteRenderer.ringBuffer.restoreVbos=true;SpriteRenderer.ringBuffer.restoreBoundTextures=true;
        }
    }
    static void remove(int depth){
        Entry e=ENTRIES.remove(depth);if(e==null)return;
        try{
            API a=API.INSTANCE;Object kept=((Map<?,?>)a.entries.get(e.gl)).remove(e.index);if(kept==null)return;
            a.drop.invoke(e.gl,kept);a.fade.invoke(e.gl,kept);a.release.invoke(null,kept);
            int framebuffer=a.keptFbo.getInt(kept),texture=a.keptTex.getInt(kept),query=a.query.getInt(kept);
            if(framebuffer!=0)GL30.glDeleteFramebuffers(framebuffer);if(texture!=0)GL11.glDeleteTextures(texture);if(query!=0)GL15.glDeleteQueries(query);
            ((Map<?,?>)a.byColour.get(e.gl)).values().removeIf(value->value==kept);((Map<?,?>)a.bare.get(e.gl)).remove(e.physicalDepth);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Physical tree kept-light lifetime",error);}
    }
    static void clear(){for(int depth:new ArrayList<>(ENTRIES.keySet()))remove(depth);if(vao!=0)GL30.glDeleteVertexArrays(vao);vao=0;REPLAYS.clear();}
    private static final class API {
        static final API INSTANCE=new API();
        final Constructor<?> constructor;final Field[] fields;
        final Field index,key,kind,fresh,stage,fbo,colour,mipmaps,mipLevels,src,n,entries,byColour,bare,keptFbo,keptTex,query,bindless;
        final Method compute,multiply,drop,fade,release,cloudHandle,cloudTerm,stepFade;
        API(){try{
            Class<?> job=Class.forName("pzopt.ChunkAo$Job"),gl=Class.forName("pzopt.ChunkAo$Gl"),entry=Class.forName("pzopt.ChunkAo$Entry");
            constructor=job.getDeclaredConstructor();constructor.setAccessible(true);fields=Arrays.stream(job.getDeclaredFields()).filter(f->!Modifier.isStatic(f.getModifiers())).toArray(Field[]::new);for(Field f:fields)f.setAccessible(true);
            index=field(job,"index");key=field(job,"key");kind=field(job,"kind");fresh=field(job,"fresh");stage=field(job,"stage");fbo=field(job,"fbo");colour=field(job,"colorTex");mipmaps=field(job,"mipmaps");mipLevels=field(job,"mipLevels");src=field(job,"srcTex");n=field(job,"n");
            entries=field(gl,"entries");byColour=field(gl,"byColour");bare=field(gl,"bareByColour");keptFbo=field(entry,"fbo");keptTex=field(entry,"tex");query=field(entry,"query");
            compute=method(gl,"compute",job);multiply=method(gl,"multiply",job);drop=method(gl,"dropStage",entry);fade=method(gl,"releaseFade",entry);release=method(gl,"release",entry);
            Class<?> ao=Class.forName("pzopt.ChunkAo");cloudHandle=method(ao,"cloudHandle",int.class);cloudTerm=method(ao,"cloudTerm",int.class);stepFade=ao.getDeclaredMethod("stepFade",int.class,long[].class);stepFade.setAccessible(true);bindless=field(Class.forName("pzopt.CloudShadow"),"bindless");
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Physical tree native-light API",error);}}
        void copy(Object from,Object to)throws IllegalAccessException {
            for(Field f:fields){Object value=f.get(from);if(value!=null&&f.getType().isArray()){
                Object dest=f.get(to);int length=Array.getLength(value);
                if(dest==null||Array.getLength(dest)!=length){dest=Array.newInstance(f.getType().getComponentType(),length);f.set(to,dest);}System.arraycopy(value,0,dest,0,length);
            }else f.set(to,value);}
            ShoreFloorLighting.copyMask(from,to);
        }
        static Field field(Class<?> type,String name)throws ReflectiveOperationException{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
        static Method method(Class<?> type,String name,Class<?> argument)throws ReflectiveOperationException{Method m=type.getDeclaredMethod(name,argument);m.setAccessible(true);return m;}
    }
}
