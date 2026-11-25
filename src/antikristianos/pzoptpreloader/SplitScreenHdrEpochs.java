package antikristianos.pzoptpreloader;

import java.util.Arrays;
import zombie.characters.IsoPlayer;
import zombie.core.textures.TextureDraw;
import zombie.iso.IsoCell;
import zombie.iso.IsoWorld;

/** Primitive player/cell generations independent of local-light gates. The shared monitor publishes
 * producer identity changes to render and JNI workers; command epochs themselves are immutable. */
public final class SplitScreenHdrEpochs {
    private static final IsoPlayer[] actors=new IsoPlayer[4];
    private static final IsoCell[] cells=new IsoCell[4];
    private static final long[] epochs=new long[4];
    private static final class Scope {long expected=-1;long[] previous=new long[16];int depth;final long[] render={-1,-1,-1,-1};}
    private static final ThreadLocal<Scope> scopes=ThreadLocal.withInitial(Scope::new);
    public static synchronized long epoch(int p){if(p<0||p>=4)return Long.MIN_VALUE;IsoCell cell=IsoWorld.instance==null?null:IsoWorld.instance.currentCell;IsoPlayer actor=p<IsoPlayer.numPlayers?IsoPlayer.players[p]:null;if(actors[p]!=actor||cells[p]!=cell){actors[p]=actor;cells[p]=cell;epochs[p]++;}return epochs[p];}
    public static void observe(){for(int p=0;p<4;p++)epoch(p);}
    public static synchronized void reset(int p){epoch(p);epochs[p]++;}
    public static boolean valid(int p,long expected){return p>=0&&p<4&&epoch(p)==expected;}
    public static boolean accepts(long current){return current!=Long.MIN_VALUE&&(scopes.get().expected==-1||scopes.get().expected==current);}
    public static long sourceEpoch(int p){long expected=scopes.get().expected;return expected==-1?epoch(p):expected;}
    public static void enter(){Scope s=scopes.get();if(s.depth==s.previous.length)s.previous=Arrays.copyOf(s.previous,s.depth*2);s.previous[s.depth++]=s.expected;s.expected=-1;}
    public static void exit(){Scope s=scopes.get();s.expected=s.previous[--s.depth];}
    public static void expect(long epoch){scopes.get().expected=epoch;}
    public static void note(TextureDraw.Type type,int p,long epoch){if(p>=0&&p<4&&(type==TextureDraw.Type.glDoStartFrame||type==TextureDraw.Type.glDoStartFrameNoZoom||type==TextureDraw.Type.glDoStartFrameText||type==TextureDraw.Type.glDoStartFrameFx||type==TextureDraw.Type.glDoStartFrameFlipY))scopes.get().render[p]=epoch;}
    public static long render(int p){return p>=0&&p<4?scopes.get().render[p]:-1;}
}
