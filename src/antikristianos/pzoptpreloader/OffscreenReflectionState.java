package antikristianos.pzoptpreloader;

import java.util.*;
import zombie.characters.*;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.*;
import zombie.core.skinnedmodel.*;
import zombie.core.skinnedmodel.model.*;
import zombie.core.textures.*;
import zombie.iso.*;
import zombie.iso.fboRenderChunk.FBORenderLevels;
import zombie.vehicles.*;

/** Existing render decisions collect bounded source candidates; no additional world traversal. */
public final class OffscreenReflectionState {
    private static final IdentityHashMap<IsoMovingObject,Integer> TRACKED=new IdentityHashMap<>();
    private static final ArrayList<IsoMovingObject> OBJECTS=new ArrayList<>();
    private static final Commands[][] COMMANDS=new Commands[3][4];
    private static volatile boolean failed;
    private static int warnings;
    public static long candidateUpdates,modelSnapshots,modelReleases;
    static{for(int state=0;state<3;state++)for(int player=0;player<4;player++)COMMANDS[state][player]=new Commands(player);}
    private OffscreenReflectionState(){ }
    public static boolean enabled(){return !failed&&OffscreenReflectionRepair.nativeReady()&&OffscreenReflectionAccess.get().enabled();}
    private static float worldMargin(){OffscreenReflectionAccess a=OffscreenReflectionAccess.get();return 384f*Math.max(0,a.integer(a.reachPct))/100f+128f;}
    /** Original scene-cull geometry is in unzoomed camera pixels. Other cull/quality/removal decisions stay native. */
    public static float cullLower(float value){return enabled()?value-worldMargin():value;}
    public static int cullWidth(int value){return enabled()?value+(int)Math.ceil(worldMargin()):value;}
    public static boolean chunkVisible(Object value,boolean original){
        if(original||!enabled()||!(value instanceof FBORenderLevels levels))return original;
        OffscreenReflectionAccess a=OffscreenReflectionAccess.get();int player=levels.getPlayerIndex();float zoom=Math.max(.01f,Core.getInstance().getZoom(player)),m=worldMargin()/zoom;
        float x=a.bound(value,"renderX"),y=a.bound(value,"renderY"),w=a.bound(value,"renderW"),h=a.bound(value,"renderH");
        return x+w>-m&&x<IsoCamera.getScreenWidth(player)+m&&y+h>-m&&y<IsoCamera.getScreenHeight(player);
    }
    private static boolean wanted(IsoMovingObject object,int player){
        if(object==null||object.getCurrentSquare()==null||object.getZ()<0||object.getZ()>=2)return false;
        float zoom=Math.max(.01f,Core.getInstance().getZoom(player));PlayerCamera camera=IsoCamera.cameras[player];
        float x=IsoUtils.XToScreen(object.getX(),object.getY(),object.getZ(),0)-camera.getOffX();
        float y=IsoUtils.YToScreen(object.getX(),object.getY(),object.getZ(),0)-camera.getOffY();
        float width=IsoCamera.getScreenWidth(player)*zoom,height=IsoCamera.getScreenHeight(player)*zoom,m=worldMargin();
        float half=object instanceof BaseVehicle?256f:96f,high=object instanceof BaseVehicle?384f:256f;
        return x+half>-m&&x-half<width+m&&y+64>-m&&y-high<height&&(x-half<0||x+half>width||y-high<0);
    }
    private static void track(IsoMovingObject object,boolean want){synchronized(TRACKED){
        Integer index=TRACKED.get(object);if(want&&index==null){TRACKED.put(object,OBJECTS.size());OBJECTS.add(object);candidateUpdates++;}
        else if(!want&&index!=null){int last=OBJECTS.size()-1;IsoMovingObject tail=OBJECTS.remove(last);TRACKED.remove(object);if(index!=last){OBJECTS.set(index,tail);TRACKED.put(tail,index);}candidateUpdates++;}
    }}
    public static void trackModel(Object value){
        if(!(value instanceof IsoMovingObject object)||!enabled())return;
        boolean want=false;for(int player=0;player<IsoPlayer.numPlayers&&player<4;player++)if(IsoPlayer.players[player]!=null&&wanted(object,player)){want=true;break;}track(object,want);
    }
    public static boolean movingVisible(Object value,boolean original){
        if(!(value instanceof IsoMovingObject object)||!enabled())return original;
        int player=IsoCamera.frameState.playerIndex;boolean want=wanted(object,player);if(want)track(object,true);else trackModel(object);return original||want;
    }
    private static Commands commands(int player){
        int index=Math.floorMod(SpriteRenderer.instance.getMainStateIndex(),COMMANDS.length);Commands c=COMMANDS[index][Math.floorMod(player,4)];
        long frame=SpriteRenderer.instance.states.getPopulating().time;if(c.frame!=frame){c.frame=frame;c.queued=false;c.next=0;}return c;
    }
    public static void queueBegin(int player){if(!enabled())return;Commands c=commands(player);SpriteRenderer.instance.drawGeneric(c.begin);}
    public static void queueSources(Object cell,int player){
        if(!enabled())return;Commands c=commands(player);if(c.queued)return;c.queued=true;
        OffscreenReflectionAccess a=OffscreenReflectionAccess.get();Object f=a.gameFrame();int puddles=0;
        if(f!=null&&a.awaitDrawData())synchronized(TRACKED){
            for(int index=0;index<OBJECTS.size()&&c.next<128;){
                IsoMovingObject object=OBJECTS.get(index);if(object.getCurrentSquare()==null){track(object,false);continue;}index++;
                if(!wanted(object,player)){
                    boolean retained=false;for(int other=0;other<IsoPlayer.numPlayers&&other<4;other++)if(IsoPlayer.players[other]!=null&&wanted(object,other)){retained=true;break;}
                    if(!retained){track(object,false);index--;}continue;
                }int near=a.near(f,object);if(near<2)continue;
                if(near==2){float dx=object.getX()-IsoCamera.frameState.camCharacterX,dy=object.getY()-IsoCamera.frameState.camCharacterY;if(dx*dx+dy*dy>196||puddles>=40)continue;puddles++;}
                IsoGameCharacter character=object instanceof IsoGameCharacter chr?chr:null;
                ModelManager.ModelSlot slot=character!=null?(character.legsSprite==null?null:character.legsSprite.modelSlot):(object.getSprite()==null?null:object.getSprite().modelSlot);
                if(slot==null||!slot.active||slot.remove){track(object,false);index--;continue;}
                if(slot.model==null||slot.model.model==null||!slot.model.model.isReady())continue;
                ActorDraw draw=c.actor();ModelSlotRenderData data=ModelSlotRenderData.alloc();ModelCameraRenderData camera=null;slot.renderRefCount++;
                try{
                    data.modelSlot=slot;a.resetSource(data);data.initModel(slot);data.init(slot);data.renderToTexture=false;
                    camera=ModelCameraRenderData.s_pool.alloc();camera.init(character!=null?CharacterModelCamera.instance:VehicleModelCamera.instance,slot);
                    draw.data=data;draw.camera=camera;SpriteRenderer.instance.drawGeneric(draw);modelSnapshots++;
                }catch(Throwable error){
                    if(camera!=null)camera.postRender();try{data.postRender();modelReleases++;}catch(Throwable suppressed){error.addSuppressed(suppressed);}warn(error);
                }
            }
        }
        SpriteRenderer.instance.drawGeneric(c.finish);
    }
    public static void chunkDraw(TextureDraw draw){OffscreenReflectionGpu.chunkDraw(draw);}
    public static void surfaceUniforms(){OffscreenReflectionGpu.surfaceUniforms();}
    public static void cameraBegin(){OffscreenReflectionGpu.cameraBegin();}
    public static void shadersChanged(){OffscreenReflectionGpu.shadersChanged();}
    public static void programBound(int program){OffscreenReflectionGpu.programBound(program);}
    public static void compositeElements(int mode,int start,int end,int count,int type,long offset){OffscreenReflectionGpu.compositeElements(mode,start,end,count,type,offset);}
    public static void clear(){synchronized(TRACKED){TRACKED.clear();OBJECTS.clear();}SpriteRenderer.instance.drawGeneric(new TextureDraw.GenericDrawer(){@Override public void render(){OffscreenReflectionGpu.clear();}});}
    static void fail(Throwable error){failed=true;warn(error);}
    static void warn(Throwable error){if(warnings++<5)System.err.println("[PZOptPreloader] Offscreen reflection source: "+error);}
    private static final class Commands{
        final ArrayList<ActorDraw> actors=new ArrayList<>();long frame=Long.MIN_VALUE;boolean queued;int next;final TextureDraw.GenericDrawer begin,finish;
        Commands(int player){begin=new TextureDraw.GenericDrawer(){@Override public void render(){OffscreenReflectionGpu.begin(player);}};finish=new TextureDraw.GenericDrawer(){@Override public void render(){OffscreenReflectionGpu.finish();}};}
        ActorDraw actor(){if(next==actors.size())actors.add(new ActorDraw());return actors.get(next++);}
    }
    private static final class ActorDraw extends TextureDraw.GenericDrawer {
        ModelSlotRenderData data;ModelCameraRenderData camera;
        @Override public void render(){if(data!=null&&camera!=null)OffscreenReflectionGpu.renderActor(data,camera);}
        @Override public void postRender(){try{if(data!=null){data.postRender();modelReleases++;}}finally{data=null;if(camera!=null)camera.postRender();camera=null;}}
    }
}