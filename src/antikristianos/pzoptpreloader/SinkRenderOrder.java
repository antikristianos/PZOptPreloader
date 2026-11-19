package antikristianos.pzoptpreloader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import zombie.core.properties.IsoPropertyType;
import zombie.iso.IsoCamera;
import zombie.iso.IsoDepthHelper;
import zombie.iso.IsoObject;
import zombie.iso.fboRenderChunk.ObjectRenderLayer;
import zombie.iso.sprite.IsoSprite;

/** Put a supported sink above its cached counter without changing the counter's admission. */
public final class SinkRenderOrder {
    // Native depth units: below one fifth of a level, large enough for the sink/counter contact.
    // Keep the depth test enabled so nearer world geometry still occludes the fixture.
    private static final float CONTACT_DEPTH = 0.0005f;
    private static final class Native {
        static final MethodHandle ENABLED = enabled();
        private static MethodHandle enabled() {
            try { return MethodHandles.publicLookup().unreflect(Class.forName("pzopt.Overrides").getMethod("enabled")); }
            catch (ReflectiveOperationException | LinkageError refused) { return null; }
        }
    }
    private SinkRenderOrder() { }
    private static boolean active(IsoObject object,IsoSprite sprite) {
        if(object==null||object.square==null||sprite==null||sprite!=object.getSprite()
                ||!sprite.getProperties().isTableTop()||!"Sink".equals(sprite.getProperties().get(IsoPropertyType.CUSTOM_NAME)))return false;
        Payload payload=RuntimeState.payload;
        if(payload==null||RuntimeState.server||!NativeSource.is(payload)||WallReflectionSource.sourcePass()||Native.ENABLED==null)return false;
        try { return (boolean)Native.ENABLED.invokeExact(); }
        catch(Throwable refused) { return false; }
    }
    private static boolean cachedSupport(IsoObject object) {
        int player=IsoCamera.frameState.playerIndex;
        var objects=object.square.getObjects();
        for(int i=objects.indexOf(object)-1;i>=0;i--) {
            IsoObject lower=objects.get(i);
            IsoSprite sprite=lower.getSprite();
            if(sprite==null||!sprite.getProperties().isTable())continue;
            ObjectRenderLayer layer=lower.getRenderInfo(player).layer;
            return layer==ObjectRenderLayer.MinusFloor||layer==ObjectRenderLayer.MinusFloorSE;
        }
        return false;
    }
    public static ObjectRenderLayer layer(ObjectRenderLayer original,IsoObject object) {
        if((original!=ObjectRenderLayer.MinusFloor&&original!=ObjectRenderLayer.MinusFloorSE)
                ||!active(object,object==null?null:object.getSprite())||!cachedSupport(object))return original;
        return original==ObjectRenderLayer.MinusFloor?ObjectRenderLayer.Translucent:ObjectRenderLayer.TranslucentSE;
    }
    public static float shift(IsoObject object,IsoSprite sprite,boolean perFrame,float z) {
        if(!perFrame||!active(object,sprite)||z!=object.square.z||!cachedSupport(object))return 0;
        ObjectRenderLayer layer=object.getRenderInfo(IsoCamera.frameState.playerIndex).layer;
        if(layer!=ObjectRenderLayer.Translucent&&layer!=ObjectRenderLayer.TranslucentSE)return 0;
        return CONTACT_DEPTH/IsoDepthHelper.LEVEL_DEPTH;
    }
}
