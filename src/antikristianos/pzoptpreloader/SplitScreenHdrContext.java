package antikristianos.pzoptpreloader;

import zombie.characters.IsoPlayer;
import zombie.core.textures.TextureDraw;
import zombie.iso.IsoCell;
import zombie.iso.IsoWorld;

/** Primitive identity transport for explicitly approved HDR separation; no native resource access or reflection. */
public final class SplitScreenHdrContext {
    private static final class Context {int player,floor=Integer.MIN_VALUE;final int[] renderFloors={Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};}
    private static final ThreadLocal<Context> CURRENT=ThreadLocal.withInitial(Context::new);
    private static final IsoPlayer[] identities=new IsoPlayer[4];
    private static final IsoCell[] cells=new IsoCell[4];
    private SplitScreenHdrContext(){ }
    public static int player(){return CURRENT.get().player;}
    public static int floor(int p){IsoPlayer actor=p>=0&&p<4?IsoPlayer.players[p]:null;return actor==null?Integer.MIN_VALUE:(int)Math.floor(actor.getZ());}
    public static long enter(int p,int floor){Context c=CURRENT.get();long previous=((long)c.player<<32)|(c.floor&0xffffffffL);c.player=Math.max(0,Math.min(3,p));c.floor=floor;return previous;}
    public static void exit(long previous){Context c=CURRENT.get();c.player=(int)(previous>>>32);c.floor=(int)previous;}
    public static boolean matchesFloor(int floor){Context c=CURRENT.get();return c.floor!=Integer.MIN_VALUE&&c.floor==floor;}
    public static void noteFrame(TextureDraw.Type type,int p,int floor){if(p<0||p>=4)return;if(type==TextureDraw.Type.glDoStartFrame||type==TextureDraw.Type.glDoStartFrameNoZoom||type==TextureDraw.Type.glDoStartFrameText||type==TextureDraw.Type.glDoStartFrameFx||type==TextureDraw.Type.glDoStartFrameFlipY)CURRENT.get().renderFloors[p]=floor;}
    public static int renderFloor(int p){return p>=0&&p<4?CURRENT.get().renderFloors[p]:Integer.MIN_VALUE;}
    public static boolean changed(int p){IsoCell cell=IsoWorld.instance==null?null:IsoWorld.instance.currentCell;boolean changed=identities[p]!=IsoPlayer.players[p]||cells[p]!=cell;identities[p]=IsoPlayer.players[p];cells[p]=cell;return changed;}
    /** One distinct, immutable command per native player pass; invokes its bound native task exactly once. */
    public static final class PlayerPass extends TextureDraw.GenericDrawer {
        private final Runnable task;
        public PlayerPass(Runnable task){if(task==null)throw new IllegalArgumentException("Missing HDR player task");this.task=task;}
        @Override public void render(){if(!ViewpointFrameCompatibility.skipOnce(this))task.run();}
    }
}
