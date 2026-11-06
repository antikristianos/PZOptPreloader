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
