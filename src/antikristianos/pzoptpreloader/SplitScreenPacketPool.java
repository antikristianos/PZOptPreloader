package antikristianos.pzoptpreloader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Array;
import java.lang.invoke.*;
import java.util.*;
import zombie.core.SpriteRenderer;
import zombie.core.sprite.SpriteRenderState;

/** Queued packets belong to a native render-state generation, never a global modulo ring. */
public final class SplitScreenPacketPool {
    private static final IdentityHashMap<Object,Role> ROLES=new IdentityHashMap<>();
    private static final IdentityHashMap<Object,Boolean> FRAMES=new IdentityHashMap<>();
    private static final IdentityHashMap<SpriteRenderState,Leases> LEASES=new IdentityHashMap<>();
    private static final class Leases {long producerTime=Long.MIN_VALUE;final IdentityHashMap<Object,Boolean> frames=new IdentityHashMap<>();}
    private static final class Role {
        final Constructor<?> constructor;
        final MethodHandle retained;
        final Class<?> rowElement;
        final int rowLength;
        final IdentityHashMap<SpriteRenderState,Pool> states=new IdentityHashMap<>();
        Role(Class<?> type,Object original){try{
            if(type.isArray()){
                Object first=((Object[])original)[0];if(!(first instanceof Object[] row))throw new IllegalStateException("Native render packet row unavailable");
                rowElement=type.getComponentType();rowLength=row.length;constructor=rowElement.getDeclaredConstructor();
            }else{rowElement=null;rowLength=0;constructor=type.getDeclaredConstructor();}
            constructor.setAccessible(true);
            if(type.getName().equals("pzopt.Glints$Frame")||type.getName().equals("pzopt.BloodWet$Frame")||type.getName().equals("pzopt.Ripples$Frame")){
                var field=type.getNestHost().getDeclaredField(type.getName().equals("pzopt.Glints$Frame")?"queuedFrame":"lastBuilt");if(field.getType()!=type)throw new IllegalStateException("Native retained render packet type changed");field.setAccessible(true);
                retained=MethodHandles.lookup().unreflectGetter(field).asType(MethodType.methodType(Object.class));
            }else retained=null;
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Native render packet constructor unavailable",error);}}
        Object create(){try{
            if(rowElement==null)return constructor.newInstance();
            Object[] row=(Object[])Array.newInstance(rowElement,rowLength);for(int i=0;i<row.length;i++)row[i]=constructor.newInstance();return row;
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Native render packet construction failed",error);}}
        Object retained(){if(retained==null)return null;try{return (Object)retained.invokeExact();}catch(Throwable error){throw new IllegalStateException("Native retained render packet unavailable",error);}}
    }
    private static final class Pool {long generation=Long.MIN_VALUE;int cursor;final ArrayList<Object> packets=new ArrayList<>();}
    private SplitScreenPacketPool(){ }
    public static Object select(Object[] original,int nativeIndex){
        return selectRole(original,original.getClass().getComponentType());
    }
    public static Object singleton(Object original){return selectRole(original,original.getClass());}
    private static Leases enter(SpriteRenderState state){
        Leases leases=LEASES.get(state);if(leases==null){leases=new Leases();LEASES.put(state,leases);}
        // Publication changes state.time too. Only a new producer entering this state releases its old consumers.
        if(leases.producerTime!=state.time){leases.producerTime=state.time;leases.frames.clear();}
        return leases;
    }
    public static void lease(Object frame){if(FRAMES.containsKey(frame)){SpriteRenderState state=SpriteRenderer.instance.states.getPopulating();enter(state).frames.put(frame,Boolean.TRUE);}}
    private static boolean leased(Object frame){for(Leases leases:LEASES.values())if(leases.frames.containsKey(frame))return true;return false;}
    private static Object selectRole(Object original,Class<?> type){
        Role role=ROLES.get(original);if(role==null){role=new Role(type,original);ROLES.put(original,role);}
        SpriteRenderState state=SpriteRenderer.instance.states.getPopulating();
        enter(state);
        Pool pool=role.states.get(state);if(pool==null){pool=new Pool();role.states.put(state,pool);}
        if(pool.generation!=state.time){pool.generation=state.time;pool.cursor=0;}
        // A native producer may reuse its last built result in later states without selecting again.
        Object retained=role.retained();while(pool.cursor<pool.packets.size()&&(pool.packets.get(pool.cursor)==retained||leased(pool.packets.get(pool.cursor))))pool.cursor++;
        if(pool.cursor==pool.packets.size()){Object packet=role.create();pool.packets.add(packet);if(role.retained!=null)FRAMES.put(packet,Boolean.TRUE);}
        return pool.packets.get(pool.cursor++);
    }
}
