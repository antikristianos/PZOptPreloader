package antikristianos.pzpeek42;


import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;

/** Per invocation only: reuses actual square lookups across overlapping wall rays. */
public final class GridCache {
    private static final int MASK=4095;
    private static final ThreadLocal<Context> LOCAL=ThreadLocal.withInitial(Context::new);
    public static long hits,lookups;
    private static final class Context {
        IsoCell cell;
        int depth,touchedCount;
        final int[] touched=new int[MASK+1];
        final int[] x=new int[MASK+1],y=new int[MASK+1],z=new int[MASK+1];
        final IsoGridSquare[] squares=new IsoGridSquare[MASK+1];
    }
    public static void begin(IsoCell cell){
        if(!Main.active)return;
        Context context=LOCAL.get();
        if(context.depth++==0)context.cell=cell;
    }
    public static void end(){
        Context context=LOCAL.get();
        if(context.depth>0 && --context.depth==0){for(int i=0;i<context.touchedCount;i++)context.squares[context.touched[i]]=null;context.touchedCount=0;context.cell=null;}
    }
    public static IsoGridSquare get(IsoCell cell,int x,int y,int z){
        Context context=LOCAL.get();
        if(context.depth==0 || context.cell!=cell){if(Diagnostics.ENABLED)lookups++;return cell.getGridSquare(x,y,z);}
        int slot=(x*73856093 ^ y*19349663 ^ z*83492791)&MASK;
        for(int probe=0;probe<8;probe++,slot=(slot+1)&MASK){
            IsoGridSquare square=context.squares[slot];
            if(square==null)break;
            if(context.x[slot]==x && context.y[slot]==y && context.z[slot]==z){if(Diagnostics.ENABLED)hits++;return square;}
        }
        IsoGridSquare square=cell.getGridSquare(x,y,z);if(Diagnostics.ENABLED)lookups++;
        if(square!=null){
            if(context.squares[slot]==null)context.touched[context.touchedCount++]=slot;
            context.x[slot]=x;context.y[slot]=y;context.z[slot]=z;context.squares[slot]=square;
        }
        return square;
    }
}
