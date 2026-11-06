package antikristianos.pzoptpreloader;

import java.util.*;
import org.lwjgl.opengl.*;
import zombie.core.SpriteRenderer;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.iso.*;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.fboRenderChunk.FBORenderChunkManager;
import zombie.iso.sprite.IsoSprite;

/** Per-floor queued uniforms, independent of optional lighting and the current puddle amount. */
public final class ShoreLandState {
    private static final ThreadLocal<Floor> FLOOR=ThreadLocal.withInitial(Floor::new);
    private static final Map<Integer,int[]> LOCATIONS=new HashMap<>();
    private static final Set<String> REPORTED=new HashSet<>();
    private static final Set<Integer> BOUND=new HashSet<>();
    private static final Pool[] POOLS={new Pool(),new Pool(),new Pool()};
    private static final class Floor {boolean active;IsoSprite sprite;float u,v,du,dv;int n;final int[] programs=new int[16];final boolean[] on=new boolean[16];}
    private static final class Pool {long generation=Long.MIN_VALUE;int cursor;final ArrayList<Marker> markers=new ArrayList<>();}
    private ShoreLandState() { }
    public static boolean active(){return FLOOR.get().active;}
    public static void shaderReload(){LOCATIONS.clear();BOUND.clear();}
    private static void report(String reason,IsoObject object,Texture texture){
        if(REPORTED.size()>=12||!REPORTED.add(reason+":"+(texture==null?"null":texture.getName())))return;
        System.out.println("[PZOptPreloader] shore land "+reason+" texture="+(texture==null?"null":texture.getName()+" crop="+texture.getWidth()+"x"+texture.getHeight()+" original="+texture.getWidthOrig()+"x"+texture.getHeightOrig()+" offset="+texture.getOffsetX()+","+texture.getOffsetY())+" objectOffset="+object.getOffsetX()+","+object.getOffsetY()+" zoom="+IsoCamera.frameState.zoom);
    }
    public static void begin(IsoObject object){
        Floor floor=FLOOR.get();floor.active=false;floor.sprite=null;floor.n=0;
        if(FBORenderChunkManager.instance.isCaching()||object==null||object.square==null||object.square.z!=0||object.getSprite()==null||!IsoWater.getInstance().getShaderEnable())return;
        var water=object.square.getWater();if(water==null||!water.isbShore())return;
        var sprite=object.getSprite();Texture texture=object.getCurrentFrameTex();
        if(texture==null||!texture.isReady()){report("waiting for texture",object,texture);return;}
        if(!sprite.solidfloor||!sprite.getProperties().has(IsoFlagType.diamondFloor)||object.getOffsetX()!=64||object.getOffsetY()!=192||object.getRenderYOffset()!=0
            ||texture.getWidth()!=126||texture.getHeight()!=64||texture.getWidthOrig()!=128||texture.getHeightOrig()!=256||texture.getOffsetX()!=0||texture.getOffsetY()!=192
            ||sprite.def!=null&&!((sprite.def.scaleX==1&&sprite.def.scaleY==1)||(sprite.def.scaleX==2&&sprite.def.scaleY==2))){report("native fallback (shape)",object,texture);return;}
        floor.u=(texture.getXStart()+texture.getXEnd())*.5f;floor.v=(texture.getYStart()+texture.getYEnd())*.5f;
        floor.du=1f/texture.getWidthHW();floor.dv=1f/texture.getHeightHW();floor.sprite=sprite;floor.active=true;report("armed",object,texture);
    }
    private static Marker marker(){
        var renderer=SpriteRenderer.instance;Pool pool=POOLS[Math.floorMod(renderer.getMainStateIndex(),POOLS.length)];long generation=renderer.states.getPopulating().time;
        if(pool.generation!=generation){pool.generation=generation;pool.cursor=0;}
        if(pool.cursor==pool.markers.size())pool.markers.add(new Marker());return pool.markers.get(pool.cursor++);
    }
    private static void queue(int program,boolean on,Floor floor){Marker marker=marker();marker.program=program;marker.on=on;marker.u=floor.u;marker.v=floor.v;marker.du=floor.du;marker.dv=floor.dv;SpriteRenderer.instance.drawGeneric(marker);}
    public static void tileStarted(int program,IsoSprite sprite){
        Floor floor=FLOOR.get();if(!floor.active||program<=0)return;boolean on=sprite==floor.sprite;
        for(int i=0;i<floor.n;i++)if(floor.programs[i]==program){if(floor.on[i]!=on){floor.on[i]=on;queue(program,on,floor);}return;}
        if(floor.n==floor.programs.length)throw new IllegalStateException("Unexpected shore land shader fanout");
        floor.on[floor.n]=on;floor.programs[floor.n++]=program;queue(program,on,floor);
    }
    public static void end(){Floor floor=FLOOR.get();if(!floor.active)return;floor.active=false;floor.sprite=null;for(int i=0;i<floor.n;i++)queue(floor.programs[i],false,floor);floor.n=0;}
    public static final class Marker extends TextureDraw.GenericDrawer {
        int program;boolean on;float u,v,du,dv;
        @Override public void render(){
            int prior=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(prior!=program)GL20.glUseProgram(program);
            int[] locations=LOCATIONS.computeIfAbsent(program,p->new int[]{GL20.glGetUniformLocation(p,"pzLandEnabled"),GL20.glGetUniformLocation(p,"pzLandUv")});
            if(locations[0]>=0&&locations[1]>=0){GL20.glUniform1f(locations[0],on?1:0);if(on)GL20.glUniform4f(locations[1],u,v,du,dv);}
            if(on&&BOUND.add(program))System.out.println("[PZOptPreloader] shore land shader="+program+" correction="+(locations[0]>=0&&locations[1]>=0&&GL20.glGetUniformf(program,locations[0])==1?"active":"unavailable"));
            if(prior!=program)GL20.glUseProgram(prior);
        }
    }
}
