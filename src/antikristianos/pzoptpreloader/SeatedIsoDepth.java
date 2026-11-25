package antikristianos.pzoptpreloader;

import java.util.Arrays;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoMovingObject;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.BaseVehicle;

/** Retain an observed DAMN depth offset in ISO snapshots without changing actor or seat coordinates. */
public final class SeatedIsoDepth {
    private static KahluaTable environment,damn,sorting,openTop,glass,managed,applied;
    private static final State[] states=new State[4];
    private static final class State {
        IsoPlayer player;BaseVehicle vehicle;VehicleScript script;float floor,offset,armedOffset;
        boolean mini,armed;
    }
    private SeatedIsoDepth(){ }
    public static void reset(){environment=damn=sorting=openTop=glass=managed=applied=null;Arrays.fill(states,null);}
    private static KahluaTable table(Object object){return object instanceof KahluaTable value?value:null;}
    private static boolean truth(Object object){return object!=null&&!Boolean.FALSE.equals(object);}
    private static boolean near(float a,float b){return Math.abs(a-b)<.0001f;}
    private static boolean context(){
        KahluaTable env=LuaManager.env,d=env==null?null:table(env.rawget("DAMN"));
        KahluaTable depth=d==null?null:table(d.rawget("DepthSorting"));
        KahluaTable top=depth==null?null:table(depth.rawget("OpenTopOffsets"));
        KahluaTable closed=depth==null?null:table(depth.rawget("GlassOffsets"));
        KahluaTable active=depth==null?null:table(depth.rawget("AppliedFloorZ"));
        KahluaTable cars=d==null?null:table(d.rawget("vehiclesManaged"));
        if(env!=environment||d!=damn||depth!=sorting||top!=openTop||closed!=glass||cars!=managed||active!=applied){
            reset();environment=env;damn=d;sorting=depth;openTop=top;glass=closed;managed=cars;applied=active;
        }
        return sorting!=null&&applied!=null;
    }
    private static State state(int index,IsoPlayer player,BaseVehicle vehicle,VehicleScript script,float floor){
        State state=states[index];
        if(state!=null&&state.player==player&&state.vehicle==vehicle&&state.script==script&&state.floor==floor)return state;
        state=new State();state.player=player;state.vehicle=vehicle;state.script=script;state.floor=floor;state.offset=Float.NaN;
        String name=script.getFullName();Object top=openTop==null?null:openTop.rawget(name);
        if(truth(top)){
            if(top instanceof Number n)state.offset=n.floatValue();
        }else if(managed!=null&&truth(managed.rawget(name))&&glass!=null){
            Object amount=glass.rawget(name);
            state.offset=amount instanceof Number n?n.floatValue():amount==null?.15f:Float.NaN;
            state.mini="Base.69miniMrB".equals(name);
        }
        states[index]=state;return state;
    }
    public static float sortingZ(IsoMovingObject object){
        float actual=object.getZ();
        if(!(object instanceof IsoPlayer player)||!Float.isFinite(actual))return actual;
        int index=-1;for(int i=0;i<IsoPlayer.players.length&&i<states.length;i++)if(IsoPlayer.players[i]==player){index=i;break;}
        if(index<0)return actual;
        BaseVehicle vehicle=player.getVehicle();
        if(vehicle==null){states[index]=null;return actual;}
        if(zombie.GameWindow.gameThread!=null&&Thread.currentThread()!=zombie.GameWindow.gameThread)return actual;
        VehicleScript script=vehicle.getScript();float floor=(float)Math.floor(vehicle.getZ());
        if(script==null||!Float.isFinite(floor)||!context())return actual;
        State state=state(index,player,vehicle,script,floor);
        float offset=state.mini&&vehicle.getSeat(player)==4?.55f:state.offset;
        if(!Float.isFinite(offset)||offset<=0||offset>=1)return actual;
        if(state.armed&&state.armedOffset!=offset)state.armed=false;
        float expected=floor+offset;Object saved=applied.rawget(player);
        if(saved!=null){
            if(!(saved instanceof Number number)||number.floatValue()!=floor)return actual;
            if(near(actual,expected)){state.armed=true;state.armedOffset=offset;}
        }
        return state.armed&&(near(actual,floor)||near(actual,expected))?expected:actual;
    }
}
