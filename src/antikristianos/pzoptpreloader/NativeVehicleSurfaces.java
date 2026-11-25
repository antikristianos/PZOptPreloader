package antikristianos.pzoptpreloader;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import zombie.core.Core;
import zombie.core.skinnedmodel.ModelManager;
import zombie.core.skinnedmodel.ModelCamera;
import zombie.core.skinnedmodel.model.*;
import zombie.core.skinnedmodel.shader.Shader;
import zombie.core.textures.Texture;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehicleModelCamera;

/** Native panes and seated actor depth share the vehicle's physical snapshot; all original meshes remain. */
public final class NativeVehicleSurfaces {
    private static final ThreadLocal<Scope> scopes=ThreadLocal.withInitial(Scope::new);
    private static final WeakHashMap<Shader,Uniforms> uniforms=new WeakHashMap<>();
    private static MethodHandle glassMap,mapTexture;
    private static boolean failed;
    private static final Map<ModelSlotRenderData,Boolean> nativeActors=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Matrix4f[]> depthMatrices=ThreadLocal.withInitial(()->new Matrix4f[]{new Matrix4f(),new Matrix4f(),new Matrix4f()});
    private static Field cameraX,cameraY;
    private static boolean depthUnavailable;
    private static final class Scope {
        boolean body,cull,glass,depth;
    }
    private record Uniforms(int program,int ready,int classes) { }
    private NativeVehicleSurfaces(){ }
    public static void snapshot(ModelSlotRenderData data,ModelManager.ModelSlot source,boolean ordinary){
        if(ordinary&&source!=null&&source.character!=null&&NativeVehicleCharacters.vehicle(source.character.getVehicle()))nativeActors.put(data,Boolean.TRUE);
        else nativeActors.remove(data);
    }
    /** Undo exactly CharacterModelCameraBegin's seat transform, retaining the frozen view/projection. */
    public static float actorBias(ModelSlotRenderData data,float squareDepth){
        Matrix4f[] m=depthMatrices.get();
        m[0].identity().rotateZ((float)Math.toRadians(data.vehicleAngleZ)).rotateY((float)Math.toRadians(data.vehicleAngleY)).rotateX((float)Math.toRadians(data.vehicleAngleX)).rotateY((float)Math.PI).translate(data.inVehicleX,data.inVehicleY,-data.inVehicleZ).scale(1.5f).invert();
        m[1].set(Core.getInstance().modelViewMatrixStack.peek()).mul(m[0]);
        m[2].set(Core.getInstance().projectionMatrixStack.peek()).mul(m[1]);
        return squareDepth-m[2].m32()/2f;
    }
    public static void startCharacter(Shader shader,ModelSlotRenderData data,ModelInstanceRenderData instance){
        shader.startCharacter(data,instance);
        if(depthUnavailable||data==null||!data.inVehicle||data.renderToTexture||!Boolean.TRUE.equals(nativeActors.get(data)))return;
        try{
            if(!isoProjection())return;
            if(cameraX==null){
                cameraX=ModelSlotRenderData.class.getDeclaredField("camCharacterX");cameraY=ModelSlotRenderData.class.getDeclaredField("camCharacterY");
                if(cameraX.getType()!=float.class||cameraY.getType()!=float.class)throw new NoSuchFieldException("Native camera coordinate types differ");
                cameraX.setAccessible(true);cameraY.setAccessible(true);
            }
            float physical=(data.centerOfMassY+BaseVehicle.centerOfMassMagic)/2.44949f;
            float square=zombie.iso.IsoDepthHelper.getSquareDepthData((int)Math.floor(cameraX.getFloat(data)),(int)Math.floor(cameraY.getFloat(data)),data.x,data.y,physical).depthStart;
            float bias=actorBias(data,square);
            if(Float.isFinite(bias))shader.setTargetDepth(bias);
        }catch(ReflectiveOperationException|RuntimeException error){depthUnavailable=true;System.err.println("[PZOptPreloader] native seated depth adapter inactive: "+error);}
    }
    private static boolean isoProjection(){Matrix4f projection=Core.getInstance().projectionMatrixStack.peek();return projection!=null&&projection.isAffine();}
    private static boolean world(ModelSlotRenderData slot,boolean ordinary){return ordinary&&slot!=null&&slot.object instanceof BaseVehicle&&ModelCamera.instance==VehicleModelCamera.instance;}
    public static void body(Shader shader,ModelSlotRenderData slot,ModelInstanceRenderData data,boolean ordinary){
        if(!world(slot,ordinary)||shader==null||!NativeVehicleCharacters.material(shader.getName()))return;
        Scope scope=scopes.get();scope.cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);scope.body=true;
        if(failed)return;
        int program=shader.getShaderProgram().getShaderID();Uniforms u=uniforms.get(shader);
        if(u==null||u.program!=program){u=new Uniforms(program,GL20.glGetUniformLocation(program,"preloaderNativeGlassReady"),GL20.glGetUniformLocation(program,"preloaderNativeGlassClass"));uniforms.put(shader,u);}
        if(u.ready<0||u.classes<0)return;GL20.glUniform1f(u.ready,0);
        ModelInstance model=data.modelInstance;VehicleModelInstance vmi=model instanceof VehicleModelInstance v?v:model instanceof VehicleSubModelInstance&&model.parent instanceof VehicleModelInstance v?v:null;if(vmi==null)return;
        try{
            if(glassMap==null){
                Class<?> nativeGlass=Class.forName("pzopt.CarGlass"),map=Class.forName("pzopt.CarGlass$GlassMap");var method=nativeGlass.getDeclaredMethod("glassMap",VehicleModelInstance.class);method.setAccessible(true);var texture=map.getDeclaredField("tex");texture.setAccessible(true);var lookup=MethodHandles.lookup();glassMap=lookup.unreflect(method).asType(MethodType.methodType(Object.class,VehicleModelInstance.class));mapTexture=lookup.unreflectGetter(texture).asType(MethodType.methodType(int.class,Object.class));
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE15);Object map=(Object)glassMap.invokeExact(vmi);int texture=map==null?0:(int)mapTexture.invokeExact(map);
            if(texture>0){GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL20.glUniform1i(u.classes,15);GL20.glUniform1f(u.ready,1);}
        }catch(Throwable error){failed=true;System.err.println("[PZOptPreloader] native pane map adapter inactive: "+error);}
        finally{GL13.glActiveTexture(GL13.GL_TEXTURE0);Texture.lastTextureID=-1;}
    }
    public static void bodyEnd(){Scope scope=scopes.get();if(scope.body){if(scope.cull)GL11.glEnable(GL11.GL_CULL_FACE);else GL11.glDisable(GL11.GL_CULL_FACE);scope.body=false;}}
    public static void glass(Shader shader,ModelSlotRenderData slot,boolean ordinary){
        if(!world(slot,ordinary)||shader==null||!shader.getName().startsWith("pzopt_glass_")||!NativeVehicleCharacters.material(shader.getName().substring(12)))return;
        Scope scope=scopes.get();scope.depth=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);scope.glass=true;GL11.glDepthMask(false);
    }
    public static void glassEnd(){Scope scope=scopes.get();if(scope.glass){GL11.glDepthMask(scope.depth);scope.glass=false;}}
    public static void end(){bodyEnd();glassEnd();}
}
