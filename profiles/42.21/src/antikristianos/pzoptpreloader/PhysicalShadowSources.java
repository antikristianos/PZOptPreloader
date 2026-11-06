package antikristianos.pzoptpreloader;

import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.opengl.*;

/** Physical cached geometry is consulted by sun rays, never by the camera's receiver reconstruction. */
public final class PhysicalShadowSources {
    private static final int FIRST_UNIT=16, COUNT=9;
    private static final Map<Integer,int[]> LOCATIONS=new HashMap<>();
    private static final int[] PREVIOUS=new int[COUNT];
    private PhysicalShadowSources(){ }
    public static String shader(String input){
        String anchor="float sd = depthAt(sp);";
        int at=input.indexOf(anchor);if(at<0||at!=input.lastIndexOf(anchor))throw new IllegalStateException("Unsupported physical sun depth query");
        StringBuilder helpers=new StringBuilder("\nuniform int pzPhysicalSources;\n");
        for(int i=0;i<COUNT;i++)helpers.append("uniform sampler2D pzPhysical").append(i).append(";\n");
        helpers.append("float pzPhysicalDepth(vec2 p,float visible){\n if(pzPhysicalSources==0)return visible;\n vec2 q;float s;\n");
        for(int i=0;i<COUNT;i++)helpers.append("if(nSrc>").append(i).append("){q=p-rect[").append(i).append("].xy;if(all(greaterThanEqual(q,vec2(0)))&&all(lessThan(q,rect[").append(i).append("].zw))){s=texelFetch(pzPhysical").append(i).append(",ivec2(q),0).r;if(s<0.99999)visible=min(visible,s+off[").append(i).append("]);}}\n");
        helpers.append("return visible;\n}\n");
        int function=input.indexOf("float sunVisibility(");if(function<0)throw new IllegalStateException("Unsupported physical sun function boundary");
        return input.substring(0,function)+helpers+input.substring(function,at)+"float sd = pzPhysicalDepth(sp,depthAt(sp));"+input.substring(at+anchor.length());
    }
    private static final class Access {
        static final Field SOURCES;
        static{try{SOURCES=Class.forName("pzopt.ChunkAo$Job").getDeclaredField("srcTex");SOURCES.setAccessible(true);}catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
    }
    public static void draw(int mode,int first,int count,Object job){
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int[] locations=LOCATIONS.computeIfAbsent(program,p->{int[] u=new int[COUNT+1];u[0]=GL20.glGetUniformLocation(p,"pzPhysicalSources");if(u[0]>=0)for(int i=0;i<COUNT;i++)u[i+1]=GL20.glGetUniformLocation(p,"pzPhysical"+i);return u;});
        if(locations[0]<0){GL11.glDrawArrays(mode,first,count);return;}
        int[] inputs;try{inputs=(int[])Access.SOURCES.get(job);}catch(IllegalAccessException e){throw new IllegalStateException("Physical sun source inputs",e);}
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);boolean any=false;
        for(int i=0;i<COUNT;i++){
            GL13.glActiveTexture(GL13.GL_TEXTURE0+FIRST_UNIT+i);PREVIOUS[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int physical=i<inputs.length?WallReflectionSource.shadowTexture(inputs[i]):0;
            // A native source is a valid fallback for slots whose physical cache has not been built yet.
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,physical!=0?physical:i<inputs.length?inputs[i]:0);any|=physical!=0;GL20.glUniform1i(locations[i+1],FIRST_UNIT+i);
        }
        GL20.glUniform1i(locations[0],any?1:0);GL13.glActiveTexture(active);
        try{GL11.glDrawArrays(mode,first,count);}
        finally{GL20.glUniform1i(locations[0],0);for(int i=0;i<COUNT;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+FIRST_UNIT+i);GL11.glBindTexture(GL11.GL_TEXTURE_2D,PREVIOUS[i]);}GL13.glActiveTexture(active);}
    }
    public static void clear(){LOCATIONS.clear();}
}
