package antikristianos.pzoptpreloader;

import java.util.Locale;
import java.util.regex.*;

/** Own source-only raster and lookup adapters; original shader bodies remain installed inputs. */
public final class OffscreenReflectionShaders {
    public static final String VIEW="pzOptReflectionView", SIZE="pzOptReflectionSize", ORIGIN="pzOptReflectionOrigin", PASS="pzOptReflectionPass", COLOUR="pzOptReflectionColour", CLIP="pzOptReflectionClip";
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
        String replacement="r = pzOptReflectionSample("+match.group(1)+");";
        body=body.substring(0,match.start())+replacement+body.substring(match.end());
        if(sample.matcher(body).find())throw new IllegalStateException("Ambiguous reflection colour sample");
        String fade="clamp((pzSsrQ.y - src.y) / 48.0, 0.0, 1.0)";
        if(body.indexOf(fade)<0||body.indexOf(fade)!=body.lastIndexOf(fade))throw new IllegalStateException("Unknown reflection source fade");
        body=body.replace(fade,"pzOptReflectionFade(src.y)");
        String helper="uniform sampler2D "+COLOUR+";\nuniform vec4 "+VIEW+";\nuniform vec4 "+SIZE+";\n"
            +"vec3 pzOptReflectionSample(vec2 p) {\n"
            +" if ("+SIZE+".y > 0.0 && (p.x < "+VIEW+".x || p.x >= "+VIEW+".x+"+VIEW+".z || p.y >= "+VIEW+".y+"+VIEW+".w)) {\n"
            +"  vec2 q=p-"+VIEW+".xy+vec2("+SIZE+".x,0.0);\n"
            +"  if(any(lessThan(q,vec2(0.0))) || any(greaterThanEqual(q,"+SIZE+".zw))) return vec3(10000.0);\n"
            +"  vec4 c=texture2D("+COLOUR+",q/"+SIZE+".zw);\n  return c.a>=0.5 ? c.rgb : vec3(10000.0);\n }\n"
            +" return texture2D(pzSsrColor,p/vec2(textureSize(pzSsrColor,0))).rgb;\n}\n"
            +"float pzOptReflectionFade(float y) {\n"
            +" float top="+SIZE+".y>0.0 ? "+VIEW+".y+"+VIEW+".w+"+SIZE+".y : pzSsrQ.y;\n"
            +" return clamp((top-y)/48.0,0.0,1.0);\n}\n";
        return source.substring(0,start)+helper+body+source.substring(end);
    }
    private static String raster(String source,boolean chunk) {
        if(source.contains("vec4 pzOptReflectionFragment("))throw new IllegalStateException("Duplicate reflection source raster");
        if(chunk&&source.contains("void pzSsrScatter("))source=guardScatter(source);
        source=source.replace("gl_FragCoord","pzOptReflectionFragment()");
        Pattern main=Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");Matcher m=main.matcher(source);
        if(!m.find())throw new IllegalStateException("Unknown source fragment entry");
        source=source.substring(0,m.start())+"void pzOptReflectionCameraMain()"+source.substring(m.end());
        if(main.matcher(source).find())throw new IllegalStateException("Ambiguous source fragment entry");
        Pattern fn=Pattern.compile("\\b(?:void|bool|float|int|uint|[biu]?vec[234]|mat[234])\\s+\\w+\\s*\\(");Matcher first=fn.matcher(source);
        if(!first.find())throw new IllegalStateException("Missing source shader function boundary");
        String helper="uniform vec4 "+VIEW+";\nuniform vec2 "+ORIGIN+";\nuniform float "+PASS+";\n"
            +"vec4 pzOptReflectionFragment(){return gl_FragCoord+vec4("+ORIGIN+",0.0,0.0);}\n";
        source=source.substring(0,first.start())+helper+source.substring(first.start());
        return source+"\nvoid main(){\n vec2 p=pzOptReflectionFragment().xy;\n"
            +" if("+PASS+">0.5 && all(greaterThanEqual(p,"+VIEW+".xy)) && all(lessThan(p,"+VIEW+".xy+"+VIEW+".zw))) discard;\n"
            +" pzOptReflectionCameraMain();\n}\n";
    }
    /** Receiver writes remain inside the original image and this player's visible viewport. */
    public static String guardScatter(String source) {
        String anchor="imageAtomicMax(pzSsrHash, ivec2(x, yb),";
        int pos=source.indexOf(anchor);if(pos<0||pos!=source.lastIndexOf(anchor))throw new IllegalStateException("Unknown reflection reduction boundary");
        String guard="if("+PASS+">0.5 && (float(x)<"+VIEW+".x || float(x)>= "+VIEW+".x+"+VIEW+".z || float(yb)<"+VIEW+".y || float(yb)>= "+VIEW+".y+"+VIEW+".w)) return;\n   ";
        return source.substring(0,pos)+guard+source.substring(pos);
    }
}
