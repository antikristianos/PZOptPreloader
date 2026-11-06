package antikristianos.pzoptpreloader;

import java.io.InputStream;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.ZipFile;
import zombie.iso.*;
import zombie.iso.fboRenderChunk.*;
import zombie.iso.objects.*;
import zombie.core.properties.IsoPropertyType;

/** Player-position-only door fade using the installed native wall predicate, never Peek state. */
public final class DoorVisibilityState {
    private static final String WALL="zombie/iso/fboRenderChunk/FBORenderCutaways$CutawayWall";
    private static final String QUERY="shouldCutawayFence(Lzombie/iso/IsoGridSquare;I)Z";
    private static final String NATIVE_QUERY="44e7b7bb219e1ff1952c7b4e873b8eed43b7b809cbf0dd2a57c9dbd31db9ed7d";
    private static final String SOURCE_QUERY="761f669659269074607b97d37ad7b90b8b68bb81d9777adccb41880900668c78";
    private static final String HORIZONTAL="cfffb259436c005c19692e3ac77e70928cc637ec946d287d85464a9303c6449e";
    private static final WeakHashMap<IsoObject,Entry> DOORS=new WeakHashMap<>();
    private static IsoCell ownerCell;
    private static long invalidations;
    private DoorVisibilityState() { }
    private static final class Entry {
        final float[] target={Float.NaN,Float.NaN,Float.NaN,Float.NaN};
    }
    private static boolean active(){return !RuntimeState.server&&RuntimeState.payload!=null&&Compatibility.currentGame()&&!WallReflectionSource.sourcePass();}
    private static boolean door(IsoObject object){return object instanceof IsoDoor||object instanceof IsoThumpable thump&&thump.isDoor();}
    private static boolean open(IsoObject object){return object instanceof IsoDoor door?door.isOpen():((IsoThumpable)object).open;}
    private static IsoDirections edge(IsoObject object){return object instanceof IsoDoor door?door.getSpriteEdge(false):((IsoThumpable)object).getSpriteEdge(false);}
    private static boolean eligible(IsoObject object,int player){
        return door(object)&&object.getSquare()!=null&&object.getSprite()!=null&&object.getDoRender()&&!object.isSpriteInvisible()
            &&object.getProperties()!=null&&!object.getProperties().has(IsoPropertyType.GARAGE_DOOR)
            &&object.getSquare().isSeen(player);
    }
    private static boolean layer(IsoObject object,int player){
        ObjectRenderLayer layer=object.getRenderInfo(player).layer;
        return layer==ObjectRenderLayer.MinusFloor||layer==ObjectRenderLayer.MinusFloorSE
            ||layer==ObjectRenderLayer.Translucent||layer==ObjectRenderLayer.TranslucentSE;
    }
    /** Called at the original calculation, before its native positive-alpha layer promotion. */
    public static float target(float original,IsoObject object){
        int player=IsoCamera.frameState.playerIndex;
        if(!active()||player<0||player>=4||original<0||!eligible(object,player)||!layer(object,player))return original;
        cell();float value=opacity(object,player);
        // Native hasSeenDoor may reject a logical door with an unusual current sprite. Repair only
        // an existing, currently visible partial leaf; zero/FOW/None outside that scope stays native.
        if(original==0&&(value>=1f||!object.getSquare().isCanSee(player)||!object.getSquare().getObjects().contains(object)))return original;
        DOORS.computeIfAbsent(object,key->new Entry()).target[player]=value;
        return value;
    }
    /** Native wall lighting may otherwise reset an opened leaf to .4 using its old doorway. */
    public static void wallAlpha(IsoObject object){
        int player=IsoCamera.frameState.playerIndex;
        if(!active()||player<0||player>=4||!eligible(object,player)||!layer(object,player))return;
        float value=object.getRenderInfo(player).targetAlpha;
        // None/FOW/native zero targets outside the partial-leaf repair remain native.
        if(value>0)object.setAlphaAndTarget(player,value);
    }
    /** Visits only already calculated doors; no cell/chunk/object discovery scan occurs here. */
    public static void refresh(int player){
        if(!active()||player<0||player>=4)return;cell();
        for(var iterator=DOORS.entrySet().iterator();iterator.hasNext();){
            var item=iterator.next();IsoObject object=item.getKey();IsoGridSquare square=object.getSquare();
            if(square==null||square.getCell()!=ownerCell||!square.getObjects().contains(object)){iterator.remove();continue;}
            Entry entry=item.getValue();if(Float.isNaN(entry.target[player]))continue;
            float value=eligible(object,player)?opacity(object,player):1.0f;
            if(value!=entry.target[player]){
                entry.target[player]=value;
                // Exactly the native obscuring-object transition flag; native rebuild recalculates both layers.
                square.invalidateRenderChunkLevel(FBORenderChunk.DIRTY_OBSCURING);invalidations++;
            }
        }
    }
    private static void cell(){
        IsoCell current=IsoWorld.instance.currentCell;
        if(ownerCell!=current){DOORS.clear();Access.QUERIES.clear();ownerCell=current;}
    }
    private static float opacity(IsoObject object,int player){return obscures(object,player)?open(object)?0.6f:0.4f:1.0f;}
    /** Uses only a current player square and the exact native fence/wall positional predicate. */
    static boolean obscures(IsoObject object,int player){
        IsoGridSquare own=object.getSquare(),playerSquare=IsoCamera.frameState.camCharacterSquare;
        if(playerSquare==null||playerSquare.getZ()!=own.getZ())return false;
        IsoDirections direction=edge(object);int x=own.x,y=own.y;
        if(direction==IsoDirections.E){x++;direction=IsoDirections.W;}
        else if(direction==IsoDirections.S){y++;direction=IsoDirections.N;}
        if(direction!=IsoDirections.N&&direction!=IsoDirections.W)return false;
        Query query=Access.QUERIES.computeIfAbsent(own.z,Access::query);
        query.wall.x1=x;query.wall.y1=y;query.wall.x2=x+(direction==IsoDirections.N?1:0);query.wall.y2=y+(direction==IsoDirections.W?1:0);
        try{return (boolean)Access.QUERY_HANDLE.invokeExact(query.wall,playerSquare,6);}
        catch(Throwable error){throw new IllegalStateException("Native door visibility predicate failed",error);}
    }
    private record Query(FBORenderCutaways.CutawayWall wall) { }
    private static final class Access {
        static final Map<Integer,Query> QUERIES=new HashMap<>();
        static final MethodHandle QUERY_HANDLE,LEVEL_CTOR;
        static final Field WALL_LEVEL;
        static {
            try{
                try(ZipFile game=new ZipFile(Compatibility.gameJar().toFile());InputStream in=game.getInputStream(game.getEntry(WALL+".class"))){
                    Map<String,String> pins=Compatibility.methods(in.readAllBytes());
                    if(!NATIVE_QUERY.equals(pins.get(QUERY))||!HORIZONTAL.equals(pins.get("isHorizontal()Z")))throw new IllegalStateException("Installed native door predicate changed");
                }
                Map<String,String> effective=Compatibility.methods(RuntimeState.payload.baseClassBytes(WALL));
                if(!RepairProfiles.matches(SOURCE_QUERY,effective.get(QUERY))||!HORIZONTAL.equals(effective.get("isHorizontal()Z")))throw new IllegalStateException("Prepared door predicate changed");
                Class<?> wall=FBORenderCutaways.CutawayWall.class,level=FBORenderCutaways.ChunkLevelData.class;
                for(String coordinate:List.of("x1","y1","x2","y2"))if(wall.getField(coordinate).getType()!=int.class)throw new NoSuchFieldException(coordinate);
                Field z=level.getField("level");if(z.getType()!=int.class||!Modifier.isFinal(z.getModifiers()))throw new NoSuchFieldException("level");
                WALL_LEVEL=wall.getDeclaredField("chunkLevelData");if(WALL_LEVEL.getType()!=level)throw new NoSuchFieldException("chunkLevelData");WALL_LEVEL.setAccessible(true);
                Method predicate=wall.getDeclaredMethod("shouldCutawayFence",IsoGridSquare.class,int.class);predicate.setAccessible(true);
                QUERY_HANDLE=MethodHandles.lookup().unreflect(predicate);
                Constructor<?> constructor=level.getDeclaredConstructor(int.class);constructor.setAccessible(true);LEVEL_CTOR=MethodHandles.lookup().unreflectConstructor(constructor);
            }catch(Exception error){throw new ExceptionInInitializerError(error);}
        }
        static Query query(int z){
            try{var wall=new FBORenderCutaways.CutawayWall();WALL_LEVEL.set(wall,LEVEL_CTOR.invoke(z));return new Query(wall);}
            catch(Throwable error){throw new IllegalStateException("Native door query creation failed",error);}
        }
    }
}
