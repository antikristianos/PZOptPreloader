package antikristianos.pzpeek42;

import java.lang.invoke.*;
import java.util.ArrayList;
import antikristianos.pzoptpreloader.StandingObjectCutaway;
import pzmod.peekaview.PeekAViewMod;
import zombie.characters.*;
import zombie.core.properties.IsoPropertyType;
import zombie.iso.*;
import zombie.iso.fboRenderChunk.FBORenderCell;
import zombie.popman.ObjectPool;

/** Extends native obscuring input; native alpha, grids, invalidation and fade recovery keep ownership. */
public final class PropCutaway {
    private static final ThreadLocal<Context> LOCAL=ThreadLocal.withInitial(Context::new);
    private static final class Context {final ArrayList<IsoGridSquare> left=new ArrayList<>(),right=new ArrayList<>();}
    private static final class Access {
        static final MethodHandle POOL,POTENTIAL;
        static{try{var lookup=MethodHandles.privateLookupIn(FBORenderCell.class,MethodHandles.lookup());
            POOL=lookup.findGetter(FBORenderCell.class,"locationPool",ObjectPool.class);
            POTENTIAL=lookup.findVirtual(FBORenderCell.class,"isPotentiallyObscuringObject",MethodType.methodType(boolean.class,IsoObject.class));
        }catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}}
    }
    private PropCutaway(){}
    public static void initialize(){if(Access.POOL==null||Access.POTENTIAL==null)throw new IllegalStateException("Native prop obscuring API unavailable");}
    private static boolean supported(IsoObject object){
        if(object==null||object.square==null||object.getSprite()==null||object instanceof zombie.iso.objects.IsoWorldInventoryObject)return false;
        if(StandingObjectCutaway.peekCandidate(object))return true;
        String name=object.getSprite().getProperties().get(IsoPropertyType.CUSTOM_NAME);
        return "Crate".equals(name)||"Single Stacked Hay".equals(name)||"Double Stacked Hay".equals(name);
    }
    @SuppressWarnings("unchecked")
    public static void extend(FBORenderCell renderer,int playerIndex,ArrayList<IsoGameCharacter.Location> locations){
        if(!Main.active||!PeekAViewMod.isActiveCutawayForCurrentRenderPlayer()||playerIndex<0||playerIndex>=IsoPlayer.players.length)return;
        IsoPlayer player=IsoPlayer.players[playerIndex];IsoCell cell=IsoWorld.instance.currentCell;
        IsoGridSquare anchor=player==null?null:player.getCurrentSquare();if(cell==null||anchor==null)return;
        Context context=LOCAL.get();context.left.clear();context.right.clear();
        try{
            // Call the real, PeekAView-patched native query: no duplicate range/cone/vehicle/aim policy.
            cell.GetSquaresAroundPlayerSquare(player,anchor,context.left,context.right);
            ObjectPool<IsoGameCharacter.Location> pool=(ObjectPool<IsoGameCharacter.Location>)Access.POOL.invokeExact(renderer);
            append(renderer,locations,context.left,anchor.z,pool);append(renderer,locations,context.right,anchor.z,pool);
        }catch(Throwable error){throw new IllegalStateException("PeekAView prop cutaway input failed",error);}
        finally{context.left.clear();context.right.clear();}
    }
    private static void append(FBORenderCell renderer,ArrayList<IsoGameCharacter.Location> locations,ArrayList<IsoGridSquare> squares,int level,ObjectPool<IsoGameCharacter.Location> pool)throws Throwable{
        for(int i=0;i<squares.size();i++){
            IsoGridSquare square=squares.get(i);if(square==null||square.z!=level)continue;boolean match=false;
            var objects=square.getObjects();for(int j=0;j<objects.size();j++){IsoObject object=objects.get(j);if(supported(object)&&(boolean)Access.POTENTIAL.invokeExact(renderer,object)){match=true;break;}}
            if(!match)continue;boolean present=false;for(int j=0;j<locations.size();j++)if(locations.get(j).equals(square.x,square.y,square.z)){present=true;break;}
            if(!present){IsoGameCharacter.Location location=pool.alloc();location.set(square.x,square.y,square.z);locations.add(location);}
        }
    }
}
