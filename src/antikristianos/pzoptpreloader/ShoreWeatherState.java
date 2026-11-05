package antikristianos.pzoptpreloader;

import java.lang.ref.*;
import java.nio.IntBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import zombie.core.SpriteRenderer;
import zombie.core.textures.TextureDraw;
import zombie.iso.*;
import zombie.iso.fboRenderChunk.*;

/** Independent weather-only cache, captured by the existing bake commands without replaying them. */
public final class ShoreWeatherState {
    public static final int UNIT=29, SLOT=7;
    private static final Map<Integer,Entry> ENTRIES=new HashMap<>(); // render thread
    private static final ThreadLocal<ArrayDeque<Packet>> QUEUED=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ArrayDeque<Saved> CAPTURE=new ArrayDeque<>(); // render thread
    private static boolean warned;
    private ShoreWeatherState() { }
    public static boolean supported(){return GL.getCapabilities().OpenGL40&&GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS)>SLOT&&GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS)>SLOT;}
    private static final class Entry {
        final WeakReference<FBORenderChunk> owner;final int depth,colour,w,h,wx,wy;int texture;
        Entry(FBORenderChunk c){owner=new WeakReference<>(c);depth=c.depth.getID();colour=c.tex.getID();w=c.w;h=c.h;wx=c.chunk.wx;wy=c.chunk.wy;}
        boolean matches(FBORenderChunk c){return owner.get()==c&&c.depth!=null&&c.tex!=null&&depth==c.depth.getID()&&colour==c.tex.getID()&&w==c.w&&h==c.h&&c.chunk!=null&&wx==c.chunk.wx&&wy==c.chunk.wy;}
    }
    private static Entry descriptor(IsoChunk chunk,int level){
        FBORenderChunk rc=FBORenderChunkManager.instance.renderChunk;
        if(level!=0||chunk==null||rc==null||rc.getMinLevel()!=0||rc.depth==null||rc.tex==null||rc.chunk==null||!IsoWater.getInstance().getShaderEnable()
            ||chunk.getRenderLevels(IsoCamera.frameState.playerIndex).getCachedSquares_WaterShore(0).isEmpty())return null;
        return new Entry(rc);
    }
    public static void bakeStart(IsoChunk chunk,int level){Entry e=descriptor(chunk,level);if(e!=null)SpriteRenderer.instance.drawGeneric(new Packet(e,true,false));}
    public static void begin(IsoChunk chunk,int level){Entry e=descriptor(chunk,level);Packet p=new Packet(e,false,true);QUEUED.get().push(p);if(e!=null)SpriteRenderer.instance.drawGeneric(p);}
    public static void end(){Packet p=QUEUED.get().pop();if(p.entry!=null)SpriteRenderer.instance.drawGeneric(new Finish());}
    public static void snow(FBORenderSnow snow,IsoChunk chunk,int level){begin(chunk,level);try{snow.RenderSnow(chunk,level);}finally{end();}}
    public static int texture(FBORenderChunk chunk){Entry e=chunk==null||chunk.depth==null?null:ENTRIES.get(chunk.depth.getID());return e!=null&&e.matches(chunk)?e.texture:0;}
    public static void coverBlend(){if(!CAPTURE.isEmpty()&&CAPTURE.peek().attached)GL40.glBlendFuncSeparatei(SLOT,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);}
    public static void reset(){SpriteRenderer.instance.drawGeneric(new TextureDraw.GenericDrawer(){@Override public void render(){for(Entry e:ENTRIES.values())GL11.glDeleteTextures(e.texture);ENTRIES.clear();}});}
    public static void free(FBORenderChunk chunk){if(chunk==null||chunk.depth==null)return;int depth=chunk.depth.getID();SpriteRenderer.instance.drawGeneric(new TextureDraw.GenericDrawer(){@Override public void render(){Entry e=ENTRIES.remove(depth);if(e!=null)GL11.glDeleteTextures(e.texture);}});}
    private static void sweep(){for(var it=ENTRIES.values().iterator();it.hasNext();){Entry e=it.next();FBORenderChunk c=e.owner.get();if(c==null||!e.matches(c)){GL11.glDeleteTextures(e.texture);it.remove();}}}
    private static final class Saved {
        final IntBuffer buffers=BufferUtils.createIntBuffer(8);int framebuffer,srcRgb,dstRgb,srcA,dstA,eqRgb,eqA;boolean blend,attached;
    }
    private static final class Packet extends TextureDraw.GenericDrawer {
        final Entry entry;final boolean clear,begin;
        Packet(Entry e,boolean clear,boolean begin){entry=e;this.clear=clear;this.begin=begin;}
        @Override public void render(){
            if(begin)CAPTURE.push(new Saved());
            try{
                if(!supported())return;
                FBORenderChunk owner=entry.owner.get();if(owner==null||!entry.matches(owner))return;
                sweep();Entry e=ENTRIES.get(entry.depth);
                if(e==null||!e.matches(owner)){if(e!=null)GL11.glDeleteTextures(e.texture);e=entry;ENTRIES.put(e.depth,e);}
                int framebuffer=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                if(framebuffer==0||GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+SLOT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)!=0)return;
                if(e.texture==0){int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);e.texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,e.texture);
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,e.w,e.h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);GL11.glBindTexture(GL11.GL_TEXTURE_2D,prior);GL13.glActiveTexture(active);}
                Saved s=begin?CAPTURE.peek():new Saved();s.framebuffer=framebuffer;
                for(int i=0;i<8;i++)s.buffers.put(i,GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i));
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+SLOT,GL11.GL_TEXTURE_2D,e.texture,0);s.attached=true;
                IntBuffer capture=BufferUtils.createIntBuffer(8);for(int i=0;i<8;i++)capture.put(i,i==SLOT?GL30.GL_COLOR_ATTACHMENT0+SLOT:s.buffers.get(i));GL20.glDrawBuffers(capture);
                if(clear){boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL30.glClearBufferfv(GL11.GL_COLOR,SLOT,new float[]{0,0,0,0});if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);restoreTarget(s);}
                if(begin){s.srcRgb=GL30.glGetIntegeri(GL14.GL_BLEND_SRC_RGB,SLOT);s.dstRgb=GL30.glGetIntegeri(GL14.GL_BLEND_DST_RGB,SLOT);s.srcA=GL30.glGetIntegeri(GL14.GL_BLEND_SRC_ALPHA,SLOT);s.dstA=GL30.glGetIntegeri(GL14.GL_BLEND_DST_ALPHA,SLOT);s.eqRgb=GL30.glGetIntegeri(GL20.GL_BLEND_EQUATION_RGB,SLOT);s.eqA=GL30.glGetIntegeri(GL20.GL_BLEND_EQUATION_ALPHA,SLOT);s.blend=GL30.glIsEnabledi(GL11.GL_BLEND,SLOT);GL30.glEnablei(GL11.GL_BLEND,SLOT);GL40.glBlendEquationSeparatei(SLOT,GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);GL40.glBlendFuncSeparatei(SLOT,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);}
            }catch(Throwable error){if(!warned){warned=true;System.err.println("[PZOptPreloader] shore weather capture unavailable; native weather retained: "+error);}}
        }
    }
    private static void restoreTarget(Saved s){if(!s.attached)return;GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+SLOT,GL11.GL_TEXTURE_2D,0,0);GL20.glDrawBuffers(s.buffers);s.attached=false;}
    private static final class Finish extends TextureDraw.GenericDrawer {
        @Override public void render(){Saved s=CAPTURE.pop();if(!s.attached)return;int current=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,s.framebuffer);restoreTarget(s);GL40.glBlendFuncSeparatei(SLOT,s.srcRgb,s.dstRgb,s.srcA,s.dstA);GL40.glBlendEquationSeparatei(SLOT,s.eqRgb,s.eqA);if(!s.blend)GL30.glDisablei(GL11.GL_BLEND,SLOT);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,current);}
    }
}
