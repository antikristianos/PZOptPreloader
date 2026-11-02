package antikristianos.pzoptpreloader;

import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.opengl.*;

/** Reuses the installed kept AO/sun terms and their exact multiply/ratio passes for the physical wall colour. */
public final class WallReflectionLighting {
    private static final Map<Integer,Term> TERMS=new HashMap<>();
    private static boolean applying;
    private WallReflectionLighting() { }
    private record Term(Object gl,int index,long key,int w,int h,int depth) { }
    public static void after(Object gl,Object job){
        if(applying)return;
        try{
            Access a=Access.INSTANCE;int[] source=(int[])a.src.get(job);if(a.n.getInt(job)<=0||source[0]<=0)return;
            if(TERMS.size()>8192)TERMS.clear();
            TERMS.put(source[0],new Term(gl,a.index.getInt(job),a.key.getLong(job),a.w.getInt(job),a.h.getInt(job),source[0]));
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Physical wall lighting metadata",error);}
    }
    public static void drawArrays(int mode,int first,int count,Object gl,Object job){
        GL11.glDrawArrays(mode,first,count);if(applying)return;
        try{WallReflectionSource.mirrorLighting(mode,first,count,Access.INSTANCE.colour.getInt(job));}
        catch(IllegalAccessException error){throw new IllegalStateException(error);}
    }
    public static void finish(int originalDepth,int framebuffer,int physicalDepth){
        Term term=TERMS.get(originalDepth);if(term==null||applying)return;
        int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);applying=true;
        try{
            Access a=Access.INSTANCE;Object job=a.jobs.computeIfAbsent(originalDepth,key->{try{return a.constructor.newInstance();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}});
            a.index.setInt(job,term.index);a.key.setLong(job,term.key);a.w.setInt(job,term.w);a.h.setInt(job,term.h);
            a.n.setInt(job,1);((int[])a.src.get(job))[0]=physicalDepth;
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);a.multiply.invoke(term.gl,job);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Physical wall kept lighting",error);}
        finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);applying=false;}
    }
    public static void remove(int originalDepth){TERMS.remove(originalDepth);if(Access.created)Access.INSTANCE.jobs.remove(originalDepth);}
    public static void clear(){TERMS.clear();if(Access.created)Access.INSTANCE.jobs.clear();}
    private static final class Access {
        static boolean created;
        static final Access INSTANCE=new Access();
        final Constructor<?> constructor;final Field index,key,w,h,n,src,colour;final Method multiply;
        final Map<Integer,Object> jobs=new HashMap<>();
        Access(){try{
            Class<?> job=Class.forName("pzopt.ChunkAo$Job"),gl=Class.forName("pzopt.ChunkAo$Gl");
            constructor=job.getDeclaredConstructor();constructor.setAccessible(true);
            index=field(job,"index");key=field(job,"key");w=field(job,"w");h=field(job,"h");n=field(job,"n");src=field(job,"srcTex");colour=field(job,"colorTex");
            multiply=gl.getDeclaredMethod("multiply",job);multiply.setAccessible(true);created=true;
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Physical wall lighting API unavailable",error);}}
        static Field field(Class<?> type,String name)throws ReflectiveOperationException{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    }
}