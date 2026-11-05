package antikristianos.pzoptpreloader;
import me.zed_0xff.zombie_buddy.Patch;
/** Native ownership remains separate from the independently fingerprinted source adapter. */
public final class PatchOffscreenReflection {
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderLevels",methodName="calculateOnScreen",strictMatch=true)
    public static final class ChunkVisibility {
        @Patch.OnExit public static void exit(@Patch.This Object levels,@Patch.Argument(0) int level,@Patch.Return(readOnly=false) boolean result){if(OffscreenReflectionRepair.nativeReady())result=OffscreenReflectionState.chunkVisible(levels,result);}
    }
    @Patch(className="zombie.core.opengl.CharacterModelCamera",methodName="Begin",strictMatch=true)
    public static final class CharacterCamera {
        @Patch.OnExit public static void exit(){if(OffscreenReflectionRepair.nativeReady())OffscreenReflectionState.cameraBegin();}
    }
    @Patch(className="zombie.vehicles.VehicleModelCamera",methodName="Begin",strictMatch=true)
    public static final class VehicleCamera {
        @Patch.OnExit public static void exit(){if(OffscreenReflectionRepair.nativeReady())OffscreenReflectionState.cameraBegin();}
    }
    @Patch(className="zombie.core.ShaderHelper",methodName="glUseProgramObjectARB",strictMatch=true)
    public static final class ProgramBinding {
        @Patch.OnExit public static void exit(@Patch.Argument(0) int program){if(OffscreenReflectionRepair.nativeReady())OffscreenReflectionState.programBound(program);}
    }
}