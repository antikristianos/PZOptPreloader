package antikristianos.pzoptpreloader;

import java.lang.invoke.*;
import java.util.*;
import org.lwjgl.opengl.*;
import zombie.core.SpriteRenderer;
import zombie.core.textures.*;
import zombie.iso.*;
import zombie.iso.fboRenderChunk.*;
import zombie.iso.objects.*;

/** Bounded WIP observation. Never writes object, visibility, cache, shader or GL state. */
public final class DoorRenderDiagnostics {
    private static final int MAX_EVENTS=256,MAX_DOORS=64,MAX_TEXTURES=64;
    private static final IdentityHashMap<IsoObject,Reading> TRACKED=new IdentityHashMap<>();
    private static final IdentityHashMap<Texture,Bake> PENDING=new IdentityHashMap<>(),COMMITTED=new IdentityHashMap<>();
    private static final IdentityHashMap<IsoObject,Map<String,String>> LAST=new IdentityHashMap<>();
    private static int events,epoch;private static long generation;private static boolean failed;
    private static Armed armed;
    private DoorRenderDiagnostics() { }
    private record Reading(IsoObject object,int x,int y,int z,int player,float px,float py,float pz,float zoom,boolean open,String sprite,int spriteId,float spriteFrame,String type,int flags,float target,String layer,boolean seen,boolean canSee,boolean cached,int frame,long stamp,int state) {
        String detail(){return "door="+x+","+y+","+z+" player="+player+":"+px+","+py+","+pz+" zoom="+zoom+" open="+open+" sprite="+sprite+" spriteId="+spriteId+" spriteFrame="+spriteFrame+" tileType="+type+" cutaway="+flags+" target="+target+" layer="+layer+" seen="+seen+" canSee="+canSee+" translucentCache="+cached;}
    }
    private static final class Bake {
        final Texture depth;final int index,min,top,wx,wy,epoch;final long generation;final float zoom;
        final IdentityHashMap<IsoObject,Float> drawn=new IdentityHashMap<>();
        final IdentityHashMap<IsoObject,Reading> entries=new IdentityHashMap<>();
        Bake(FBORenderChunk owner){depth=owner.depth;index=owner.index;min=owner.getMinLevel();top=owner.getTopLevel();wx=owner.chunk.wx;wy=owner.chunk.wy;epoch=DoorRenderDiagnostics.epoch;generation=++DoorRenderDiagnostics.generation;zoom=IsoCamera.frameState.zoom;}
        String detail(){return "cache="+wx+","+wy+"/"+min+":"+top+" index="+index+" texture="+Integer.toHexString(System.identityHashCode(depth))+" generation="+generation+" bakeZoom="+zoom;}
        String identity(){return wx+","+wy+":"+min+","+top+":"+index+":"+System.identityHashCode(depth);}
    }
    private record Armed(Bake bake,int program) { }
    private static boolean active(){return !failed&&events<MAX_EVENTS&&!RuntimeState.server&&RuntimeState.payload!=null&&Compatibility.currentGame();}
    private static boolean physical(){return WallReflectionSource.sourcePass();}
    private static boolean recorded(){try{return Recorder.CURRENT.invokeExact()!=null;}catch(Throwable unavailable){return true;}}
    private static final class Recorder {
        static final MethodHandle CURRENT=resolve();
        static MethodHandle resolve(){try{return MethodHandles.publicLookup().unreflect(Class.forName("pzopt.DrawRecorder").getMethod("currentRecorder")).asType(MethodType.methodType(Object.class));}catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}}
    }
    private static Reading read(IsoObject object){
        if(object==null||!(object instanceof IsoDoor||object instanceof IsoThumpable gate&&gate.isDoor())||object.square==null||object.getSprite()==null)return null;
        int player=IsoCamera.frameState.playerIndex;if(player<0||player>=4)return null;
        var frame=IsoCamera.frameState;IsoGridSquare square=object.square;
        float dx=square.x+.5f-frame.camCharacterX,dy=square.y+.5f-frame.camCharacterY;
        if(dx*dx+dy*dy>64||Math.abs(square.z-frame.camCharacterZ)>1)return null;
        var info=object.getRenderInfo(player);IsoChunk chunk=square.getChunk();
        boolean cached=chunk!=null&&chunk.getRenderLevels(player).getCachedSquares_TranslucentNonFloor(square.z).contains(square);
        boolean open=object instanceof IsoDoor door?door.IsOpen():((IsoThumpable)object).isOpen();
        var sprite=object.getSprite();var state=SpriteRenderer.instance.states.getPopulating();
        return new Reading(object,square.x,square.y,square.z,player,frame.camCharacterX,frame.camCharacterY,frame.camCharacterZ,frame.zoom,open,String.valueOf(sprite.getName()),sprite.id,sprite.def==null?0:sprite.def.frame,String.valueOf(sprite.getTileType()),square.getPlayerCutawayFlag(player,0L),info.targetAlpha,String.valueOf(info.layer),square.isSeen(player),square.isCanSee(player),cached,frame.frameCount,state.time,System.identityHashCode(state));
    }
    private static void emit(String phase,Reading reading,String extra){emit(phase,reading,extra,extra);}
    private static void emit(String phase,Reading reading,String extra,String change){
        String key=reading.detail()+" "+extra;
        String changedKey=reading.open+"|"+reading.sprite+"|"+reading.spriteId+"|"+reading.type+"|"+reading.flags+"|"+reading.target+"|"+reading.layer+"|"+reading.seen+"|"+reading.canSee+"|"+reading.cached+"|"+change;var phases=LAST.get(reading.object);
        if(phases==null){if(LAST.size()>=MAX_DOORS)return;phases=new HashMap<>();LAST.put(reading.object,phases);}
        if(changedKey.equals(phases.put(phase,changedKey))||events>=MAX_EVENTS)return;
        events++;System.out.println("[PZOptPreloader/door-diag] event="+events+" phase="+phase+" source="+RuntimeState.payload.build.getProperty("commit","unknown")+" decisionFrame="+reading.frame+" producerStamp="+reading.stamp+" "+key+(events==MAX_EVENTS?" limit=256; further diagnostic events disabled":""));
    }
    private static void unavailable(Throwable error){if(!failed){failed=true;System.err.println("[PZOptPreloader/door-diag] observation disabled: "+error);}}
    public static synchronized void decision(IsoObject object){if(!active()||physical())return;try{Reading r=read(object);if(r==null||!TRACKED.containsKey(object)&&TRACKED.size()>=MAX_DOORS)return;TRACKED.put(object,r);emit("native-decision",r,recorded()?"recorded=true":"recorded=false");}catch(Throwable error){unavailable(error);}}
    public static synchronized void begin(boolean dirty,IsoChunk chunk,int level){if(!active()||!dirty||physical())return;try{FBORenderChunk owner=FBORenderChunkManager.instance.renderChunk;if(owner!=null&&owner.chunk==chunk&&level==owner.getMinLevel()&&owner.depth!=null){if(PENDING.size()>=MAX_TEXTURES)PENDING.clear();PENDING.put(owner.depth,new Bake(owner));}}catch(Throwable error){unavailable(error);}}
    public static synchronized void drawn(IsoObject object){if(!active()||physical())return;try{Reading r=read(object);if(r==null)return;if(TRACKED.containsKey(object)||TRACKED.size()<MAX_DOORS)TRACKED.put(object,r);var info=object.getRenderInfo(r.player);boolean recorded=recorded();boolean caching=FBORenderChunkManager.instance.isCaching();
        if(caching&&!recorded){FBORenderChunk owner=FBORenderChunkManager.instance.renderChunk;Bake bake=owner==null?null:PENDING.get(owner.depth);if(bake!=null&&bake.drawn.size()<MAX_DOORS)bake.drawn.put(object,info.renderAlpha);}
        emit("native-wall-submit",r,"renderAlpha="+info.renderAlpha+" renderSize="+info.renderWidth+","+info.renderHeight+" caching="+caching+" recorded="+recorded,info.renderAlpha+"|"+caching+"|"+recorded);
    }catch(Throwable error){unavailable(error);}}
    public static synchronized void baked(boolean rendered,IsoChunk chunk,int level){if(!active()||!rendered||physical())return;try{FBORenderChunk owner=FBORenderChunkManager.instance.renderChunk;if(owner==null||owner.chunk!=chunk||level!=owner.getTopLevel()||owner.depth==null)return;Bake bake=PENDING.remove(owner.depth);if(bake==null)return;
        for(var entry:TRACKED.entrySet()){Reading r=read(entry.getKey());if(r!=null&&r.z>=bake.min&&r.z<=bake.top&&Math.abs(Math.floorDiv(r.x,8)-bake.wx)<=1&&Math.abs(Math.floorDiv(r.y,8)-bake.wy)<=1)bake.entries.put(entry.getKey(),r);}
        // Commit in the actual render stream, rather than publishing a later producer frame early.
        SpriteRenderer.instance.drawGeneric(new TextureDraw.GenericDrawer(){@Override public void render(){commit(bake);}});
    }catch(Throwable error){unavailable(error);}}
    private static synchronized void commit(Bake bake){if(!active()||bake.epoch!=epoch)return;if(COMMITTED.size()>=MAX_TEXTURES)COMMITTED.clear();COMMITTED.put(bake.depth,bake);for(Reading r:bake.entries.values())emit("cache-commit",r,bake.detail()+" bakedDrawAlpha="+bake.drawn.getOrDefault(r.object,Float.NaN)+" (NaN=not-observed; not proof of absence)",bake.identity()+"|"+bake.drawn.getOrDefault(r.object,Float.NaN));}
    public static synchronized void composite(TextureDraw draw){armed=null;if(!active()||draw==null||draw.tex1==null)return;try{Bake bake=COMMITTED.get(draw.tex1);if(bake!=null&&bake.epoch==epoch&&!bake.entries.isEmpty())armed=new Armed(bake,GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));}catch(Throwable error){unavailable(error);}}
    public static synchronized void gpuDraw(){if(!active()||armed==null)return;Armed current=armed;armed=null;try{if(current.program==0||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=current.program)return;
        var consumer=SpriteRenderer.instance.states.getRendering();
        for(var entry:current.bake.entries.entrySet()){Reading now=TRACKED.get(entry.getKey());if(now==null)continue;Reading old=entry.getValue();Float drawAlpha=current.bake.drawn.get(entry.getKey());boolean producerAhead=now.stamp>consumer.time||now.stamp==consumer.time&&now.state!=System.identityHashCode(consumer);boolean staleOpaque=!producerAhead&&drawAlpha!=null&&drawAlpha>=.999f&&now.target<1f;
            emit("gpu-cache-draw",now,current.bake.detail()+" bakedTarget="+old.target+" bakedLayer="+old.layer+" bakedDrawAlpha="+(drawAlpha==null?"unobserved":drawAlpha)+" staleOpaqueCandidate="+staleOpaque+" latestNativeTargetOpaque="+(now.target>=1f)+" producerAhead="+producerAhead+" consumerStamp="+consumer.time+" program="+current.program,current.bake.identity()+"|"+old.target+"|"+old.layer+"|"+drawAlpha+"|"+staleOpaque+"|"+producerAhead);
        }
    }catch(Throwable error){unavailable(error);}}
    public static synchronized void clear(){epoch++;events=0;failed=false;TRACKED.clear();PENDING.clear();LAST.clear();armed=null;SpriteRenderer.instance.drawGeneric(new TextureDraw.GenericDrawer(){@Override public void render(){synchronized(DoorRenderDiagnostics.class){COMMITTED.clear();armed=null;}}});}
}
