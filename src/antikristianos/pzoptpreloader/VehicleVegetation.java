package antikristianos.pzoptpreloader;

import java.lang.invoke.*;
import java.util.*;
import zombie.characters.IsoPlayer;
import zombie.iso.*;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.fboRenderChunk.ObjectRenderLayer;
import zombie.iso.objects.IsoTree;
import zombie.iso.sprite.*;
import zombie.pathfind.VehiclePoly;
import zombie.vehicles.BaseVehicle;

/** ISO camera coverage only. Objects remain in the world and keep their original sprites. */
public final class VehicleVegetation {
    public static final float ALPHA=0f;
    private static final State[] PLAYERS={new State(),new State(),new State(),new State()};
    private VehicleVegetation(){ }
    static final class State {
        IsoCell cell;
        volatile Selection selection=new Selection(Set.of(),Set.of());
        final Set<IsoObject> next=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<IsoObject> nextCached=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<IsoGridSquare> dirty=Collections.newSetFromMap(new IdentityHashMap<>());
        final IdentityHashMap<BaseVehicle,Mask> vehicles=new IdentityHashMap<>();
        final Set<BaseVehicle> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        float minX,maxX,minY,maxY,z;
    }
    private record Selection(Set<IsoObject> all,Set<IsoObject> cached){ }
    static final class Mask {
        final float[] shape=new float[9];boolean initialized;
        final Set<IsoObject> objects=Collections.newSetFromMap(new IdentityHashMap<>());
        final ArrayList<Root> roots=new ArrayList<>();
        boolean unchanged(IsoCell cell,VehiclePoly p,float z){
            if(!initialized||shape[0]!=p.x1||shape[1]!=p.y1||shape[2]!=p.x2||shape[3]!=p.y2||shape[4]!=p.x3||shape[5]!=p.y3||shape[6]!=p.x4||shape[7]!=p.y4||shape[8]!=z)return false;
            for(Root root:roots)if(!root.unchanged(cell))return false;
            return true;
        }
        void refresh(State state,IsoCell cell,VehiclePoly p,float z){
            objects.clear();roots.clear();initialized=false;
            if(!bounds(state,p,z))return;
            shape[0]=p.x1;shape[1]=p.y1;shape[2]=p.x2;shape[3]=p.y2;shape[4]=p.x3;shape[5]=p.y3;shape[6]=p.x4;shape[7]=p.y4;shape[8]=z;
            for(int y=(int)Math.floor(state.minY);y<=Math.ceil(state.maxY);y++)for(int x=(int)Math.floor(state.minX);x<=Math.ceil(state.maxX);x++){
                if(!covered(state,p,x,y,(int)z))continue;
                Root root=new Root(cell.getGridSquare(x,y,(int)z),x,y,(int)z);roots.add(root);
                for(int i=0;i<root.objects.length;i++)if(root.candidates[i])objects.add(root.objects[i]);
            }
            initialized=true;
        }
    }
    private static final class Root {
        final IsoGridSquare square;final int x,y,z;final IsoObject[] objects;final boolean[] candidates;
        Root(IsoGridSquare square,int x,int y,int z){
            this.square=square;this.x=x;this.y=y;this.z=z;
            objects=square==null?new IsoObject[0]:square.getObjects().toArray(IsoObject[]::new);candidates=new boolean[objects.length];
            for(int i=0;i<objects.length;i++)candidates[i]=candidate(objects[i]);
        }
        boolean unchanged(IsoCell cell){
            if(cell.getGridSquare(x,y,z)!=square)return false;
            if(square==null)return true;
            var current=square.getObjects();if(current.size()!=objects.length)return false;
            for(int i=0;i<objects.length;i++)if(current.get(i)!=objects[i]||candidate(objects[i])!=candidates[i])return false;
            return true;
        }
    }
    private static final class Native {
        static final MethodHandle ENABLED=lookup();
        static MethodHandle lookup(){try{return MethodHandles.publicLookup().unreflect(Class.forName("pzopt.Overrides").getMethod("enabled"));}catch(ReflectiveOperationException|LinkageError absent){return null;}}
    }
    static boolean enabled(){
        if(RuntimeState.server||RuntimeState.payload==null||!NativeSource.NEXT_COMMIT.equals(NativeSource.sourceCommit(RuntimeState.payload))||Native.ENABLED==null)return false;
        try{return (boolean)Native.ENABLED.invokeExact();}catch(Throwable unavailable){return false;}
    }
    public static void update(int player){
        if(player<0||player>=PLAYERS.length||WallReflectionSource.sourcePass())return;
        IsoCell cell=IsoWorld.instance==null?null:IsoWorld.instance.currentCell;
        boolean iso=!ViewpointAnimationCompatibility.selected()||ViewpointNativePresentationCompatibility.worldJobs(false);
        State state=PLAYERS[player];state.next.clear();state.nextCached.clear();state.seen.clear();
        if(state.cell!=cell)state.vehicles.clear();
        IsoChunkMap map=cell==null?null:cell.getChunkMap(player);
        if(iso&&enabled()&&IsoPlayer.players[player]!=null&&map!=null&&!map.ignore){
            for(BaseVehicle vehicle:cell.getVehicles()){
                if(vehicle==null||vehicle.isRemovedFromWorld()||vehicle.getScript()==null
                    ||map.getChunkForGridSquare((int)Math.floor(vehicle.getX()),(int)Math.floor(vehicle.getY()))==null)continue;
                boolean running=vehicle.isEngineRunning();Mask mask=state.vehicles.get(vehicle);
                // Retain a parked cache while it remains loaded, even after scrolling offscreen.
                if(!vehicle.isOnScreen()&&(mask==null||running))continue;
                VehiclePoly poly=vehicle.getPoly();if(poly==null)continue;
                if(mask==null){mask=new Mask();state.vehicles.put(vehicle,mask);}
                float z=(float)Math.floor(vehicle.getZ());
                if(!mask.unchanged(cell,poly,z))mask.refresh(state,cell,poly,z);
                state.seen.add(vehicle);contribute(state,mask,running);
            }
        }
        state.vehicles.keySet().removeIf(vehicle->!state.seen.contains(vehicle));
        publish(state,cell,null);
    }
    static void contribute(State state,Mask mask,boolean running){
        state.next.addAll(mask.objects);
        if(!running)state.nextCached.addAll(mask.objects);
    }
    static void publish(State state,IsoCell cell,BaseVehicle vehicle){
        state.dirty.clear();
        if(state.cell!=cell){state.selection=new Selection(Set.of(),Set.of());state.cell=cell;}
        Selection previous=state.selection;
        changedSquares(previous.all,state.next,state.dirty);changedSquares(state.next,previous.all,state.dirty);
        changedSquares(previous.cached,state.nextCached,state.dirty);changedSquares(state.nextCached,previous.cached,state.dirty);
        if(!state.dirty.isEmpty()||state.next.size()!=previous.all.size()||state.nextCached.size()!=previous.cached.size())
            state.selection=new Selection(Set.copyOf(state.next),Set.copyOf(state.nextCached));
        for(IsoGridSquare square:state.dirty)square.invalidateRenderChunkLevel(8192L);
        state.next.clear();state.nextCached.clear();
    }
    private static void changedSquares(Set<IsoObject> from,Set<IsoObject> to,Set<IsoGridSquare> dirty){
        for(IsoObject object:from)if(!to.contains(object)&&object.square!=null)dirty.add(object.square);
    }
    public static boolean candidate(IsoObject object){
        if(object==null||object.square==null||object instanceof IsoTree||!object.getDoRender())return false;
        IsoSprite sprite=object.getSprite();
        return sprite!=null&&!sprite.solidfloor&&sprite.renderLayer!=1
            &&(sprite.isBush||sprite.canBeRemoved&&sprite.getProperties().has(IsoFlagType.vegitation));
    }
    static boolean selected(IsoObject object,int player){
        return object!=null&&player>=0&&player<PLAYERS.length&&PLAYERS[player].selection.all.contains(object);
    }
    static boolean cached(IsoObject object,int player){
        return object!=null&&player>=0&&player<PLAYERS.length&&PLAYERS[player].selection.cached.contains(object);
    }
    public static ObjectRenderLayer layer(ObjectRenderLayer original,IsoObject object){
        if(WallReflectionSource.sourcePass()||!selected(object,IsoCamera.frameState.playerIndex))return original;
        if(cached(object,IsoCamera.frameState.playerIndex))return switch(original){case MinusFloor,MinusFloorSE,Vegetation,Translucent,TranslucentSE -> ObjectRenderLayer.None;default -> original;};
        return switch(original){case MinusFloor,Vegetation -> ObjectRenderLayer.Translucent;case MinusFloorSE -> ObjectRenderLayer.TranslucentSE;default -> original;};
    }
    public static float alpha(float original,IsoObject object){
        int player=IsoCamera.frameState.playerIndex;
        if(WallReflectionSource.sourcePass()||!selected(object,player))return original;
        ObjectRenderLayer layer=object.getRenderInfo(player).layer;
        if(layer==ObjectRenderLayer.Floor||layer==ObjectRenderLayer.TranslucentFloor||layer==ObjectRenderLayer.None&&!cached(object,player))return original;
        float result=Math.min(original,ALPHA);
        object.setAlphaAndTarget(player,result);
        return result;
    }
    public static int depthFunc(int original,boolean perFrame,IsoObject object){
        // The native sprite fallback uses GL_ALWAYS for cards without a depth texture.
        // Once drawn per frame, faded plants must still test against vehicles and the world.
        // perFrame comes from the recorder-aware native context, not the global cache builder.
        if(original!=519||!perFrame||WallReflectionSource.sourcePass()||!selected(object,IsoCamera.frameState.playerIndex))return original;
        return 515; // GL_LEQUAL; keep native depth values, blend and depth-write ownership.
    }
    static boolean bounds(State s,VehiclePoly p,float z){
        s.minX=Math.min(Math.min(p.x1,p.x2),Math.min(p.x3,p.x4))-1;s.maxX=Math.max(Math.max(p.x1,p.x2),Math.max(p.x3,p.x4));
        s.minY=Math.min(Math.min(p.y1,p.y2),Math.min(p.y3,p.y4))-1;s.maxY=Math.max(Math.max(p.y1,p.y2),Math.max(p.y3,p.y4));s.z=z;
        return Float.isFinite(s.minX)&&Float.isFinite(s.maxX)&&Float.isFinite(s.minY)&&Float.isFinite(s.maxY)&&Float.isFinite(z)&&s.minX+1<s.maxX&&s.minY+1<s.maxY;
    }
    static boolean covered(State s,VehiclePoly poly,IsoObject object){
        IsoGridSquare square=object.square;
        return covered(s,poly,square.x,square.y,square.z);
    }
    private static boolean covered(State s,VehiclePoly poly,int tileX,int tileY,int z){
        if(z!=s.z)return false;
        float x=tileX+.5f,y=tileY+.5f;
        return poly.containsPoint(x,y)||poly.containsPoint(x+1,y+1)
            ||crosses(x,y,poly.x1,poly.y1,poly.x2,poly.y2)||crosses(x,y,poly.x2,poly.y2,poly.x3,poly.y3)
            ||crosses(x,y,poly.x3,poly.y3,poly.x4,poly.y4)||crosses(x,y,poly.x4,poly.y4,poly.x1,poly.y1);
    }
    private static boolean crosses(float x,float y,float ax,float ay,float bx,float by){
        if(Math.max(ax,bx)<x||Math.min(ax,bx)>x+1||Math.max(ay,by)<y||Math.min(ay,by)>y+1)return false;
        double sideA=(ax-x)-(ay-y),sideB=(bx-x)-(by-y);
        double from=(bx-ax)*(y-ay)-(by-ay)*(x-ax),to=from+(bx-ax)-(by-ay);
        return sideA*sideB<=0&&from*to<=0;
    }
    public static void clear(){for(State state:PLAYERS){state.next.clear();state.nextCached.clear();state.vehicles.clear();state.seen.clear();publish(state,state.cell,null);state.cell=null;}}
}
