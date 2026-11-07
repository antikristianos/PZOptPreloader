package antikristianos.pzpeek42;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

/** Exact memoization of original TreeShade results, without quantization or replacing its math. */
public final class ShadeCache {
    private static final Class<?> CONFIG, SUN;
    private static final VarHandle AO,SUN_ON,SUN_TREES,SUN_STRENGTH,AO_STRENGTH,VEGETATION,WORLD,DIRECTION;
    static {
        try{
            CONFIG=Class.forName("pzopt.Config");SUN=Class.forName("pzopt.SunShadow");
            AO=field(CONFIG,"AO",boolean.class);SUN_ON=field(CONFIG,"SUN_SHADOWS",boolean.class);
            SUN_TREES=field(CONFIG,"SUN_SHADOW_TREES",boolean.class);
            SUN_STRENGTH=field(CONFIG,"SUN_SHADOW_CANOPY_PCT",int.class);
            AO_STRENGTH=field(CONFIG,"AO_TREE_CANOPY_PCT",int.class);
            VEGETATION=field(CONFIG,"AO_STRENGTH_VEGETATION_PCT",int.class);
            WORLD=field(SUN,"world",float[].class);DIRECTION=field(SUN,"dir",float[].class);
        }catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    private static VarHandle field(Class<?> type,String name,Class<?> value)throws Exception{
        return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).findStaticVarHandle(type,name,value);
    }
    private static final int MASK=2047;
    private static final ThreadLocal<Cache> LOCAL=ThreadLocal.withInitial(Cache::new);
    public static long hits,misses,environmentChanges;
    private static final class Cache {
        final int[] generation=new int[MASK+1],a=new int[MASK+1],b=new int[MASK+1],c=new int[MASK+1],d=new int[MASK+1];
        final float[] result=new float[MASK+1];
        final int[] rowArguments=new int[8];
        final float[] rowValues=new float[2];
        int epoch=1,slot,scope,point,rowMask,rowWrite;
        boolean ao,sun,trees,valid;
        int strength,aoStrength,vegetation,x,y,z,light;
        boolean environment(){
            float[] world=(float[])WORLD.get(),dir=(float[])DIRECTION.get();
            int nextStrength=(int)SUN_STRENGTH.get(),nextAoStrength=(int)AO_STRENGTH.get();
            boolean nextAo=(boolean)AO.get() && nextAoStrength>0;
            boolean nextSun=(boolean)SUN_ON.get() && (boolean)SUN_TREES.get() && dir[3]>0 && nextStrength>0;
            boolean nextTrees=nextSun;
            int nextVegetation=nextAo?(int)VEGETATION.get():0;
            if(!nextAo)nextAoStrength=0;if(!nextSun)nextStrength=0;
            int nx=nextSun?Float.floatToRawIntBits(world[0]):0,ny=nextSun?Float.floatToRawIntBits(world[1]):0;
            int nz=nextSun?Float.floatToRawIntBits(world[2]):0,nl=nextSun?Float.floatToRawIntBits(dir[3]):0;
            boolean same=valid && ao==nextAo && sun==nextSun && trees==nextTrees && strength==nextStrength
                && aoStrength==nextAoStrength && vegetation==nextVegetation && x==nx && y==ny && z==nz && light==nl;
            if(!same){
                if(++epoch==0){Arrays.fill(generation,0);epoch=1;}
                ao=nextAo;sun=nextSun;trees=nextTrees;strength=nextStrength;aoStrength=nextAoStrength;vegetation=nextVegetation;
                x=nx;y=ny;z=nz;light=nl;valid=true;if(Diagnostics.ENABLED)environmentChanges++;
            }
            return same;
        }
    }
    public static void initialize(){ }
    public static int begin(){Cache cache=LOCAL.get();if(cache.scope++==0){cache.environment();cache.point=0;cache.rowMask=0;}return cache.epoch;}
    public static void end(){Cache cache=LOCAL.get();if(cache.scope>0)cache.scope--;}
    public static void clear(){LOCAL.remove();}
    public static long lookup(float u,float h,float height,float radius){
        if(!Main.active)return 0;
        Cache cache=LOCAL.get();if(cache.scope==0)cache.environment();
        int a=Float.floatToRawIntBits(u),b=Float.floatToRawIntBits(h),c=Float.floatToRawIntBits(height),d=Float.floatToRawIntBits(radius);
        if(cache.scope>0){
            int phase=cache.point++&3;cache.rowWrite=phase>=2?phase-2:-1;
            if(phase<2 && (cache.rowMask&(1<<phase))!=0){
                int offset=phase*4;int[] row=cache.rowArguments;
                if(row[offset]==a && row[offset+1]==b && row[offset+2]==c && row[offset+3]==d){
                    if(Diagnostics.ENABLED)hits++;return 0x100000000L|(Float.floatToRawIntBits(cache.rowValues[phase])&0xffffffffL);
                }
            }
            if(Diagnostics.ENABLED)misses++;return 0;
        }
        int hash=a*31+b*17+c*7+d;
        hash^=hash>>>16;hash*=0x7feb352d;hash^=hash>>>15;hash*=0x846ca68b;hash^=hash>>>16;
        int slot=hash&MASK;
        for(int probe=0;probe<4;probe++,slot=(slot+1)&MASK){
            if(cache.generation[slot]!=cache.epoch)break;
            if(cache.a[slot]==a && cache.b[slot]==b && cache.c[slot]==c && cache.d[slot]==d){if(Diagnostics.ENABLED)hits++;return 0x100000000L|(Float.floatToRawIntBits(cache.result[slot])&0xffffffffL);}
        }
        cache.slot=slot;if(Diagnostics.ENABLED)misses++;return 0;
    }
    public static void remember(float u,float h,float height,float radius,float value){
        if(!Main.active)return;
        Cache cache=LOCAL.get();int slot=cache.slot;
        if(cache.scope>0){
            if(cache.rowWrite>=0){
                int index=cache.rowWrite,offset=index*4;int[] row=cache.rowArguments;
                row[offset]=Float.floatToRawIntBits(u);row[offset+1]=Float.floatToRawIntBits(h);
                row[offset+2]=Float.floatToRawIntBits(height);row[offset+3]=Float.floatToRawIntBits(radius);
                cache.rowValues[index]=value;cache.rowMask|=1<<index;
            }
            return;
        }
        cache.a[slot]=Float.floatToRawIntBits(u);cache.b[slot]=Float.floatToRawIntBits(h);
        cache.c[slot]=Float.floatToRawIntBits(height);cache.d[slot]=Float.floatToRawIntBits(radius);
        cache.result[slot]=value;cache.generation[slot]=cache.epoch;
    }
}
