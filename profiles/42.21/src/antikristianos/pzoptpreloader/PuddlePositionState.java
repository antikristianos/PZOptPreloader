package antikristianos.pzoptpreloader;

import java.lang.reflect.Field;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoPuddlesGeometry;

/** Preserve pristine native coordinates at cache construction; stable frames need no geometry lookup. */
public final class PuddlePositionState {
    private static final Field X=coordinate("x"),Y=coordinate("y");
    private PuddlePositionState() { }
    private static Field coordinate(String name){
        try{Field field=IsoPuddlesGeometry.class.getDeclaredField(name);field.setAccessible(true);return field;}
        catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    public static void canonical(float[] data,IsoGridSquare[] squares,int count){
        try{
            for(int square=0;square<count;square++){
                IsoPuddlesGeometry geometry=squares[square].getPuddles();
                float[] x=(float[])X.get(geometry),y=(float[])Y.get(geometry);
                for(int vertex=0;vertex<4;vertex++){int at=square*32+vertex*8;data[at+4]=x[vertex];data[at+5]=y[vertex];}
            }
        }catch(IllegalAccessException failure){throw new IllegalStateException("Native puddle coordinates unavailable",failure);}
    }
    public static CharSequence shader(CharSequence input,String path){
        if(input==null||path==null)return input;
        path=path.replace('\\','/');
        if(!path.endsWith("/puddles_common.vert.glsl")&&!path.endsWith("/pzopt_puddles_common.vert.glsl"))return input;
        String source=input.toString();if(source.contains("pzNativePuddleJiggle"))return input;
        String declaration="uniform mat4 ModelViewProjection;",position="ModelViewProjection * vec4(vertex.xy, 0, 1)";
        if(source.indexOf(declaration)<0||source.indexOf(position)<0||source.indexOf(position)!=source.lastIndexOf(position))
            throw new IllegalStateException("Unsupported native puddle vertex boundary: "+path);
        return source.replace(declaration,declaration+"\nuniform vec2 pzNativePuddleJiggle;")
            .replace(position,"ModelViewProjection * vec4(vertex.xy + pzNativePuddleJiggle, 0, 1)");
    }
}
