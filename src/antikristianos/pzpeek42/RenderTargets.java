package antikristianos.pzpeek42;

import java.util.WeakHashMap;
import pzmod.peekaview.PeekAViewMod;
import zombie.core.Core;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoTree;

/** Each target is checked at most every 100 ms; unchanged answers have no expiry. */
public final class RenderTargets {
    public static final long INTERVAL_NANOS=100_000_000L;
    private static final ThreadLocal<Context> LOCAL=ThreadLocal.withInitial(Context::new);
    public static long hits,misses,refreshes;
    private static final class Entry {
        int mask,x,y,geometryVersion,positionVersion;
        boolean initialized;
        float x0,x1,y0,y1;
        final long[] nextChecks=new long[11];
        final int[] generations=new int[11],spatial=new int[11],arguments=new int[10],values=new int[11];
        void geometry(IsoTree tree){
            boolean moved=!initialized || x!=tree.square.x || y!=tree.square.y;
            boolean changed=moved || x0!=PeekAViewMod.treeBoxX0 || x1!=PeekAViewMod.treeBoxX1
                || y0!=PeekAViewMod.treeBoxY0 || y1!=PeekAViewMod.treeBoxY1;
            if(moved)positionVersion++;
            if(changed)geometryVersion++;
            x=tree.square.x;y=tree.square.y;x0=PeekAViewMod.treeBoxX0;x1=PeekAViewMod.treeBoxX1;
            y0=PeekAViewMod.treeBoxY0;y1=PeekAViewMod.treeBoxY1;initialized=true;
        }
    }
    private static final class Player {
        final WeakHashMap<IsoTree,Entry> entries=new WeakHashMap<>();
        final float[] inputs=new float[12];
        final int[] generations={1,1,1,1,1,1};
        int scale,radius,flags;
        void check(){
            int nextFlags=(PeekAViewMod.camPlayerSnapshotValid?1:0)|(PeekAViewMod.roadValid?2:0);
            int dirty=scale!=2?31:0;
            if(((flags^nextFlags)&1)!=0)dirty|=55;
            boolean position=input(0,PeekAViewMod.camPlayerX);position|=input(1,PeekAViewMod.camPlayerY);
            if(position)dirty|=55;
            boolean facing=input(2,PeekAViewMod.camPlayerFwdX);facing|=input(3,PeekAViewMod.camPlayerFwdY);
            boolean road=((flags^nextFlags)&2)!=0;
            road|=input(4,PeekAViewMod.roadLen);road|=input(5,PeekAViewMod.roadTx);road|=input(6,PeekAViewMod.roadTy);
            road|=input(7,PeekAViewMod.roadNx);road|=input(8,PeekAViewMod.roadNy);road|=input(9,PeekAViewMod.roadHalfW);
            boolean aim=input(10,PeekAViewMod.aimPointX);aim|=input(11,PeekAViewMod.aimPointY);
            if(facing)dirty|=(1<<2)|(1<<5);if(road)dirty|=1<<2;if(aim)dirty|=1<<3;
            if(radius!=PeekAViewMod.treeFadeRevealRadius)dirty|=1;
            for(int kind=0;kind<6;kind++)if((dirty&(1<<kind))!=0 && ++generations[kind]==0){entries.clear();generations[kind]=1;}
            scale=2;radius=PeekAViewMod.treeFadeRevealRadius;flags=nextFlags;
        }
        boolean input(int index,float value){boolean changed=Float.floatToRawIntBits(inputs[index])!=Float.floatToRawIntBits(value);inputs[index]=value;return changed;}
    }
    private static final class Context {
        final Player[] players=new Player[4];
        int depth;long now;IsoTree tree;Entry entry;Player player;boolean checked;
        void check(){if(!checked){player.check();entry.geometry(tree);checked=true;}}
    }
    public static void begin(IsoTree tree){begin(tree,System.nanoTime());}
    static void begin(IsoTree tree,long now){
        Context context=LOCAL.get();if(context.depth++!=0)return;
        int index=IsoCamera.frameState.playerIndex;
        context.player=index>=0 && index<4?context.players[index]:null;
        if(context.player==null && index>=0 && index<4)context.player=context.players[index]=new Player();
        context.tree=tree;context.now=now;context.entry=null;context.checked=false;
    }
    public static void end(){Context context=LOCAL.get();if(context.depth>0 && --context.depth==0){context.tree=null;context.entry=null;context.player=null;}}
    public static void clear(){LOCAL.remove();}
    public static void geometry(IsoTree tree){
        Context context=LOCAL.get();if(context.depth!=1 || context.tree!=tree || tree.square==null || context.player==null)return;
        Entry entry=context.player.entries.get(tree);
        if(entry==null){entry=new Entry();context.player.entries.put(tree,entry);}
        context.entry=entry;
    }
    private static boolean reusable(Context context,int slot,int argument){
        Entry entry=context.entry;
        if((entry.mask&(1<<slot))!=0){
            if(context.now-entry.nextChecks[slot]<0)return true;
            context.check();
            int spatial=slot==10?entry.positionVersion:entry.geometryVersion;
            if(entry.generations[slot]==context.player.generations[slot==10?5:slot/2] && entry.spatial[slot]==spatial
                    && (slot==10 || entry.arguments[slot]==argument)){
                entry.nextChecks[slot]=context.now+INTERVAL_NANOS;return true;
            }
        }else context.check();
        return false;
    }
    private static void remember(Context context,int slot,int argument,int value){
        Entry entry=context.entry;context.check();
        entry.generations[slot]=context.player.generations[slot==10?5:slot/2];
        entry.spatial[slot]=slot==10?entry.positionVersion:entry.geometryVersion;
        if(slot!=10)entry.arguments[slot]=argument;
        entry.values[slot]=value;entry.mask|=1<<slot;entry.nextChecks[slot]=context.now+INTERVAL_NANOS;
        if(Diagnostics.ENABLED)refreshes++;
    }
    public static int lookup(int kind,IsoTree tree,boolean held,int argument){
        if(!Main.active)return 0;
        Context context=LOCAL.get();if(context.depth!=1 || context.tree!=tree || context.entry==null)return 0;
        int slot=kind*2+(held?1:0);
        if(reusable(context,slot,argument)){if(Diagnostics.ENABLED)hits++;return context.entry.values[slot]!=0?3:1;}
        if(Diagnostics.ENABLED)misses++;return 0;
    }
    public static void remember(int kind,IsoTree tree,boolean held,int argument,boolean value){
        if(!Main.active)return;
        Context context=LOCAL.get();if(context.depth!=1 || context.tree!=tree || context.entry==null)return;
        remember(context,kind*2+(held?1:0),argument,value?1:0);
    }
    public static long dot(IsoGridSquare square){
        if(!Main.active)return 0;
        Context context=LOCAL.get();if(context.depth!=1 || context.tree==null || context.tree.square!=square || context.entry==null)return 0;
        if(reusable(context,10,0)){if(Diagnostics.ENABLED)hits++;return 0x100000000L|(context.entry.values[10]&0xffffffffL);}
        if(Diagnostics.ENABLED)misses++;return 0;
    }
    public static void rememberDot(IsoGridSquare square,float value){
        if(!Main.active)return;
        Context context=LOCAL.get();if(context.depth!=1 || context.tree==null || context.tree.square!=square || context.entry==null)return;
        remember(context,10,0,Float.floatToRawIntBits(value));
    }
}
