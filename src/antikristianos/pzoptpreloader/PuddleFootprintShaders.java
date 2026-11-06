package antikristianos.pzoptpreloader;

/** Native shader chain with a tile-owned floor mask; zero uniform keeps the original geometry. */
public final class PuddleFootprintShaders {
    private PuddleFootprintShaders() { }
    private static String once(String source,String before,String after){int at=source.indexOf(before);if(at<0||source.indexOf(before,at+before.length())>=0)throw new IllegalStateException("Unsupported puddle footprint anchor: "+before);return source.substring(0,at)+after+source.substring(at+before.length());}
    public static CharSequence shader(CharSequence input,String path){
        if(input==null||path==null)return input;path=path.replace('\\','/');String source=input.toString().replace("\r\n","\n");
        boolean vertex=path.endsWith("/puddles_common.vert.glsl")||path.endsWith("/pzopt_puddles_common.vert.glsl");
        boolean fragment=path.endsWith("/puddles_common.frag.glsl")||path.endsWith("/pzopt_puddles_common.frag.glsl");
        if(!vertex&&!fragment)return input;if(source.contains("pzPuddleMaskEnabled"))return input;
        PuddleFootprintState.shaderReload();
        if(vertex){
            source=once(source,"void puddlesMain(void)",VERTEX+"\nvoid puddlesMain(void)");
            source=once(source,"puddlesDirNone = 1.0 - aDirNone;","puddlesDirNone = 1.0 - (aDirNone >= 16.0 ? step(24.0, aDirNone) : aDirNone);");
            String depth=source.contains("vDepth = aFragDepth + pzoptDepthShift;")?"vDepth = aFragDepth + pzoptDepthShift;":"vDepth = aFragDepth;";
            source=once(source,depth,depth+"\n pzPuddleFootprint();");
            for(String name:new String[]{"vertColour","puddlesDirNE","puddlesDirNW","puddlesDirAll","puddlesDirNone"})source=source.replaceAll("\\b"+name+"\\b","pzRaw_"+name);
            return source;
        }
        int header=source.indexOf('\n');
        if(header<0||!source.startsWith("#version "))throw new IllegalStateException("Unsupported puddle fragment header: "+path);
        source=source.substring(0,header+1)+"#extension GL_EXT_gpu_shader4 : require\n"+source.substring(header+1);
        for(String name:new String[]{"vertColour","puddlesDirNE","puddlesDirNW","puddlesDirAll","puddlesDirNone"}){
            String type=name.equals("vertColour")?"vec4":"float";
            source=once(source,"varying "+type+" "+name+";","varying "+type+" pzRaw_"+name+";\n"+type+" "+name+";");
        }
        source=once(source,"float wetLand = PuddlesParams[1].y*puddlesDirNone;","float wetLand = 0.0;");
        source=once(source,"void puddlesMain()",FRAGMENT+"\nvoid puddlesMain()");
        for(String main:new String[]{"puddlesMain","puddlesMain_MQ","puddlesMain_LQ"})source=once(source,"void "+main+"()\n{","void "+main+"()\n{\n pzPuddleFootprint();\n wetLand = PuddlesParams[1].y*puddlesDirNone;");
        return source;
    }
    private static final String VERTEX="""
        uniform float pzPuddleMaskEnabled;
        uniform vec2 pzPuddleMaskInvSize;
        uniform vec4 pzPuddleMaskViewport;
        flat out float pzPuddleOwner;
        flat out vec2 pzPuddleCentre;
        void pzPuddleFootprint() {
            pzPuddleOwner=0.0;pzPuddleCentre=vec2(0.0);
            if(pzPuddleMaskEnabled<0.5 || aDirNone<16.0)return;
            int corner=gl_VertexID & 3;
            vec2 delta=corner==0?vec2(0,0):corner==1?vec2(-64,32):corner==2?vec2(0,64):vec2(64,32);
            vec2 top=vertex.xy-delta;
            vec2 tile=floor(vec2((top.x+1)/128.0+top.y/64.0,top.y/64.0-(top.x+1)/128.0)+.5);
            tile=mod(tile,4096.0);pzPuddleOwner=1+tile.x+tile.y*4096;
            vec2 expansion=(delta-vec2(0,32))/16.0;
            gl_Position=ModelViewProjection*vec4(vertex.xy+pzNativePuddleJiggle+expansion,0,1);
            vec4 centre=ModelViewProjection*vec4(top+vec2(0,32)+pzNativePuddleJiggle,0,1);
            pzPuddleCentre=pzPuddleMaskViewport.xy+(centre.xy/centre.w*.5+.5)*pzPuddleMaskViewport.zw;
            float centreDepth=(aDirNone-16.0-8.0*step(24.0,aDirNone))*.25;
            vDepth+=(aFragDepth-centreDepth)/16.0;
        }
        """;
    private static final String FRAGMENT="""
        uniform sampler2D pzPuddleMask;
        uniform float pzPuddleMaskEnabled;
        uniform vec2 pzPuddleMaskInvSize;
        flat varying float pzPuddleOwner;
        flat varying vec2 pzPuddleCentre;
        void pzPuddleFootprint() {
            vertColour=pzRaw_vertColour;puddlesDirNE=pzRaw_puddlesDirNE;puddlesDirNW=pzRaw_puddlesDirNW;
            puddlesDirAll=pzRaw_puddlesDirAll;puddlesDirNone=pzRaw_puddlesDirNone;
            if(pzPuddleMaskEnabled<0.5 || pzPuddleOwner==0.0)return;
            vec2 distance=gl_FragCoord.xy-pzPuddleCentre;
            vertColour+=(dFdx(pzRaw_vertColour)*distance.x+dFdy(pzRaw_vertColour)*distance.y)/16.0;
            puddlesDirNE+=(dFdx(pzRaw_puddlesDirNE)*distance.x+dFdy(pzRaw_puddlesDirNE)*distance.y)/16.0;
            puddlesDirNW+=(dFdx(pzRaw_puddlesDirNW)*distance.x+dFdy(pzRaw_puddlesDirNW)*distance.y)/16.0;
            puddlesDirAll+=(dFdx(pzRaw_puddlesDirAll)*distance.x+dFdy(pzRaw_puddlesDirAll)*distance.y)/16.0;
            puddlesDirNone+=(dFdx(pzRaw_puddlesDirNone)*distance.x+dFdy(pzRaw_puddlesDirNone)*distance.y)/16.0;
            float owner=texture2D(pzPuddleMask,gl_FragCoord.xy*pzPuddleMaskInvSize).b;
            if(owner!=pzPuddleOwner)discard;
        }
        """;
}
