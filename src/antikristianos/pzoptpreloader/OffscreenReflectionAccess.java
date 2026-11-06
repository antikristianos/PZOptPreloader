package antikristianos.pzoptpreloader;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import zombie.iso.*;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;

/** Cached adapters to the separately installed source and native draw-data API. */
final class OffscreenReflectionAccess {
    static final class Holder { static final OffscreenReflectionAccess INSTANCE=new OffscreenReflectionAccess(); }
    static OffscreenReflectionAccess get(){return Holder.INSTANCE;}
    final MethodHandle active,ppr,program,mapping,reach,near,performCharacter,performVehicle;
    final Field renderFrame,gameFrame,view,epoch,stamp,originX,originY,hash,tile,map,nearWater,on;
    final Field reachPct,distortPct,walkPending,pending,drawFailed,rendered,mapSize,matrixEntries;
    final Map<String,Field> chunkBounds=new HashMap<>();
    OffscreenReflectionAccess(){try{
        ClassLoader loader=getClass().getClassLoader();Class<?> ssr=Class.forName("pzopt.Ssr",false,loader),frame=Class.forName("pzopt.Ssr$Frame",false,loader),v=Class.forName("pzopt.Ssr$View",false,loader);
        active=method(ssr,"active");ppr=method(ssr,"ppr");program=method(ssr,"boundProgram");renderFrame=field(ssr,"renderFrame");gameFrame=field(ssr,"gameFrame");view=field(frame,"view");epoch=field(frame,"epoch");stamp=field(frame,"stamp");nearWater=field(frame,"nearWater");on=field(v,"on");originX=field(v,"ox");originY=field(v,"oy");
        mapping=method(v,"mapping",float[].class,float[].class).asType(MethodType.methodType(void.class,Object.class,float[].class,float[].class));
        reach=method(ssr,"reachRows",v,float[].class).asType(MethodType.methodType(float.class,Object.class,float[].class));
        near=method(ssr,"nearCode",frame,IsoMovingObject.class,float.class,boolean.class,float.class,float.class).asType(MethodType.methodType(int.class,Object.class,IsoMovingObject.class,float.class,boolean.class,float.class,float.class));
        hash=field(ssr,"hashTex");tile=field(ssr,"tileTex");map=field(ssr,"mapTex");
        matrixEntries=field(zombie.core.opengl.MatrixStack.class,"matrices");mapSize=field(ssr,"MAP_SIZE");rendered=field(ModelSlotRenderData.class,"rendered");
        Class<?> cfg=Class.forName("pzopt.Config",false,loader);reachPct=field(cfg,"SSR_REACH_PCT");distortPct=field(cfg,"SSR_DISTORT_PCT");
        Class<?> draw=Class.forName("pzopt.CharDraw",false,loader);walkPending=field(draw,"walkPending");pending=field(draw,"pending");drawFailed=field(draw,"failed");
        performCharacter=method(ModelSlotRenderData.class,"performRenderCharacter").asType(MethodType.methodType(void.class,ModelSlotRenderData.class));
        performVehicle=method(ModelSlotRenderData.class,"renderVehicle").asType(MethodType.methodType(void.class,ModelSlotRenderData.class));
        Class<?> levels=Class.forName("zombie.iso.fboRenderChunk.FBORenderLevels",false,loader);for(String n:List.of("renderX","renderY","renderW","renderH"))chunkBounds.put(n,field(levels,n));
    }catch(ReflectiveOperationException error){throw new IllegalStateException("Offscreen source API unavailable",error);}}
    static Field field(Class<?> c,String n)throws ReflectiveOperationException{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    static MethodHandle method(Class<?> c,String n,Class<?>...p)throws ReflectiveOperationException{Method m=c.getDeclaredMethod(n,p);m.setAccessible(true);return MethodHandles.lookup().unreflect(m);}
    boolean enabled(){try{return (boolean)active.invokeExact()&&(boolean)ppr.invokeExact();}catch(Throwable e){throw failure(e);}}
    int program(){try{return (int)program.invokeExact();}catch(Throwable e){throw failure(e);}}
    Object renderFrame(){try{return renderFrame.get(null);}catch(IllegalAccessException e){throw failure(e);}}
    Object gameFrame(){try{return gameFrame.get(null);}catch(IllegalAccessException e){throw failure(e);}}
    Object view(Object f){try{return view.get(f);}catch(IllegalAccessException e){throw failure(e);}}
    boolean on(Object f){try{return f!=null&&on.getBoolean(view(f));}catch(IllegalAccessException e){throw failure(e);}}
    int stamp(Object f){try{return stamp.getInt(f);}catch(IllegalAccessException e){throw failure(e);}}
    int epoch(Object f){try{return epoch.getInt(f);}catch(IllegalAccessException e){throw failure(e);}}
    int originX(Object f){try{return originX.getInt(view(f));}catch(IllegalAccessException e){throw failure(e);}}
    int originY(Object f){try{return originY.getInt(view(f));}catch(IllegalAccessException e){throw failure(e);}}
    int integer(Field f){try{return f.getInt(null);}catch(IllegalAccessException e){throw failure(e);}}
    float bound(Object levels,String n){try{return chunkBounds.get(n).getFloat(levels);}catch(IllegalAccessException e){throw failure(e);}}
    boolean hasSources(Object f){try{return f!=null&&!((Map<?,?>)nearWater.get(f)).isEmpty();}catch(IllegalAccessException e){throw failure(e);}}
    boolean relevant(Object f,Object depth){try{return f!=null&&((Map<?,?>)nearWater.get(f)).containsKey(depth);}catch(IllegalAccessException e){throw failure(e);}}
    void mapping(Object frame,float[] vp,float[] out){try{mapping.invokeExact(view(frame),vp,out);}catch(Throwable e){throw failure(e);}}
    float reach(Object frame,float[] vp){try{return (float)reach.invokeExact(view(frame),vp);}catch(Throwable e){throw failure(e);}}
    int near(Object f,IsoMovingObject o){try{boolean vehicle=o instanceof zombie.vehicles.BaseVehicle;return (int)near.invokeExact(f,o,o.getZ(),vehicle,vehicle?2.6f:.4f,vehicle?.8f:.75f);}catch(Throwable e){throw failure(e);}}
    /** Native preparation remains owned by the source; wait without consuming its join/start state. */
    boolean awaitDrawData(){try{
        AtomicInteger walk=(AtomicInteger)walkPending.get(null),draw=(AtomicInteger)pending.get(null);long until=System.nanoTime()+100_000_000L;
        while(walk.get()!=0||draw.get()!=0){if(System.nanoTime()>=until)return false;LockSupport.parkNanos(20_000L);}
        return !drawFailed.getBoolean(null);
    }catch(IllegalAccessException e){throw failure(e);}}
    int matrixDepth(zombie.core.opengl.MatrixStack stack){try{return ((Deque<?>)matrixEntries.get(stack)).size();}catch(IllegalAccessException e){throw failure(e);}}
    void restoreMatrix(zombie.core.opengl.MatrixStack stack,int depth){while(matrixDepth(stack)>depth)stack.pop();}
    void resetSource(ModelSlotRenderData data){try{rendered.setBoolean(data,false);}catch(IllegalAccessException e){throw failure(e);}}
    void renderModel(ModelSlotRenderData data)throws Throwable{if(data.character!=null)performCharacter.invokeExact(data);else performVehicle.invokeExact(data);}
    String scatter(){try{Class<?> c=renderFrame.getDeclaringClass();return (String)field(c,"SCATTER_FN").get(null);}catch(ReflectiveOperationException e){throw failure(e);}}
    static IllegalStateException failure(Throwable e){return new IllegalStateException("Offscreen source access failed",e);}
}