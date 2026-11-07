package antikristianos.pzoptpreloader;

/** Preserve native colour/depth semantics while exposing actual late-floor receiver coverage. */
public final class ShoreReceiverShaders {
    private ShoreReceiverShaders() { }
    private static String replaceOnce(String source,String before,String after){
        int at=source.indexOf(before);
        if(at<0||source.indexOf(before,at+before.length())>=0)throw new IllegalStateException("Unsupported shore receiver anchor: "+before);
        return source.substring(0,at)+after+source.substring(at+before.length());
    }
    public static CharSequence tile(CharSequence input,String path){
        if(input==null||path==null)return input;
        path=path.replace('\\','/');
        if(!path.endsWith("/tileWithDepth.frag")&&!path.endsWith("/opaqueWithDepth.frag")&&!path.endsWith("/seamFix2.frag"))return input;
        String source=input.toString();
        if(source.contains("pzShoreReceiverCapture"))return input;
        if(source.contains("gl_FragData[7]"))throw new IllegalStateException("Shore receiver attachment 7 already owned");
        source=source.replace("gl_FragColor","gl_FragData[0]");
        source=replaceOnce(source,"void main()","void pzReceiverFloorOriginal()");
        ShoreReceiverState.shaderReload();
        return source+"\n"+FLOOR;
    }
    public static String shadow(String source){
        if(source.contains("pzShoreReceiverDepth"))return source;
        source=replaceOnce(source,"void main()",SHADOW+"\nvoid pzReceiverShadowOriginal()");
        source=replaceOnce(source,"float d = texelFetch(SceneDepth, ivec2(f), 0).r;",
            "float d = pzShoreReceiverDepth(f, texelFetch(SceneDepth, ivec2(f), 0).r);");
        if(source.contains("wallCut(P, L0,"))source=replaceOnce(source,"float m = 1.0 - mapC.w * alpha * (1.0 - vis);","vis = mix(vis, 1.0, pzStandingCut(P, L0, ba0.xyz, bb0.xyz));\n   float m = 1.0 - mapC.w * alpha * (1.0 - vis);");
        else if(source.contains("wallCut(P, sun.xyz,"))source=replaceOnce(source,"float m = 1.0 - mapC.w * alpha * (1.0 - vis);","vis = mix(vis, 1.0, pzStandingCut(P, sun.xyz, ba0.xyz, bb0.xyz));\n   float m = 1.0 - mapC.w * alpha * (1.0 - vis);");
        else if(source.contains("float m = 1.0 - mapC.w * share * alpha * (1.0 - vis);"))source=replaceOnce(source,"float m = 1.0 - mapC.w * share * alpha * (1.0 - vis);","vis = mix(vis, 1.0, pzStandingCut(P, rd, ba0.xyz, bb0.xyz));\n   float m = 1.0 - mapC.w * share * alpha * (1.0 - vis);");
        return source+"\nvoid main(){pzReceiverShadowOriginal();fragColor.rgb=mix(vec3(1.0),fragColor.rgb,pzShoreReceiverCoverage);}\n";
    }
    public static final String FLOOR="""
        uniform float pzShoreReceiverCapture;
        uniform float pzShoreReceiverOwner;
        void main() {
            pzReceiverFloorOriginal();
            float coverage = clamp(gl_FragData[0].a, 0.0, 1.0);
            // Alpha one makes a valid sample overwrite under either native floor blend convention.
            // Alpha-zero floor texels preserve an already captured overlapping receiver.
            gl_FragData[7] = pzShoreReceiverCapture > 0.5 && coverage > 0.0
                ? vec4(clamp(gl_FragDepth, 0.0, 1.0), coverage, pzShoreReceiverOwner, 1.0) : vec4(0.0);
        }
        """;
    public static final String SHADOW="""
        uniform sampler2D pzShoreReceiver;
        uniform float pzShoreReceiverEnabled;
        uniform int pzStandingCount;
        uniform vec4 pzStandingPillars[64];
        float pzStandingCut(vec3 P, vec3 L, vec3 a, vec3 b) {
            float limit = dot(0.5*(a+b)-P,L), aa=dot(L.xy,L.xy);
            if(limit<=0.03||aa<0.000001)return 0.0;
            for(int i=0;i<pzStandingCount;i++){
                vec4 shape=pzStandingPillars[i];vec2 delta=P.xy-shape.xy;
                // A receiving surface does not occlude its own incoming shadow.
                if(dot(delta,delta)<=0.2551&&P.z>=shape.z-0.03&&P.z<=shape.w+0.03)continue;
                float bb=dot(delta,L.xy),disc=bb*bb-aa*(dot(delta,delta)-0.25);
                if(disc<=0.0)continue;
                float near=(-bb-sqrt(disc))/aa,far=(-bb+sqrt(disc))/aa;
                if(abs(L.z)>0.000001){float z0=(shape.z-P.z)/L.z,z1=(shape.w-P.z)/L.z;near=max(near,min(z0,z1));far=min(far,max(z0,z1));}
                else if(P.z<shape.z||P.z>shape.w)continue;
                if(max(near,0.03)<min(far,limit))return 1.0;
            }return 0.0;
        }
        float pzShoreReceiverCoverage = 1.0;
        float pzShoreReceiverDepth(vec2 pixel, float original) {
            if (pzShoreReceiverEnabled < 0.5) return original;
            ivec2 p = ivec2(pixel), size = textureSize(pzShoreReceiver, 0);
            if (any(lessThan(p, ivec2(0))) || any(greaterThanEqual(p, size))) return original;
            vec2 receiver = texelFetch(pzShoreReceiver, p, 0).rg;
            if (receiver.g <= 0.0 || receiver.r < 0.0 || receiver.r >= original) return original;
            pzShoreReceiverCoverage = clamp(receiver.g, 0.0, 1.0);
            return receiver.r;
        }
        """;
}
