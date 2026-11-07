package antikristianos.pzoptpreloader;

import java.util.List;
import me.zed_0xff.zombie_buddy.Patch;

/** Preserve the native outfit algorithm; redirect only its shared scratch argument. */
public final class PatchHumanVisualScratch {
    @Patch(className="zombie.core.skinnedmodel.visual.HumanVisual",methodName="getItemVisualLocations",strictMatch=true)
    public static final class BuildLocations {
        @Patch.OnEnter public static void enter(@Patch.Argument(value=1,readOnly=false) List<?> locations){locations=HumanVisualScratch.redirect(locations);}
    }
    @Patch(className="zombie.core.skinnedmodel.visual.HumanVisual",methodName="addClothingItem",strictMatch=true)
    public static final class InsertItem {
        @Patch.OnEnter public static void enter(@Patch.Argument(value=1,readOnly=false) List<?> locations){locations=HumanVisualScratch.redirect(locations);}
    }
}
