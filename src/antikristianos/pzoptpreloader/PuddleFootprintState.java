package antikristianos.pzoptpreloader;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.*;
import org.lwjgl.opengl.*;
import zombie.core.textures.Texture;
import zombie.iso.*;
import zombie.iso.SpriteDetails.IsoFlagType;

/** Supported shore coverage only; native positions stay pristine until a valid render-time mask exists. */
public final class PuddleFootprintState {
    private static final Field SQUARE=field(IsoPuddlesGeometry.class,"square");
    private static final ClassValue<Field[]> DATA=new ClassValue<>(){
        @Override protected Field[] computeValue(Class<?> type){return new Field[]{field(type,"data"),field(type,"numSquares")};}
    };
    private static final Map<Integer,int[]> UNIFORMS=new HashMap<>();
    private static final ArrayList<Scope> SCOPES=new ArrayList<>();
    private static int depth;
    private static final class Scope {int program,enabled;float prior;boolean bound;}
    private PuddleFootprintState() { }
    private static Field field(Class<?> type,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
    public static float owner(IsoGridSquare square){return 1+Math.floorMod(square.x,4096)+4096*Math.floorMod(square.y,4096);}
    public static boolean supported(IsoObject object){
        if(object==null||object.square==null||object.square.z!=0||object.getSprite()==null||!object.getSprite().solidfloor)return false;
        if(zombie.iso.fboRenderChunk.FBORenderLevels.getTextureScale(IsoCamera.frameState.zoom)!=1)return false;
        var sprite=object.getSprite();var water=object.square.getWater();
        if(water==null||!water.isbShore()||!sprite.getProperties().has(IsoFlagType.diamondFloor)||object.getOffsetX()!=64||object.getOffsetY()!=192||object.getRenderYOffset()!=0)return false;
        if(sprite.def!=null&&(sprite.def.scaleX!=1||sprite.def.scaleY!=1))return false;
        Texture texture=object.getCurrentFrameTex();
        return texture!=null&&texture.isReady()&&texture.getWidth()==126&&texture.getHeight()==64&&texture.getWidthOrig()==128&&texture.getHeightOrig()==256&&texture.getOffsetX()==0&&texture.getOffsetY()==192;
    }
    public static boolean enabled(){return IsoPuddles.getInstance().getShaderEnable()&&IsoPuddles.getInstance().shouldRenderPuddles();}
    /** Reserved metadata in the native binary none-direction field; shader restores exactly 0/1 on all paths. */
    public static void mark(Object data,IsoPuddlesGeometry geometry){
        try{
            IsoGridSquare square=(IsoGridSquare)SQUARE.get(geometry);if(square==null||!supported(square.getFloor()))return;
            Field[] fields=DATA.get(data.getClass());int at=(fields[1].getInt(data)-1)*32;float[] values=(float[])fields[0].get(data);
            if(at<0||at+32>values.length)return;
            float topX=values[at+4],topY=values[at+5];
            if(values[at+12]!=topX-64||values[at+13]!=topY+32||values[at+20]!=topX||values[at+21]!=topY+64||values[at+28]!=topX+64||values[at+29]!=topY+32)return;
            float centre=(values[at+15]+values[at+31])*.5f;if(!Float.isFinite(centre)||centre<0||centre>1)return;
            for(int v=0;v<4;v++){float none=values[at+v*8+3],z=values[at+v*8+7];if((none!=0&&none!=1)||!Float.isFinite(z)||z<0||z>1)return;}
            for(int v=0;v<4;v++)values[at+v*8+3]=16+8*values[at+v*8+3]+4*centre;
        }catch(IllegalAccessException e){throw new IllegalStateException("Native shore puddle metadata unavailable",e);}
    }
    public static void shaderReload(){UNIFORMS.clear();}
    /** CPU caches refresh native depth independently of geometry; encode the current plane at its bulk upload. */
    public static FloatBuffer pack(FloatBuffer destination,float[] values,int offset,int length){
        if((offset&31)==0&&(length&31)==0){
            for(int at=offset;at<offset+length;at+=32){
                float tag=values[at+3];if(!Float.isFinite(tag)||!((tag>=16&&tag<=20)||(tag>=24&&tag<=28)))continue;
                float centre=(values[at+15]+values[at+31])*.5f;
                boolean valid=Float.isFinite(centre)&&centre>=0&&centre<=1;
                for(int v=0;v<4;v++){int index=at+v*8+3;float none=values[index]>=24?1:0;values[index]=valid?16+8*none+4*centre:none;}
            }
        }
        return destination.put(values,offset,length);
    }
    public static void begin(){
        if(depth==SCOPES.size())SCOPES.add(new Scope());Scope scope=SCOPES.get(depth++);scope.bound=false;scope.enabled=-1;
        scope.program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(scope.program==0)return;
        int[] u=UNIFORMS.computeIfAbsent(scope.program,p->new int[]{GL20.glGetUniformLocation(p,"pzPuddleMaskEnabled"),GL20.glGetUniformLocation(p,"pzPuddleMask"),GL20.glGetUniformLocation(p,"pzPuddleMaskInvSize"),GL20.glGetUniformLocation(p,"pzPuddleMaskViewport")});
        if(u[0]<0)return;scope.enabled=u[0];scope.prior=GL20.glGetUniformf(scope.program,u[0]);
        scope.bound=ShoreReceiverState.puddleBegin();GL20.glUniform1f(u[0],scope.bound?1:0);
        if(scope.bound){GL20.glUniform1i(u[1],ShoreReceiverState.PUDDLE_UNIT);ShoreReceiverState.puddleUniforms(u[2],u[3]);}
    }
    public static void end(){
        if(depth==0)return;Scope scope=SCOPES.get(--depth);
        if(scope.program!=0&&scope.enabled>=0){int current=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(current!=scope.program)GL20.glUseProgram(scope.program);GL20.glUniform1f(scope.enabled,scope.prior);if(current!=scope.program)GL20.glUseProgram(current);}
        if(scope.bound)ShoreReceiverState.puddleEnd();scope.enabled=-1;
    }
}
