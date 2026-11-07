package antikristianos.pzpeek42;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.IdentityHashMap;
import pzmod.peekaview.PeekAViewMod;
import zombie.core.Core;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.fboRenderChunk.FBORenderCell;
import zombie.iso.fboRenderChunk.FBORenderLevels;
import zombie.iso.objects.IsoTree;

/** Thread-local hits avoid the recorder lock; only a state's owning thread may update it. */
public final class TreeCache {
    private static MethodHandle reachOn,invalidateCopies,linger,onBakeRecorder;
    private static RuntimeException skipBakeRecord;
    private static Map<IsoTree,Integer> crownMarks;
    private static boolean lazyRebakes;
    private static VarHandle upstreamLogging;
    @SuppressWarnings("unchecked")
    private static final Map<IsoTree,State>[] states=new Map[]{new WeakHashMap<>(),new WeakHashMap<>(),new WeakHashMap<>(),new WeakHashMap<>()};
    private static volatile int generation;
    private static final class Hot {
        @SuppressWarnings("unchecked") final Map<IsoTree,State>[] entries=new Map[]{new IdentityHashMap<>(),new IdentityHashMap<>(),new IdentityHashMap<>(),new IdentityHashMap<>()};
        final int[] frames={Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};
        int epoch=-1;
        Map<IsoTree,State> entries(int player){
            if(epoch!=generation){for(var map:entries)map.clear();java.util.Arrays.fill(frames,Integer.MIN_VALUE);epoch=generation;}
            int frame=IsoCamera.frameState.frameCount;
            if(frames[player]!=frame){entries[player].clear();frames[player]=frame;}
            return entries[player];
        }
    }
    private static final ThreadLocal<Hot> HOT=ThreadLocal.withInitial(Hot::new);
    public static long decisions,crownTransitions,recordingFallbacks,cacheHits,weakLookups;

    private static final class State {
        Thread owner;
        int frame=Integer.MIN_VALUE;
        float x,y,z,offX,offY;
        int squareIdentity;
        boolean decision,crown,enabled,aiming,fadeSetting;
        boolean matches(){
            return frame==IsoCamera.frameState.frameCount && x==IsoCamera.frameState.camCharacterX
                && y==IsoCamera.frameState.camCharacterY && z==IsoCamera.frameState.camCharacterZ
                && offX==IsoCamera.frameState.offX && offY==IsoCamera.frameState.offY
                && squareIdentity==System.identityHashCode(IsoCamera.frameState.camCharacterSquare)
                && aiming==PeekAViewMod.currentCameraPlayerAiming && fadeSetting==PeekAViewMod.fadeNWTrees;
        }
    }
    @SuppressWarnings("unchecked")
    public static void initialize()throws ReflectiveOperationException{
        for(String name:new String[]{"fadeNWTrees","currentCameraPlayerAiming","felledCrowns"}){
            Field field=PeekAViewMod.class.getField(name);
            if(!Modifier.isStatic(field.getModifiers()) || field.getType()!=(name.equals("felledCrowns")?Set.class:boolean.class))
                throw new NoSuchFieldException("PeekAView tree-state field changed: "+name);
        }
        for(String name:new String[]{"isActiveTreeFadeForCurrentRenderPlayer","fellsCrown","readTreeCutawayAlpha"}){
            Method method=PeekAViewMod.class.getMethod(name,name.equals("isActiveTreeFadeForCurrentRenderPlayer")?new Class<?>[0]:new Class<?>[]{IsoTree.class});
            if(!Modifier.isStatic(method.getModifiers()) || method.getReturnType()!=(name.equals("readTreeCutawayAlpha")?float.class:boolean.class))
                throw new NoSuchMethodException("PeekAView tree-state method changed: "+name);
        }
        reachOn=method("pzoptReachOn",boolean.class);
        invalidateCopies=method("pzoptInvalidateTreeCopies",void.class,IsoTree.class,int.class);
        linger=method("pzoptTreeLinger",void.class,FBORenderLevels.class,IsoTree.class,int.class);
        Field marks=PeekAViewMod.class.getDeclaredField("fellMarks");marks.setAccessible(true);
        if(!Modifier.isStatic(marks.getModifiers()) || !Map.class.isAssignableFrom(marks.getType()))
            throw new NoSuchFieldException("PeekAView.fellMarks must be a static Map");
        crownMarks=(Map<IsoTree,Integer>)marks.get(null);
        if(crownMarks==null)throw new IllegalStateException("PeekAView crown marks unavailable");
        lazyRebakes=Class.forName("pzopt.Config").getField("TREE_REBAKE_LAZY").getBoolean(null);
        upstreamLogging=MethodHandles.publicLookup().findStaticVarHandle(Class.forName("pzopt.Config"),"DEV_XXL_TREE_LOG",boolean.class);
        Class<?> recording=Class.forName("pzopt.BakeRecord");
        Method recorder=recording.getMethod("onBakeRecorder");
        if(!Modifier.isStatic(recorder.getModifiers()) || recorder.getReturnType()!=boolean.class)
            throw new NoSuchMethodException("BakeRecord.onBakeRecorder signature changed");
        onBakeRecorder=MethodHandles.lookup().unreflect(recorder);
        Field skip=recording.getDeclaredField("SKIP");skip.setAccessible(true);
        skipBakeRecord=(RuntimeException)skip.get(null);
        if(skipBakeRecord==null)throw new IllegalStateException("BakeRecord fallback sentinel unavailable");
    }
    private static MethodHandle method(String name,Class<?> result,Class<?>... args)throws ReflectiveOperationException{
        Method method=FBORenderCell.class.getDeclaredMethod(name,args);method.setAccessible(true);
        if(Modifier.isStatic(method.getModifiers()) || method.getReturnType()!=result)throw new NoSuchMethodException(name);
        return MethodHandles.lookup().unreflect(method);
    }
    private static boolean recording(){
        try{return (boolean)onBakeRecorder.invokeExact();}
        catch(Throwable error){throw new IllegalStateException("PZPeek bake-record context failed",error);}
    }
    private static boolean enabled(boolean recorder){
        return Main.active && PeekAViewMod.fadeNWTrees && (recorder || PeekAViewMod.isActiveTreeFadeForCurrentRenderPlayer());
    }
    private static State evaluate(FBORenderCell cell,IsoTree tree,boolean recorder,boolean enabled){
        int player=IsoCamera.frameState.playerIndex,frame=IsoCamera.frameState.frameCount;
        State state=states[player].get(tree);if(Diagnostics.ENABLED)weakLookups++;
        float x=IsoCamera.frameState.camCharacterX,y=IsoCamera.frameState.camCharacterY,z=IsoCamera.frameState.camCharacterZ;
        float offX=IsoCamera.frameState.offX,offY=IsoCamera.frameState.offY;
        int square=System.identityHashCode(IsoCamera.frameState.camCharacterSquare);
        boolean aiming=PeekAViewMod.currentCameraPlayerAiming,fade=PeekAViewMod.fadeNWTrees;
        boolean matches=state!=null && state.matches();
        if(recorder){
            if(matches){if(Diagnostics.ENABLED)cacheHits++;return state;}
            if(Diagnostics.ENABLED)recordingFallbacks++;throw skipBakeRecord;
        }
        if(matches && state.enabled==enabled){if(Diagnostics.ENABLED)cacheHits++;return state;}
        boolean result;
        boolean geometric=enabled && tree.square!=null && tree.sprite!=null;
        if(geometric)RenderTargets.begin(tree);
        try{result=cell.isTranslucentTree(tree);}finally{if(geometric)RenderTargets.end();}
        if(Diagnostics.ENABLED)decisions++;
        boolean crown=false;
        if(enabled && PeekAViewMod.fellsCrown(tree)){
            Integer mark=crownMarks.get(tree);
            crown=(mark!=null && (mark>>1)>=frame-1)
                || (PeekAViewMod.felledCrowns.contains(tree) && PeekAViewMod.readTreeCutawayAlpha(tree)<1);
        }
        if(crown!=(state!=null && state.crown)){
            try{
                if(!(boolean)reachOn.invokeExact(cell) && !cell.isTreeRenderedEveryFrame(tree)
                        && !Core.getInstance().getOptionDoWindSpriteEffects())crownTransition(cell,tree,player,crown);
            }catch(Throwable error){throw new IllegalStateException("PZPeek tree-cache transition failed",error);}
        }
        // Reuse the foreground owner's allocation across frames. A different writer gets a new state,
        // so it cannot mutate a state in another thread's lock-free identity map.
        if(state==null || state.owner!=Thread.currentThread()){state=new State();state.owner=Thread.currentThread();}
        state.frame=frame;state.x=x;state.y=y;state.z=z;state.offX=offX;state.offY=offY;state.squareIdentity=square;
        state.enabled=enabled;state.decision=result;state.crown=crown;state.aiming=aiming;state.fadeSetting=fade;
        states[player].put(tree,state);
        return state;
    }
    private static State resolve(FBORenderCell cell,IsoTree tree,boolean allowInactive){
        Map<IsoTree,State> local=HOT.get().entries(IsoCamera.frameState.playerIndex);
        State state=local.get(tree);
        if(state!=null && state.matches() && state.enabled==enabled(false)){if(Diagnostics.ENABLED)cacheHits++;return state.enabled || allowInactive?state:null;}
        synchronized(TreeCache.class){
            boolean recorder=recording();
            boolean active=enabled(recorder);if(!active && !allowInactive)return null;
            state=evaluate(cell,tree,recorder,active);
            // Recorders never populate a fast path; a second foreground reader stays synchronized
            // until it has produced its own state, rather than sharing mutable entries lock-free.
            if(!recorder && state.owner==Thread.currentThread())local.put(tree,state);
            return state;
        }
    }
    private static boolean validPlayer(){int player=IsoCamera.frameState.playerIndex;return player>=0 && player<states.length;}
    private static boolean individual(State state,IsoTree tree){return state.enabled && (state.decision || tree.fadeAlpha<1 || state.crown);}
    public static boolean decision(FBORenderCell cell,IsoObject object,boolean aiming){return decision(cell,object);}
    public static boolean decision(FBORenderCell cell,IsoObject object){
        if(!(object instanceof IsoTree tree) || !Main.active || !validPlayer())return cell.isTranslucentTree(object);
        return resolve(cell,tree,true).decision;
    }
    public static int earlyNeeds(FBORenderCell cell,IsoTree tree){
        if(!Main.active || !PeekAViewMod.fadeNWTrees || !validPlayer() || (boolean)upstreamLogging.get())return 0;
        State state=resolve(cell,tree,false);
        return state!=null?(individual(state,tree)?3:1):0;
    }
    public static boolean needsPerFrame(boolean original,FBORenderCell cell,IsoTree tree){
        if(!Main.active || !PeekAViewMod.fadeNWTrees || !validPlayer())return original;
        State state=resolve(cell,tree,false);
        return state!=null?individual(state,tree):original;
    }
    public static boolean bakes(boolean original,FBORenderCell cell,IsoTree tree){
        if(!original || !Main.active || !PeekAViewMod.fadeNWTrees || !validPlayer())return original;
        State state=resolve(cell,tree,false);return state==null || !individual(state,tree);
    }
    public static boolean minusFloor(boolean original,FBORenderCell cell,IsoObject object){
        return !(object instanceof IsoTree tree)?original:bakes(original,cell,tree);
    }
    public static boolean translucentLayer(boolean original,FBORenderCell cell,IsoObject object){
        if(original || !(object instanceof IsoTree tree) || !Main.active || !PeekAViewMod.fadeNWTrees || !validPlayer())return original;
        State state=resolve(cell,tree,false);return state!=null && individual(state,tree);
    }
    public static void finishFrame(){
        int player=IsoCamera.frameState.playerIndex;
        if(player>=0 && player<states.length)HOT.get().entries[player].clear();
    }
    public static synchronized void clear(){
        RenderTargets.clear();
        if(Main.performanceReady)DrawGeometry.clear();
        for(var map:states)map.clear();generation++;HOT.remove();
    }
    private static void crownTransition(FBORenderCell cell,IsoTree tree,int player,boolean out)throws Throwable{
        IsoGridSquare square=tree.getSquare();
        if(square==null || square.chunk==null)return;
        if(out || !lazyRebakes){
            square.chunk.getRenderLevels(player).invalidateLevel(0,4096L);
            IsoGridSquare renderSquare=tree.getRenderSquare();
            if(renderSquare!=null && renderSquare!=square && renderSquare.chunk!=null)
                renderSquare.chunk.getRenderLevels(player).invalidateLevel(0,4096L);
            invalidateCopies.invokeExact(cell,tree,player);
        }else linger.invokeExact(cell,square.chunk.getRenderLevels(player),tree,player);
        if(Diagnostics.ENABLED)crownTransitions++;
    }
}
