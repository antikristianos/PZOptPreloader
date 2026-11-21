package antikristianos.pzoptpreloader;

import java.util.Locale;
import java.util.regex.*;

/** Own source-only raster and lookup adapters; original shader bodies remain installed inputs. */
public final class OffscreenReflectionShaders {
    public static final String VIEW="pzOptReflectionView", SIZE="pzOptReflectionSize", ORIGIN="pzOptReflectionOrigin", PASS="pzOptReflectionPass", COLOUR="pzOptReflectionColour", CLIP="pzOptReflectionClip";
    public static final String DEPTH="pzOptReflectionDepth",DEPTH_ON="pzOptReflectionDepthOn";
    /** 0: native source; 1: camera chunk owned by a complete physical source; 2: physical replay. */
    public static final String PHYSICAL_MASK_ON="pzOptPhysicalMaskOn";
    private OffscreenReflectionShaders() { }
    public static CharSequence filter(CharSequence input,String path) {
        if(input==null||path==null)return input;
        OffscreenReflectionState.shadersChanged();
        String name=path.replace('\\','/').toLowerCase(Locale.ROOT), source=input.toString();
        boolean chunk=name.matches(".*/(?:chunkshader|pzopt_(?:chunkbase|chunkstock|sfchunk|swchunk))\\.(?:frag|vert)"), model=name.matches(".*/(?:basiceffect|vehicle|imposter|skinned|itemmodel|worlditem)[^/]*\\.(?:frag|vert)");
        if(source.contains("float pzSsrLookup("))source=lookup(source);
        if(chunk||model)source=name.endsWith(".vert")?vertex(source):raster(source,chunk);
        return source;
    }
    private static String vertex(String source) {
        if(source.contains("pzOptReflectionVertexMain"))throw new IllegalStateException("Duplicate reflection source vertex");
        Pattern entry=Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");Matcher m=entry.matcher(source);
        if(!m.find())throw new IllegalStateException("Missing reflection vertex entry");
        source=source.substring(0,m.start())+"uniform vec4 "+CLIP+";\nvoid pzOptReflectionVertexMain()"+source.substring(m.end());
        if(entry.matcher(source).find())throw new IllegalStateException("Ambiguous reflection vertex entry");
        return source+"\nvoid main(){pzOptReflectionVertexMain();if("+CLIP+".w>0.5){gl_Position.x*="+CLIP+".x;gl_Position.y="+CLIP+".y*gl_Position.y+"+CLIP+".z*gl_Position.w;}}\n";
    }
    private static String lookup(String source) {
        if(source.contains("vec3 pzOptReflectionSample("))throw new IllegalStateException("Duplicate reflection source lookup");
        int start=source.indexOf("float pzSsrLookup("),end=source.indexOf("vec4 pzSsrPpr(",start);
        if(end<0)throw new IllegalStateException("Unknown reflection lookup boundary");
        String body=source.substring(start,end);
        Pattern sample=Pattern.compile("r\\s*=\\s*texture2D\\(pzSsrColor,\\s*\\((src.+?)\\)\\s*/\\s*vec2\\(textureSize\\(pzSsrColor,\\s*0\\)\\)\\)\\.rgb;",Pattern.DOTALL);
        Matcher match=sample.matcher(body);if(!match.find())throw new IllegalStateException("Unknown original reflection colour sample");
        String replacement="r = pzOptReflectionSample("+match.group(1)+", vec2(float(p.x)+0.5,src.y), pk, half_, float(p.y));";
        body=body.substring(0,match.start())+replacement+body.substring(match.end());
        if(sample.matcher(body).find())throw new IllegalStateException("Ambiguous reflection colour sample");
        String fade="clamp((pzSsrQ.y - src.y) / 48.0, 0.0, 1.0)";
        if(body.indexOf(fade)<0||body.indexOf(fade)!=body.lastIndexOf(fade))throw new IllegalStateException("Unknown reflection source fade");
        body=body.replace(fade,"pzOptReflectionFade(src.y)");
        String strength="return clamp(pzSsrMapC.w * fade * clamp(fr, 0.35, 2.2) * keep, 0.0, 1.0);";
        if(body.indexOf(strength)<0||body.indexOf(strength)!=body.lastIndexOf(strength))throw new IllegalStateException("Unknown native reflection strength");
        body=body.replace(strength,strength.replace(";"," * pzOptReflectionCoverage;"));
        String helper="uniform sampler2D "+COLOUR+";\nuniform sampler2D "+DEPTH+";\nuniform int "+DEPTH_ON+";\nuniform vec4 "+VIEW+";\nuniform vec4 "+SIZE+";\nfloat pzOptReflectionCoverage=1.0;\n"
            +"vec3 pzOptReflectionSample(vec2 p) {\n"
            +" if ("+SIZE+".y > 0.0 && (p.x < "+VIEW+".x || p.x >= "+VIEW+".x+"+VIEW+".z || p.y >= "+VIEW+".y+"+VIEW+".w)) {\n"
            +"  vec2 q=p-"+VIEW+".xy+vec2("+SIZE+".x,0.0);\n"
            +"  if(any(lessThan(q,vec2(0.0))) || any(greaterThanEqual(q,"+SIZE+".zw))) return vec3(10000.0);\n"
            +"  vec4 c=texture2D("+COLOUR+",q/"+SIZE+".zw);\n  if(c.a<0.01)return vec3(10000.0);pzOptReflectionCoverage=c.a;return c.rgb/max(c.a,0.001);\n }\n"
            +" return texture2D(pzSsrColor,p/vec2(textureSize(pzSsrColor,0))).rgb;\n}\n"
            +"bool pzOptReflectionOwns(vec2 q,vec3 keyColour,bool halfSource,float receiverY) {\n"
            +" if(any(lessThan(q,vec2(0.0))) || any(greaterThanEqual(q,"+SIZE+".zw))) return false;\n"
            +" vec4 base=texture2D("+COLOUR+",q/"+SIZE+".zw);vec3 bits=vec3(31.0,31.0,15.0);\n"
            +" if(base.a<0.01)return false;\n"
            +" if("+DEPTH_ON+">0 && abs(pzSsrMapA.z)>0.000001){\n"
            +"  float sy=q.y+"+VIEW+".y,d=texture2D("+DEPTH+",q/"+SIZE+".zw).r;\n"
            +"  float h=(pzSsrMapC.x*d+pzSsrMapC.y-(pzSsrMapA.z*sy+pzSsrMapA.w))/8.0-pzSsrMapC.z;\n"
            +"  float y=floor(sy-12.0*h/-pzSsrMapA.z+0.5);if(halfSource)y=floor(y/2.0)*2.0;\n"
            +"  return h>0.02 && abs(y-receiverY)<=1.0;\n }\n"
            +" vec3 nativeRGB=clamp(base.rgb/max(base.a,0.001),0.0,1.0);\n"
            +" return base.a>=0.01 && all(lessThanEqual(abs(nativeRGB-keyColour),0.5/bits+vec3(0.001)));\n}\n"
            +"bool pzOptReflectionRaised(vec2 q) {\n"
            +" if("+DEPTH_ON+"<=0 || abs(pzSsrMapA.z)<=0.000001)return true;\n"
            +" float sy=q.y+"+VIEW+".y,d=texture2D("+DEPTH+",q/"+SIZE+".zw).r;\n"
            +" return (pzSsrMapC.x*d+pzSsrMapC.y-(pzSsrMapA.z*sy+pzSsrMapA.w))/8.0-pzSsrMapC.z>0.02;\n}\n"
            +"vec3 pzOptReflectionSample(vec2 p,vec2 keySource,vec3 keyColour,bool halfSource,float receiverY) {\n pzOptReflectionCoverage=1.0;\n"
            +" if("+SIZE+".y>0.0) {\n"
            +"  vec2 origin="+VIEW+".xy-vec2("+SIZE+".x,0.0),q=keySource-origin;\n"
            +"  bool owned=pzOptReflectionOwns(q,keyColour,halfSource,receiverY);\n"
            +"  if(halfSource && !owned) owned=pzOptReflectionOwns(q+vec2(1.0,0.0),keyColour,halfSource,receiverY);\n"
            +"  if(owned) {\n"
            +"    q=p-origin;\n"
            +"    if(all(greaterThanEqual(q,vec2(0.0))) && all(lessThan(q,"+SIZE+".zw))) {\n"
            +"     vec4 c=texture2D("+COLOUR+",q/"+SIZE+".zw);if(c.a>=0.01 && pzOptReflectionRaised(q)){pzOptReflectionCoverage=c.a;return c.rgb/max(c.a,0.001);}\n"
            +"    }\n"
            +"    return keyColour;\n  }\n }\n"
            +" return pzOptReflectionSample(p);\n}\n"
            +"float pzOptReflectionFade(float y) {\n"
            +" float top="+SIZE+".y>0.0 ? "+VIEW+".y+"+VIEW+".w+"+SIZE+".y : pzSsrQ.y;\n"
            +" return clamp((top-y)/48.0,0.0,1.0);\n}\n";
        return source.substring(0,start)+helper+body+source.substring(end);
    }
    private static String raster(String source,boolean chunk) {
        if(source.contains("vec4 pzOptReflectionFragment("))throw new IllegalStateException("Duplicate reflection source raster");
        boolean scatter=chunk&&source.contains("void pzSsrScatter(");
        boolean masked=scatter&&Pattern.compile("\\b(?:varying|in)\\s+vec2\\s+texCoord\\b").matcher(source).find();
        if(scatter){
            source=guardScatter(source);
        }
        if(masked){
            String cutoff="if (pzSsrMapC.w <= 0.0 || PZ_SSR_OUT.a < 0.5) return;";
            if(source.indexOf(cutoff)<0||source.indexOf(cutoff)!=source.lastIndexOf(cutoff))throw new IllegalStateException("Unknown native reflection material cutoff");
            source=source.replace(cutoff,"if (pzSsrMapC.w <= 0.0 || PZ_SSR_OUT.a < (("+PHYSICAL_MASK_ON+">1.5 || "+PASS+">1.5)?0.01:0.5)) return;");
            String call="pzSsrScatter(PZ_SSR_OUT.rgb / PZ_SSR_OUT.a, gl_FragDepth, gl_FragCoord.xy);";
            if(source.indexOf(call)<0||source.indexOf(call)!=source.lastIndexOf(call))throw new IllegalStateException("Unknown camera reflection source boundary");
            source=source.replace(call,"if("+PHYSICAL_MASK_ON+"<0.5 || "+PHYSICAL_MASK_ON+">1.5) "+call);
        }
        source=source.replace("gl_FragCoord","pzOptReflectionFragment()");
        Pattern main=Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");Matcher m=main.matcher(source);
        if(!m.find())throw new IllegalStateException("Unknown source fragment entry");
        source=source.substring(0,m.start())+"void pzOptReflectionCameraMain()"+source.substring(m.end());
        if(main.matcher(source).find())throw new IllegalStateException("Ambiguous source fragment entry");
        Pattern fn=Pattern.compile("\\b(?:void|bool|float|int|uint|[biu]?vec[234]|mat[234])\\s+\\w+\\s*\\(");Matcher first=fn.matcher(source);
        if(!first.find())throw new IllegalStateException("Missing source shader function boundary");
        String helper="uniform vec4 "+VIEW+";\nuniform vec2 "+ORIGIN+";\nuniform float "+PASS+";\n"
            +(masked?"uniform float "+PHYSICAL_MASK_ON+";\n":"")
            +"vec4 pzOptReflectionFragment(){return gl_FragCoord+vec4("+ORIGIN+",0.0,0.0);}\n";
        source=source.substring(0,first.start())+helper+source.substring(first.start());
        return source+"\nvoid main(){\n vec2 p=pzOptReflectionFragment().xy;\n"
            +" if("+PASS+">0.5 && "+PASS+"<1.5 && all(greaterThanEqual(p,"+VIEW+".xy)) && all(lessThan(p,"+VIEW+".xy+"+VIEW+".zw))) discard;\n"
            +" pzOptReflectionCameraMain();\n"
            +(scatter?" if("+PASS+">0.5 && PZ_SSR_OUT.a<0.01) discard;\n":"")+"}\n";
    }
    /** Receiver writes remain inside the original image and this player's visible viewport. */
    public static String guardScatter(String source) {
        String anchor="imageAtomicMax(pzSsrHash, ivec2(x, yb),";
        int pos=source.indexOf(anchor);if(pos<0||pos!=source.lastIndexOf(anchor))throw new IllegalStateException("Unknown reflection reduction boundary");
        String guard="if("+PASS+">0.5 && (float(x)<"+VIEW+".x || float(x)>= "+VIEW+".x+"+VIEW+".z || float(yb)<"+VIEW+".y || float(yb)>= "+VIEW+".y+"+VIEW+".w)) return;\n   ";
        return source.substring(0,pos)+guard+source.substring(pos);
    }
}
