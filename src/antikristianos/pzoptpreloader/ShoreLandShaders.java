package antikristianos.pzoptpreloader;

/** Recover absent edge fragments while retaining native textured overlap and shore fade. */
public final class ShoreLandShaders {
    private ShoreLandShaders() { }
    private static String once(String source,String before,String after){int at=source.indexOf(before);if(at<0||source.indexOf(before,at+before.length())>=0)throw new IllegalStateException("Unsupported shore land shader: "+before);return source.substring(0,at)+after+source.substring(at+before.length());}
    public static String patch(String source){
        if(source.contains("pzLandEnabled"))return source;
        source=once(source,"texture2D(DIFFUSE, texCoord.st, 0.0)","pzLandSample(texCoord.st)");
        source=once(source,"texture2D(DEPTH, texCoord2.st, 0.0).r","pzLandDepth(texCoord2.st,texCoord.st)");
        return once(source,"void main()",HELPERS+"\nvoid main()");
    }
    private static final String HELPERS="""
        uniform float pzLandEnabled;
        uniform vec4 pzLandUv;
        vec2 pzLandLocal(vec2 uv) { return (uv-pzLandUv.xy)/pzLandUv.zw; }
        bool pzLandOwns(vec2 p) {
            vec2 q=vec2(p.x/128.0+p.y/64.0,p.y/64.0-p.x/128.0);
            // Resolve only sub-texel interpolation noise; adjoining tiles use the same half-open rule.
            q=floor(q*1048576.0+0.5)/1048576.0;
            return all(greaterThanEqual(q,vec2(-0.5)))&&all(lessThan(q,vec2(0.5)));
        }
        float pzLandEdge(vec2 p) { return (2048.0-abs(p.x)*32.0-abs(p.y)*64.0)/71.55417528; }
        vec4 pzLandSample(vec2 uv) {
            vec4 original=texture2D(DIFFUSE,uv,0.0);
            if(pzLandEnabled<0.5||original.a>0.0)return original;
            vec2 p=pzLandLocal(uv);
            if(!pzLandOwns(p)||pzLandEdge(p)>1.5)return original;
            vec2 inward=-sign(p)*pzLandUv.zw;
            vec4 sample1=texture2D(DIFFUSE,uv+inward,0.0);
            if(sample1.a>0.0)return sample1;
            vec4 sample2=texture2D(DIFFUSE,uv+2.0*inward,0.0);
            return sample2.a>0.0?sample2:original;
        }
        float pzLandDepth(vec2 depthUv,vec2 diffuseUv) {
            float original=texture2D(DEPTH,depthUv,0.0).r;
            if(pzLandEnabled<0.5)return original;
            // Derivatives require uniform quad flow, before any sample-dependent return.
            vec2 stepUv=vec2(dFdx(depthUv).x/dFdx(diffuseUv).x*pzLandUv.z,
                dFdy(depthUv).y/dFdy(diffuseUv).y*pzLandUv.w);
            if(original>0.0)return original;
            vec2 p=pzLandLocal(diffuseUv);
            if(!pzLandOwns(p)||pzLandEdge(p)>1.5)return original;
            vec2 inward=-sign(p)*stepUv;
            float sample1=texture2D(DEPTH,depthUv+inward,0.0).r;
            return sample1>0.0?sample1:texture2D(DEPTH,depthUv+2.0*inward,0.0).r;
        }
        """;
}
