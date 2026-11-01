package antikristianos.pzoptpreloader;

import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.opengl.*;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.textures.TextureDraw;
import zombie.iso.*;
import zombie.iso.fboRenderChunk.*;

/** Per-draw shore lighting; native square colours, fade alpha, water and ordinary floors stay authoritative. */
public final class ShoreFloorLighting {
    private static final int TERM_UNIT=17, OLD_UNIT=23, CLOUD_UNIT=16;
    private static final Map<Object,int[]> MASKS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final int[] EMPTY_MASK=new int[8];
    private static final ThreadLocal<Context> CURRENT=new ThreadLocal<>();
    private static final Map<Integer,Uniforms> PROGRAMS=new HashMap<>(); // render thread only
    private static final Map<Integer,Integer> KERNELS=new HashMap<>(); // render thread only
    private static boolean warned;
    private ShoreFloorLighting() { }
    public static boolean active(){return CURRENT.get()!=null;}
    public static void shaderReload(){PROGRAMS.clear();KERNELS.clear();}
    /** Native water draws pop texture attributes. Uniform caching cannot retain this sampler binding. */
    public static void waterCloudBind(){
        try{
            Access a=Access.get();
            if(!a.waterPatched.getBoolean(null)||a.cloudFailed.getBoolean(null)||!(Boolean)a.cloudEnabled.invoke(null))return;
            int cloud=a.fieldTex.getInt(null);if(cloud<=0)return;
            int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            try{bind(CLOUD_UNIT,cloud);}finally{GL13.glActiveTexture(active);}
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Water cloud binding API unavailable",error);}
    }
    public static void capture(Object job,IsoChunk chunk,FBORenderChunk renderChunk){
        int[] mask=EMPTY_MASK;
        if(chunk!=null&&renderChunk.getMinLevel()==0&&IsoWater.getInstance().getShaderEnable()){
            mask=new int[8];
            for(int y=-4;y<12;y++)for(int x=-4;x<12;x++){
                IsoGridSquare square=IsoWorld.instance.currentCell.getGridSquare(chunk.wx*8+x,chunk.wy*8+y,0);
                if(square==null)continue;
                IsoWaterGeometry water=square.getWater();
                if(water!=null&&water.isbShore()&&square.getFloor()!=null&&square.getFloor().getSprite()!=null&&square.getFloor().getSprite().solidfloor){
                    int bit=(y+4)*16+x+4;mask[bit>>5]|=1<<(bit&31);
                }
            }
        }
        MASKS.put(job,mask);
    }
    public static void uploadMask(Object job){
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int location=KERNELS.computeIfAbsent(program,p->GL20.glGetUniformLocation(p,"pzShoreMask"));
        if(location<0)throw new IllegalStateException("Shore receiver mask absent from active AO kernel");
        int[] mask=MASKS.get(job);GL30.glUniform1uiv(location,mask==null?EMPTY_MASK:mask);
    }
    public static void begin(IsoObject object){
        if(FBORenderChunkManager.instance.isCaching()||object==null||object.square==null||object.square.z!=0||object.square.chunk==null)return;
        if(object.getSprite()==null||!object.getSprite().solidfloor||!IsoWater.getInstance().getShaderEnable())return;
        IsoWaterGeometry water=object.square.getWater();if(water==null||!water.isbShore())return;
        try{
            Access a=Access.get();if(!(Boolean)a.enabled.invoke(null))return;
            int player=IsoCamera.frameState.playerIndex;float zoom=Core.getInstance().getZoom(player);
            // lightverts itself is only a cached read; refresh the native lighting before reading its four colours.
            object.square.lighting[player].bCouldSee();
            FBORenderChunk chunk=object.square.chunk.getRenderLevels(player).getFBOForLevel(0,zoom);
            if(chunk==null||chunk.depth==null||chunk.getMinLevel()!=0)return;
            CURRENT.set(new Context(chunk,player,zoom));
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Shore lighting API unavailable",error);}
    }
    public static void tileStarted(int program){
        Context context=CURRENT.get();if(context==null)return;
        SpriteRenderer.instance.drawGeneric(new Binding(context,program));context.started=true;
    }
    public static void end(){
        Context context=CURRENT.get();if(context==null)return;
        CURRENT.remove();if(context.started)SpriteRenderer.instance.drawGeneric(new Reset());
    }
    private static final class Context {
        final int depth,w,h,chunkX,chunkY,scale,top,screenW,screenH;
        final float zoom,offX,offY;
        boolean started;
        Context(FBORenderChunk chunk,int player,float zoom){
            depth=chunk.depth.getID();w=chunk.w;h=chunk.h;chunkX=chunk.chunk.wx*8;chunkY=chunk.chunk.wy*8;
            scale=FBORenderLevels.getTextureScale(zoom);
            top=FBORenderChunk.PIXELS_PER_LEVEL*(chunk.getTopLevel()-chunk.getMinLevel()+1)
                +FBORenderLevels.extraHeightForJumboTrees(chunk.getMinLevel(),chunk.getTopLevel());
            this.zoom=zoom;offX=IsoCamera.getOffX();offY=IsoCamera.getOffY();
            screenW=IsoCamera.getScreenWidth(player);screenH=IsoCamera.getScreenHeight(player);
        }
    }
    /** Resolve once. No reflection search, source discovery or world scan runs while drawing. */
    private static final class Access {
        static Access instance;
        final Object gl;
        final Map<?,?> entries,bare;
        final Field valid,owner,tex,fadeTex,hasShare,fieldTex,waterPatched,cloudFailed,cloudBindless,dir;
        final boolean cloudTermMips;
        final int cloudTermLod;
        final Map<?,?> cloudLocations,cloudApplied;
        final Method enabled,cloudEnabled,waterUniforms,remaining;
        private Access()throws ReflectiveOperationException {
            Class<?> ao=Class.forName("pzopt.ChunkAo"),entry=Class.forName("pzopt.ChunkAo$Entry"),cloud=Class.forName("pzopt.CloudShadow");
            gl=field(ao,"GL").get(null);Class<?> glClass=gl.getClass();
            entries=(Map<?,?>)field(glClass,"byColour").get(gl);bare=(Map<?,?>)field(glClass,"bareByColour").get(gl);
            valid=field(entry,"valid");owner=field(entry,"colorTex");tex=field(entry,"tex");fadeTex=field(entry,"fadeTex");
            hasShare=field(entry,"q");cloudBindless=field(cloud,"bindless");
            Class<?> config=Class.forName("pzopt.Config");cloudTermMips=field(config,"CLOUD_TERM_MIPS").getBoolean(null);
            cloudTermLod=Math.max(0,Math.min(2,field(config,"CLOUD_TERM_LOD").getInt(null)));
            fieldTex=field(cloud,"fieldTex");waterPatched=field(cloud,"waterPatched");cloudFailed=field(cloud,"failed");
            dir=field(Class.forName("pzopt.SunShadow"),"dir");
            cloudLocations=(Map<?,?>)field(cloud,"WATER_LOCATIONS").get(null);cloudApplied=(Map<?,?>)field(cloud,"WATER_APPLIED").get(null);
            enabled=method(ao,"enabled");cloudEnabled=method(cloud,"enabled");waterUniforms=method(cloud,"waterUniforms");
            remaining=method(glClass,"remaining",entry,long.class);
        }
        static synchronized Access get()throws ReflectiveOperationException {if(instance==null)instance=new Access();return instance;}
        static Field field(Class<?> type,String name)throws ReflectiveOperationException {Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
        static Method method(Class<?> type,String name,Class<?>...args)throws ReflectiveOperationException {Method method=type.getDeclaredMethod(name,args);method.setAccessible(true);return method;}
    }
    private static final class Uniforms {
        final int enabled,x,y,params,term,old,cloudRead,cloudP,cloudE;
        Uniforms(int program){
            try{Access a=Access.get();a.cloudLocations.remove(program);a.cloudApplied.remove(program);}
            catch(ReflectiveOperationException error){throw new IllegalStateException("Shore shader API unavailable",error);}
            enabled=GL20.glGetUniformLocation(program,"pzShoreEnabled");x=GL20.glGetUniformLocation(program,"pzShoreX");y=GL20.glGetUniformLocation(program,"pzShoreY");
            params=GL20.glGetUniformLocation(program,"pzShoreParams");term=GL20.glGetUniformLocation(program,"pzShoreTerm");old=GL20.glGetUniformLocation(program,"pzShoreOld");
            cloudRead=GL20.glGetUniformLocation(program,"pzShoreCloudRead");
            cloudP=GL20.glGetUniformLocation(program,"pzCloudP");cloudE=GL20.glGetUniformLocation(program,"pzCloudE");
        }
    }
    private static final class Bound {
        int program,active,term,old,cloud;
        Uniforms uniforms;
    }
    private static final ArrayDeque<Bound> BOUND=new ArrayDeque<>(); // render thread only
    private static final class Binding extends TextureDraw.GenericDrawer {
        final Context context;final int program;
        Binding(Context context,int program){this.context=context;this.program=program;}
        @Override public void render(){
            Uniforms u=PROGRAMS.computeIfAbsent(program,Uniforms::new);
            if(u.enabled<0)return;
            Bound state=new Bound();state.program=program;state.uniforms=u;state.active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            state.term=texture(TERM_UNIT);state.old=texture(OLD_UNIT);state.cloud=texture(CLOUD_UNIT);BOUND.push(state);
            try{
                Access a=Access.get();Object entry=a.entries.get(context.depth);int term=0,old=0;float fade=0,direct=0;boolean share=false;
                if(entry!=null&&a.valid.getBoolean(entry)&&a.owner.getInt(entry)==context.depth){
                    term=a.tex.getInt(entry);old=a.fadeTex.getInt(entry);
                    share=a.hasShare.getBoolean(entry);
                    if(old>0)fade=(Float)a.remaining.invoke(null,entry,System.nanoTime());
                }else if(Integer.valueOf(1).equals(a.bare.get(context.depth)))direct=((float[])a.dir.get(null))[3];
                bind(TERM_UNIT,term);bind(OLD_UNIT,old>0?old:term);
                GL20.glUniform1i(u.term,TERM_UNIT);GL20.glUniform1i(u.old,OLD_UNIT);
                GL20.glUniform4f(u.params,term>0?1:0,direct,fade,0);
                GL20.glUniform4f(u.cloudRead,share?1:0,a.cloudBindless.getBoolean(null)?1:0,a.cloudTermMips?a.cloudTermLod:0,a.cloudTermMips?1:-8);
                int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
                float dx=context.screenW*context.zoom/(viewport[2]*64f),dy=-context.screenH*context.zoom/(viewport[3]*32f);
                float cx=(context.offX-viewport[0]*context.screenW*context.zoom/viewport[2])/64f-context.chunkX+context.chunkY;
                float cy=(context.offY+(viewport[1]+viewport[3])*context.screenH*context.zoom/viewport[3])/32f-context.chunkX-context.chunkY;
                float texel=context.scale*64f;
                GL20.glUniform4f(u.x,dx*texel/context.w,0,0,(context.w*0.5f+cx*texel)/context.w);
                GL20.glUniform4f(u.y,0,dy*context.scale*32f/context.h,0,(cy*32f+context.top)*context.scale/context.h);
                if(a.waterPatched.getBoolean(null)&&!a.cloudFailed.getBoolean(null)&&(Boolean)a.cloudEnabled.invoke(null)){
                    a.waterUniforms.invoke(null);bind(CLOUD_UNIT,a.fieldTex.getInt(null));
                    GL20.glUniform4f(u.cloudE,1,0,0,0);
                }else GL20.glUniform4f(u.cloudP,0,0,0,0);
                GL20.glUniform1f(u.enabled,1);
            }catch(Throwable error){
                GL20.glUniform1f(u.enabled,0);
                if(!warned){warned=true;System.err.println("[PZOptPreloader] shore lighting unavailable; native draw retained: "+error);}
            }finally{GL13.glActiveTexture(state.active);}
        }
    }
    private static int texture(int unit){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}
    private static void bind(int unit,int texture){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);}
    private static final class Reset extends TextureDraw.GenericDrawer {
        @Override public void render(){
            while(!BOUND.isEmpty()){
                Bound state=BOUND.pop();int previous=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
                GL20.glUseProgram(state.program);GL20.glUniform1f(state.uniforms.enabled,0);GL20.glUseProgram(previous);
                bind(TERM_UNIT,state.term);bind(OLD_UNIT,state.old);bind(CLOUD_UNIT,state.cloud);GL13.glActiveTexture(state.active);
            }
        }
    }
}
