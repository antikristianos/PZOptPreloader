package antikristianos.pzoptpreloader;

/** Own supplementary shaders. The original AO/sun algorithm stays in the separately installed input. */
public final class ShoreFloorShaders {
    private ShoreFloorShaders() { }
    private static String replaceOnce(String source,String before,String after){
        int at=source.indexOf(before);
        if(at<0||source.indexOf(before,at+before.length())>=0)throw new IllegalStateException("Unsupported shore shader anchor: "+before);
        return source.substring(0,at)+after+source.substring(at+before.length());
    }
    public static String kernel(String source){
        // The native shore is deliberately absent from Src0. Supply just its flat depth as an AO receiver,
        // with the original kernel, strengths, neighbours, vegetation and sun masks still doing the shading.
        String declaration="""
            uniform uint pzShoreMask[8];
            float pzShoreDepth(vec2 pixel, float original) {
                pixel = floor(pixel) + 0.5;
                float row = geo.z < 0.0 ? pixel.y : rect[0].w - pixel.y;
                float diagonal = (row / iso0.z - iso0.w) / iso1.x;
                float across = (pixel.x - iso0.x) * iso0.y;
                ivec2 square = ivec2(floor(vec2(diagonal + across, diagonal - across) * 0.5)) + ivec2(4);
                if (square.x < 0 || square.y < 0 || square.x >= 16 || square.y >= 16) return original;
                int bit = square.y * 16 + square.x;
                if ((pzShoreMask[bit >> 5] & (1u << uint(bit & 31))) == 0u) return original;
                return min(original, (20.0 - diagonal) * iso1.z);
            }
            """;
        source=replaceOnce(source,"float depthAt(vec2 p) {",declaration+"float depthAt(vec2 p) {");
        source=replaceOnce(source,"   return d;\n}","   return pzShoreDepth(p, d);\n}");
        source=replaceOnce(source,"texelFetch(Src0, ivec2(c), 0).r >= 0.99999",
            "pzShoreDepth(c, texelFetch(Src0, ivec2(c), 0).r) >= 0.99999");
        source=replaceOnce(source,"return nSrc > 1 ? depthAt(p) : 1.0;",
            "return pzShoreDepth(p, nSrc > 1 ? depthAt(p) : 1.0);");
        return source;
    }
    public static CharSequence tile(CharSequence text,String fileName){
        if(text==null||fileName==null)return text;
        String path=fileName.replace('\\','/');
        if(path.endsWith("/water.frag")||path.endsWith("/water_hq.frag"))return water(text);
        if(!path.endsWith("/tileWithDepth.frag")&&!path.endsWith("/opaqueWithDepth.frag")&&!path.endsWith("/seamFix2.frag"))return text;
        String source=text.toString();
        if(source.contains("pzShoreEnabled"))return text;
        ShoreFloorLighting.shaderReload();
        String output=source.contains("gl_FragData[0]")?"gl_FragData[0]":"gl_FragColor";
        if(!source.contains(output)||!source.contains("gl_FragDepth"))throw new IllegalStateException("Unsupported shore tile shader: "+path);
        int header=source.indexOf('\n');
        if(header<0||!source.startsWith("#version"))throw new IllegalStateException("Unsupported shore shader header: "+path);
        source=source.substring(0,header+1)+"#ifdef GL_ARB_shader_texture_lod\n#extension GL_ARB_shader_texture_lod : enable\n#endif\n"+source.substring(header+1);
        source=replaceOnce(source,"void main()","void pzShoreOriginal()");
        return source+"\n#define PZ_SHORE_OUT "+output+"\n"+TILE;
    }
    /** Keep native per-pixel bank coverage when the installed reflection pass blends over water. */
    private static CharSequence water(CharSequence text){
        String source=text.toString();
        if(source.contains("pzShoreWaterBase"))return text;
        String anchor="fragColor = pzSsrApply(fragColor, gm);";
        if(!source.contains(anchor))return text; // SSR disabled: preserve the native program exactly.
        return replaceOnce(source,anchor,"""
            vec4 pzShoreWaterBase = fragColor;
            fragColor = pzSsrApply(fragColor, gm);
            // Recover straight colour before restoring coverage: simply restoring alpha
            // would overweight the reflected colour at partially covered bank pixels.
            fragColor.rgb = mix(pzShoreWaterBase.rgb, fragColor.rgb, fragColor.a);
            fragColor.a = pzShoreWaterBase.a;
            """);
    }
    public static final String TILE="""
        uniform float pzShoreEnabled;
        uniform sampler2D pzShoreTerm, pzShoreOld;
        uniform vec4 pzShoreX, pzShoreY;
        uniform vec4 pzShoreParams;
        uniform vec4 pzShoreCloudRead;
        uniform sampler2D pzCloudFieldW;
        uniform vec4 pzCloudU, pzCloudV, pzCloudP, pzCloudD, pzCloudE;
        float pzShoreDirectShare(vec2 uv) {
            if (pzShoreCloudRead.x < 0.5) return pzShoreParams.y;
            #ifdef GL_ARB_shader_texture_lod
            if (pzShoreCloudRead.y > 0.5) return texture2DLod(pzShoreTerm, uv, pzShoreCloudRead.z).g;
            #endif
            return texture2D(pzShoreTerm, uv, pzShoreCloudRead.w).g;
        }
        float pzShoreCloud() {
            if (pzCloudP.x <= 0.0) return 1.0;
            vec4 pixel = vec4(gl_FragCoord.xy, gl_FragDepth, 1.0);
            vec2 uv = vec2(dot(pzCloudU, pixel), dot(pzCloudV, pixel));
            float density = clamp((texture2D(pzCloudFieldW, uv).r - pzCloudP.y) * pzCloudP.z, 0.0, 1.0);
            float erosion = texture2D(pzCloudFieldW, uv * pzCloudD.x + pzCloudD.yz).g * pzCloudP.w;
            density = clamp((density - erosion) / max(1.0 - erosion, 0.0001), 0.0, 1.0);
            return 1.0 - pzCloudD.w * (1.0 - exp(-3.0 * density));
        }
        void main() {
            pzShoreOriginal();
            if (pzShoreEnabled < 0.5) return;
            vec4 pixel = vec4(gl_FragCoord.xy, 0.0, 1.0);
            vec2 uv = vec2(dot(pzShoreX, pixel), dot(pzShoreY, pixel));
            float light = 1.0;
            if (pzShoreParams.x > 0.5) {
                light = texture2D(pzShoreTerm, uv, -8.0).r;
                if (pzShoreParams.z > 0.0) light = mix(light, texture2D(pzShoreOld, uv, -8.0).r, pzShoreParams.z);
            }
            // The land composite samples the current direct share at its configured LOD.
            // Its separate sun-step fade adjusts baked light, never this cloud share.
            float factor = clamp(light, 0.0, 1.0) * (1.0 - clamp(pzShoreDirectShare(uv), 0.0, 1.0) * (1.0 - pzShoreCloud()));
            PZ_SHORE_OUT.rgb *= factor;
        }
        """;
}
