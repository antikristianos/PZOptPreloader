package antikristianos.pzoptpreloader;

/** Copies only the existing installed weather output; its algorithm and native output stay intact. */
public final class ShoreWeatherShaders {
    private ShoreWeatherShaders() { }
    public static String cover(String source) {
        if(!ShoreWeatherState.supported())return source;
        if(source.contains("pzWeatherCapture"))throw new IllegalStateException("Weather capture already installed");
        if(!source.startsWith("#version 330")||!source.contains("out vec4 fragColor;")||!source.contains("void main()"))
            throw new IllegalStateException("Unsupported installed ground-cover shader");
        return source.replace("out vec4 fragColor;","layout(location=0) out vec4 fragColor;\nlayout(location=7) out vec4 pzWeatherCapture;")
            .replace("void main()", "void pzWeatherOriginal()")
            +"\nvoid main() { pzWeatherOriginal(); pzWeatherCapture = fragColor; }\n";
    }
    public static CharSequence snow(CharSequence text) {
        if(!ShoreWeatherState.supported())return text;
        String source=text.toString();
        if(source.contains("pzWeatherCapture"))return text;
        if(!source.contains("gl_FragColor = fragCol;")||!source.contains("void main()"))
            throw new IllegalStateException("Unsupported native snow floor shader");
        // Native GLSL 1.20 MRT output: slot zero remains the identical native colour.
        return source.replace("gl_FragColor = fragCol;", "gl_FragData[0] = fragCol;\n    gl_FragData[7] = fragCol; // pzWeatherCapture");
    }
    public static final String FLOOR="""
        uniform sampler2D pzShoreWeather;
        uniform float pzShoreWeatherEnabled;
        """;
    public static final String APPLY="""
            if (pzShoreWeatherEnabled > 0.5) {
                vec4 weather = texture2D(pzShoreWeather, uv);
                PZ_SHORE_OUT.rgb = weather.rgb + PZ_SHORE_OUT.rgb * (1.0 - weather.a);
            }
        """;
}
