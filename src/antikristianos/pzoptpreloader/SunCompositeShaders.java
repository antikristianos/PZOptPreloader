package antikristianos.pzoptpreloader;

import java.util.Locale;
import java.util.regex.*;

/** Channel and composition adapters over the separately installed, verified lighting shaders. */
public final class SunCompositeShaders {
    private static volatile String originalMultiply;
    private static volatile String originalBlur;
    private static volatile boolean enabled;
    static volatile long generation;
    private SunCompositeShaders() { }
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
    static boolean supportedContext(){try{return org.lwjgl.opengl.GL.getCapabilities().OpenGL33&&org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_MAX_TEXTURE_IMAGE_UNITS)>=32;}catch(IllegalStateException noContext){return false;}}
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
    public static CharSequence shaderAndOffscreen(CharSequence source,String path){return OffscreenReflectionShaders.filter(shader(source,path),path);}
    static String shore(String tail){
        if(supportedContext())return tail;
        tail=once(tail,"uniform sampler2D pzSunLayer, pzSunLayerOld;\nuniform vec2 pzSunLayerParams;\n","");
        return once(tail,"if (pzSunLayerParams.x > 0.5) light *= mix(texture2D(pzSunLayer, uv).r, texture2D(pzSunLayerOld, uv).r, pzSunLayerParams.y);","");
    }
    public static CharSequence shader(CharSequence text,String path){
        if(text==null||path==null)return text;String name=path.replace('\\','/').toLowerCase(Locale.ROOT);
        if(!name.matches(".*/(?:chunkshader|pzopt_(?:chunkbase|chunkstock|sfchunk|swchunk))\\.frag"))return text;
        if(!supportedContext())return text;
        String s=text.toString();if(s.contains("uniform vec2 pzSunLayerParams"))throw new IllegalStateException("Duplicate sun composite shader");
        generation++;
        String uv=s.contains("vec2 pzSwT;")?"pzSwT":"texCoord.st";
        String helpers="\nuniform sampler2D pzSunLayer, pzSunLayerOld;\nuniform vec2 pzSunLayerParams;\nfloat pzSunFactor(vec2 uv){if(pzSunLayerParams.x<0.5)return 1.0;return mix(texture2D(pzSunLayer,uv).r,texture2D(pzSunLayerOld,uv).r,pzSunLayerParams.y);}\n";
        if(s.contains("pzChunkMain();")){
            int p=s.indexOf("void main()");if(p<0)throw new IllegalStateException("Sun SSR composite main missing");
            s=s.substring(0,p)+helpers+s.substring(p);
            return once(s,"pzChunkMain();","pzChunkMain();\n PZ_SSR_OUT.rgb *= pzSunFactor("+uv+");");
        }
        Matcher main=Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)").matcher(s);if(!main.find())throw new IllegalStateException("Sun composite main missing");
        s=s.substring(0,main.start())+"void pzSunInner()"+s.substring(main.end());
        String out=s.contains("out vec4 fragColor;")?"fragColor":"gl_FragColor";
        return s+helpers+"\nvoid main(){pzSunInner();"+out+".rgb *= pzSunFactor("+uv+");}\n";
    }
}
