package antikristianos.pzoptpreloader;

import java.lang.invoke.*;
import java.util.regex.Pattern;

/** Remove native glass/mirror reflections without replacing its cabin or any model. */
public final class NativeVehicleReflections {
    private static final String MARK="/* preloader vehicle glass without reflections */";
    private static final String START="  vec3 R = reflect(V, N);",END="  vec3 o = pzGlassEnc(c);";
    static final String FAR_BACKGROUND="""
          if (see > 0.5) {
            vec3 back = horizon * pzGlassSkyR.w;
            if (pzGlassTex.x == 0.0 && pzGlassK.w >= 0.0) { vec4 pb = pzGlassProbe(pzGlassWorld(V)); back = mix(back, pzGlassLin(pb.rgb), pb.a); }
            inside = pzGlassScene(px, back) * tint * pzGlassT.x;
          }
        """;
    static final String CABIN_BACKGROUND="""
          if (see > 0.5) {
            /* preloader far-window background uses native cabin light */
            inside = pzGlassInt.rgb * lit * 0.5 * pzGlassInt.w;
          }
        """;
    private static final Pattern MIX=Pattern.compile("(?m)^[ \\t]*col\\s*=\\s*mix\\(col,\\s*texRef(?:\\s*/\\s*4\\.0)?,[^;]*ReflectionParam\\.[yz][^;]*;");
    private NativeVehicleReflections(){ }
    public static String prepare(String name,String code){return shader(name,code,enabled());}
    public static boolean enabled(){
        if(RuntimeState.server||NativeFlag.GLASS==null||NativeFlag.ENABLED==null)return false;
        try{return (boolean)NativeFlag.GLASS.invokeExact()&&(boolean)NativeFlag.ENABLED.invokeExact();}catch(Throwable unavailable){return false;}
    }
    private static final class NativeFlag {
        static final MethodHandle GLASS=lookup(true),ENABLED=lookup(false);
        static MethodHandle lookup(boolean glass){try{var l=MethodHandles.publicLookup();return glass?l.unreflectGetter(Class.forName("pzopt.Config").getField("CAR_GLASS")):l.unreflect(Class.forName("pzopt.Overrides").getMethod("enabled"));}catch(ReflectiveOperationException|LinkageError unavailable){return null;}}
    }
    public static String shader(String name,String code,boolean enabled){
        if(!enabled||name==null||code==null||!name.endsWith(".frag")||code.contains(MARK))return code;
        String base=name.replace('\\','/');base=base.substring(base.lastIndexOf('/')+1,base.length()-5);
        boolean glass=base.startsWith("pzopt_glass_");String material=glass?base.substring(12):base;
        if(!NativeVehicleCharacters.material(material))return code;
        if(glass){
            int start=code.indexOf(START),end=code.indexOf(END,start);
            if(start<0||end<start||code.indexOf(START,start+1)>=0||!code.contains(FAR_BACKGROUND)||!code.contains("vec3 pzGlass(vec3 stock, float cover, float open, vec3 litDisplay)"))return code;
            String changed=code.substring(0,start)+MARK+"\n  vec3 refl=vec3(0.0), w=vec3(0.0), spec=vec3(0.0); float F=0.0;\n  vec3 c=open>0.5?inside:inside*tint*pzGlassT.x; pzGlassHdrSpec=vec3(0.0);\n"+code.substring(end);
            changed=changed.replace(FAR_BACKGROUND,CABIN_BACKGROUND);
            if(changed.contains("#define PZ_GLASS_COMPACT"))changed=changed.replace("if (cls < 0.5) discard;","if (cls < 0.5 || cls > 7.5) discard;");
            else{
                // The stock-shaped variant retains its original colour before calling pzGlass.
                changed=MIX.matcher(changed).replaceAll("");
                changed=changed.replace("pzGlassWindowOnly(texCoords);","pzGlassWindowOnly(texCoords); if(pzGlassMirror>0.5) discard;");
            }
            return changed;
        }
        var mixes=MIX.matcher(code);int count=0;while(mixes.find())count++;
        int main=code.indexOf("void main()"),brace=code.indexOf('{',main);
        if(count!=2||main<0||brace<0||!code.contains("uniform sampler2D TextureMask;"))return code;
        String changed=code.substring(0,brace+1)+"\n"+MARK+"\n float preloaderReflectionClass=floor(texture2D(preloaderNativeGlassClass,texCoords).r*255.0+0.5);\n bool preloaderReflectiveBody=preloaderNativeGlassReady<0.5||preloaderReflectionClass<0.5||preloaderReflectionClass>8.5;\n"+code.substring(brace+1);
        changed=MIX.matcher(changed).replaceAll(match->"if(preloaderReflectiveBody) "+match.group().strip());
        if(!changed.contains("uniform sampler2D preloaderNativeGlassClass;"))changed=changed.replace("uniform sampler2D TextureMask;","uniform sampler2D TextureMask;\nuniform sampler2D preloaderNativeGlassClass;\nuniform float preloaderNativeGlassReady;");
        return changed;
    }
}
