package antikristianos.pzoptpreloader;

import java.util.Locale;
import java.util.regex.*;

/** Native AO/sun channel separation and unchanged-colour contribution capture for a later world draw. */
public final class WorldSunLayerShaders {
    private static volatile String originalMultiply;
    private static volatile String originalBlur;
    private static volatile boolean enabled;
    static volatile long generation;
    private WorldSunLayerShaders() { }
    private static String once(String s,String a,String b){int p=s.indexOf(a);if(p<0||p!=s.lastIndexOf(a))throw new IllegalStateException("Changed sun shader boundary: "+a);return s.substring(0,p)+b+s.substring(p+a.length());}
    public static String blur(String s){
        originalBlur=s;
        s=once(s,"float n = 0.0, rs = 0.0, qs = 0.0;","float n = 0.0, rs = 0.0, qs = 0.0, aos = 0.0;");
        s=once(s,"rs += sunOnly > 0.5 ? sn : a.r * sn;","rs += sunOnly > 0.5 ? sn : a.r * sn; aos += sunOnly > 0.5 ? 1.0 : a.r;");
        s=once(s,"vec4(rs / n, clamp(qs / n, 0.0, 1.0), 0.0, 1.0)","vec4(aos / n, clamp(qs / n, 0.0, 1.0), rs / n, 1.0)");
        s=once(s,"vec4(1.0, n > 0.0 ? clamp(qs / n, 0.0, 1.0) : 0.0, 0.0, 1.0)","vec4(1.0, n > 0.0 ? clamp(qs / n, 0.0, 1.0) : 0.0, 1.0, 1.0)");
        // The newer source alone has this raw AO debug-view branch (both class initializers are pinned).
        if(s.contains("vec4(c.r, 0.0, 0.0, 1.0)"))s=once(s,"vec4(c.r, 0.0, 0.0, 1.0)","vec4(c.r, 0.0, c.r, 1.0)");
        return once(s,"vec4(sunOnly > 0.5 ? sun : ao * sun, clamp(q, 0.0, 1.0), 0.0, 1.0)","vec4(sunOnly > 0.5 ? 1.0 : ao, clamp(q, 0.0, 1.0), sunOnly > 0.5 ? sun : ao * sun, 1.0)");
    }
    public static String copy(String s){return once(s,"texelFetch(Src, ivec2(gl_FragCoord.xy), 0).rg, 0.0, 1.0","texelFetch(Src, ivec2(gl_FragCoord.xy), 0).rgb, 1.0");}
    public static void fadeMask(boolean r,boolean g,boolean b,boolean a){org.lwjgl.opengl.GL11.glColorMask(r,g,r&&g?true:b,a);}
    public static boolean enabled(){return enabled;}
    static boolean supportedContext(){try{return org.lwjgl.opengl.GL.getCapabilities().OpenGL42&&org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_MAX_DRAW_BUFFERS)>=3&&org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL42.GL_MAX_IMAGE_UNITS)>=8;}catch(IllegalStateException noContext){return false;}}
    /** Context exists here: native Gl.init is about to link its blur program. */
    public static String linkBlur(String adapted){enabled=supportedContext();return enabled?adapted:originalBlur;}
    public static String multiply(String s){
        if(!s.contains("float aoReadEdge(")||!s.contains("out vec4 fragColor;"))throw new IllegalStateException("Sun receiver filter missing");
        originalMultiply=s;return s;
    }
    /** Execute each original depth/leaf filter before dividing; never filter a pre-divided term. */
    public static String layerFragment(){
        String s=originalMultiply;if(s==null)throw new IllegalStateException("Original sun receiver filter not initialized");
        int begin=s.indexOf("float aoRead("),end=s.indexOf("out vec4 fragColor;");
        if(begin<0||end<=begin)throw new IllegalStateException("Changed original receiver functions");
        String r=s.substring(begin,end),b=r.replace("aoReadEdge","pzSunReadEdge").replace("aoRead(","pzSunRead(");
        b=once(b,"textureLod(A, uv * scale, 0.0).r","textureLod(A, uv * scale, 0.0).b");
        b=once(b,"textureLod(A, (t + 0.5) * uvPerAo * scale, 0.0).r","textureLod(A, (t + 0.5) * uvPerAo * scale, 0.0).b");
        b=once(b,"textureLod(A, (gl_FragCoord.xy + vec2(o)) * uvPerAo * e.y * scale, 0.0).r","textureLod(A, (gl_FragCoord.xy + vec2(o)) * uvPerAo * e.y * scale, 0.0).b");
        return "#version 140\nuniform sampler2D Ao, Depth;\nuniform vec4 m,e,pn;\n"+r+b+"\nout vec4 fragColor;\nvoid main(){vec2 uv=gl_FragCoord.xy*m.xy;vec2 step=m.xy/e.y;float a=aoReadEdge(Ao,uv,step,vec2(1.0));float c=pzSunReadEdge(Ao,uv,step,vec2(1.0));a=a>0.996?1.0:a;c=c>0.996?1.0:c;float sun=a>0.00001?clamp(c/a,0.0,1.0):1.0;fragColor=vec4(sun,0.0,0.0,1.0);}\n";
    }
    public static CharSequence shaderAndOffscreen(CharSequence source,String path){return shader(OffscreenReflectionShaders.filter(source,path),path);}
    private static String blue(String source){
        source=once(source,"textureLod(A, uv * scale, 0.0).r","textureLod(A, uv * scale, 0.0).b");
        source=once(source,"textureLod(A, (t + 0.5) * uvPerAo * scale, 0.0).r","textureLod(A, (t + 0.5) * uvPerAo * scale, 0.0).b");
        return once(source,"textureLod(A, (gl_FragCoord.xy + vec2(o)) * uvPerAo * e.y * scale, 0.0).r","textureLod(A, (gl_FragCoord.xy + vec2(o)) * uvPerAo * e.y * scale, 0.0).b");
    }
    private static volatile String originalRatio;
    public static String ratio(String original){originalRatio=original;return original;}
    public static String colourMultiply(){if(originalMultiply==null)throw new IllegalStateException("Native multiply uninitialized");return blue(originalMultiply);}
    public static String colourRatio(){if(originalRatio==null)throw new IllegalStateException("Native ratio uninitialized");return blue(originalRatio);}
    /** Native shading and its exact DIFFUSE fetches run unchanged, once per real main/data draw. */
    public static CharSequence shader(CharSequence text,String path){
        if(text==null||path==null||!supportedContext())return text;
        String name=path.replace('\\','/').toLowerCase(Locale.ROOT),s=text.toString();
        boolean chunk=name.matches(".*/(?:chunkshader|pzopt_(?:chunkbase|chunkstock|sfchunk|swchunk))\\.frag");
        boolean shore=name.endsWith(".frag")&&s.contains("uniform float pzShoreEnabled;");
        if(!chunk&&!shore)return text;
        if(s.contains("void pzWorldNative("))throw new IllegalStateException("Duplicate world shadow data marker");generation++;
        Matcher fn=Pattern.compile("\\b(?:void|bool|float|int|uint|[biu]?vec[234]|mat[234])\\s+\\w+\\s*\\(").matcher(s);
        if(!fn.find())throw new IllegalStateException("Native shadow shader function boundary missing");s=s.substring(0,fn.start())+"uniform float pzWorldData=1.0;uniform float pzWorldSourceReady;\n"+s.substring(fn.start());
        if(chunk&&s.contains("void pzSsrScatter(")){int begin=s.indexOf("void pzSsrScatter("),brace=s.indexOf('{',begin);s=s.substring(0,brace+1)+"\n if(pzWorldData<0.5)return;\n"+s.substring(brace+1);}
        // Native motion is produced by the data draw; its exact native wind computation/phase is untouched.
        s=s.replace("imageStore(pzSwMvImg,","if(pzWorldData>0.5)imageStore(pzSwMvImg,").replace("imageStore(pzSwMvTile,","if(pzWorldData>0.5)imageStore(pzSwMvTile,");
        for(String term:new String[]{"pzCloudTerm","pzStepOld"}){
            String sample="textureLod("+term+", "+(s.contains("vec2 pzSwT;")?"pzSwT":"texCoord.st")+", 0.0).r";
            if(s.contains(sample))s=s.replace(sample,"(pzWorldData>0.5?"+sample.substring(0,sample.length()-1)+"b:"+sample+")");
        }
        s=s.replace("pzStepFade.x","(pzStepFade.x*(pzWorldData>0.5?pzWorldSourceReady:1.0))");
        if(shore){
            s=s.replace("pzShoreParams.z","(pzWorldData>0.5&&pzWorldSourceReady<-.5?1.0:pzShoreParams.z)");
            s=once(s,"texture2D(pzShoreTerm, uv, -8.0).r","(pzWorldData>0.5?texture2D(pzShoreTerm, uv, -8.0).b:texture2D(pzShoreTerm, uv, -8.0).r)");
            s=once(s,"texture2D(pzShoreOld, uv, -8.0).r","(pzWorldData>0.5?texture2D(pzShoreOld, uv, -8.0).b:texture2D(pzShoreOld, uv, -8.0).r)");
        }
        Matcher main=Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)").matcher(s);if(!main.find())throw new IllegalStateException("Native world shader main missing");
        s=s.substring(0,main.start())+"void pzWorldNative()"+s.substring(main.end());
        return s+"\nvoid main(){if(pzWorldData < -1.0)discard;pzWorldNative();}\n";
    }
}
