package antikristianos.pzoptpreloader;

import java.util.Set;
import java.util.regex.Pattern;
import zombie.characters.IsoGameCharacter;
import zombie.vehicles.BaseVehicle;

/** Native seated actors behind real transparent panes; other materials retain their owner. */
public final class NativeVehicleCharacters {
    private static final Set<String> MATERIALS=Set.of("vehicle","vehicle_noreflect","vehicle_multiuv","vehicle_multiuv_noreflect","vehicle_norandom_multiuv","vehicle_norandom_multiuv_noreflect");
    private static final Pattern OUTPUT=Pattern.compile("gl_FragColor\\s*=\\s*vec4\\(\\s*col\\s*,\\s*TexturePainColor\\.a\\s*\\)\\s*;");
    private NativeVehicleCharacters(){ }
    public static boolean material(String name){return MATERIALS.contains(name);}
    public static boolean vehicle(BaseVehicle value){
        if(value==null||value.sprite==null||value.sprite.modelSlot==null)return false;
        var model=value.sprite.modelSlot.model;
        return model!=null&&model.model!=null&&model.model.effect!=null&&MATERIALS.contains(model.model.effect.getName());
    }
    public static boolean show(boolean original,IsoGameCharacter actor,boolean impostor){return original||impostor&&actor!=null&&vehicle(actor.getVehicle());}
    public static boolean drawable(boolean original,BaseVehicle vehicle){return original&&!vehicle(vehicle);}
    public static Object tile(Object original,Object vehicle,boolean ordinary){return ordinary&&vehicle instanceof BaseVehicle v&&vehicle(v)?null:original;}
    public static float proxy(float original,Object vehicle,boolean ordinary){return ordinary&&vehicle instanceof BaseVehicle v&&vehicle(v)?1f:original;}
    public static String shader(String name,String code,boolean active){
        if(!active||name==null||code==null||!name.endsWith(".frag")||code.contains("/* preloader native panes */"))return code;
        String base=name.replace('\\','/');base=base.substring(base.lastIndexOf('/')+1,base.length()-5);
        boolean pane=base.startsWith("pzopt_glass_");String material=pane?base.substring(12):base;
        if(!MATERIALS.contains(material))return code;
        var output=OUTPUT.matcher(code);int begin=-1,end=-1;if(output.find()){begin=output.start();end=output.end();if(output.find())return code;}
        String window=code.contains("float windowAlpha")?"clamp(windowAlpha, 0.0, 1.0)":"clamp(texen1[1][2] + texen1[1][3] + texen1[2][0] + texen1[2][1] + texen1[2][2] + texen1[2][3], 0.0, 1.0)";
        if(!pane){
            if(begin<0||!code.contains("TextureMask"))return code;
            String changed=code.substring(0,begin)+"/* preloader native panes */ float preloaderPaneClass = floor(texture2D(preloaderNativeGlassClass, texCoords).r * 255.0 + 0.5); float preloaderPane = preloaderNativeGlassReady > 0.5 ? (preloaderPaneClass > 0.5 && preloaderPaneClass < 7.5 ? 1.0 : 0.0) : "+window+"; if (preloaderPane > 0.5) discard; gl_FragColor = vec4(col, TexturePainColor.a * (1.0 - preloaderPane));"+code.substring(end);
            return changed.replace("uniform sampler2D TextureMask;","uniform sampler2D TextureMask;\nuniform sampler2D preloaderNativeGlassClass;\nuniform float preloaderNativeGlassReady;");
        }
        String compact="gl_FragColor = vec4(o, litA.a);";
        int finalOutput=code.lastIndexOf(compact);
        if(code.contains("#define PZ_GLASS_COMPACT")&&finalOutput>=0)
            return code.substring(0,finalOutput)+"/* preloader native panes */ gl_FragColor = vec4(o, litA.a * (wf.z > 0.5 ? 0.0 : (pzGlassMirror > 0.5 ? 1.0 : 0.60)));"+code.substring(finalOutput+compact.length());
        if(begin>=0&&code.contains("t4en"))
            return code.substring(0,begin)+"gl_FragColor = vec4(col, TexturePainColor.a * (1.0 - "+window+" * (t4en > 0.5 ? 1.0 : 0.40)));"+code.substring(end);
        return code;
    }
}
