package antikristianos.pzpeek42;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import zombie.core.opengl.VBORenderer;
import zombie.core.textures.Texture;
import zombie.iso.fboRenderChunk.FBORenderTrees;

/** Stores original local vertices, never a framebuffer, tree, texture or camera position. */
public final class DrawGeometry {
    private static final int MASK=1023,KEY_SIZE=18,VERTICES=24*9;
    private static final VarHandle RED,GREEN,BLUE,ALPHA;
    private static final MethodHandle DEPTH;
    public static long hits,misses,depthHits,bypasses,recordedVertices;
    static{
        try{
            Class<?> tree=Class.forName("zombie.iso.fboRenderChunk.FBORenderTrees$Tree");
            var lookup=MethodHandles.privateLookupIn(tree,MethodHandles.lookup());
            RED=lookup.findVarHandle(tree,"r",float.class);GREEN=lookup.findVarHandle(tree,"g",float.class);
            BLUE=lookup.findVarHandle(tree,"b",float.class);ALPHA=lookup.findVarHandle(tree,"a",float.class);
            var method=FBORenderTrees.class.getDeclaredMethod("calculateDepth",tree,boolean.class,boolean.class,boolean.class);
            method.setAccessible(true);DEPTH=MethodHandles.lookup().unreflect(method);
        }catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    private static final class Entry {
        final int[] key=new int[KEY_SIZE];
        float[] vertices;
        int environment,hash;boolean valid;
    }
    private static final class Context {
        final Entry[] entries=new Entry[MASK+1];
        final int[] key=new int[KEY_SIZE];
        final float[] depths=new float[8];
        int nesting,written,drawNesting,depthMask;
        Entry recording;
        Object draw;FBORenderTrees batch;
    }
    private static final ThreadLocal<Context> LOCAL=ThreadLocal.withInitial(Context::new);
    public static void initialize(){ }
    public static void clear(){LOCAL.remove();ShadeCache.clear();}
    private static int bits(float value){return Float.floatToRawIntBits(value);}
    /** 0 = inactive, 1 = record repeated input, 2 = replay, 3 = nested, 4 = first-use fallback. */
    public static int begin(VBORenderer vbor,Object tree,Texture texture,
            float xTL,float yTL,float xTR,float yTR,float xBR,float yBR,float xBL,float yBL,
            float top,float bottom,int unitsX,int unitsY){
        if(!Main.active)return 0;
        Context context=LOCAL.get();
        if(context.nesting++!=0)return 3;
        try{
        int environment=ShadeCache.begin();
        int[] key=context.key;
        key[0]=bits(xTL);key[1]=bits(yTL);key[2]=bits(xTR);key[3]=bits(yTR);
        key[4]=bits(xBR);key[5]=bits(yBR);key[6]=bits(xBL);key[7]=bits(yBL);
        key[8]=bits(top);key[9]=bits(bottom);key[10]=bits(texture.getXStart());key[11]=bits(texture.getXEnd());
        key[12]=bits((float)RED.get(tree));key[13]=bits((float)GREEN.get(tree));
        key[14]=bits((float)BLUE.get(tree));key[15]=bits((float)ALPHA.get(tree));key[16]=unitsX;key[17]=unitsY;
        int hash=0x811c9dc5;for(int value:key)hash=(hash^value)*0x01000193;
        hash^=hash>>>16;hash*=0x7feb352d;hash^=hash>>>15;
        int slot=hash&MASK;
        Entry found=null;
        for(int i=0;i<4;i++,slot=(slot+1)&MASK){
            Entry entry=context.entries[slot];
            if(entry==null){if(found==null){entry=new Entry();context.entries[slot]=entry;found=entry;}break;}
            if(entry.hash==hash && entry.environment==environment && Arrays.equals(entry.key,key)){
                if(!entry.valid){
                    if(entry.vertices==null)entry.vertices=new float[VERTICES];
                    context.recording=entry;context.written=0;if(Diagnostics.ENABLED)misses++;return 1;
                }
                if(Diagnostics.ENABLED)hits++;
                for(int offset=0;offset<VERTICES;){
                    vbor.reserve(4);
                    for(int vertex=0;vertex<4;vertex++,offset+=9){float[] v=entry.vertices;
                        vbor.addElement(v[offset],v[offset+1],v[offset+2],v[offset+3],v[offset+4],v[offset+5],v[offset+6],v[offset+7],v[offset+8]);
                    }
                }
                return 2;
            }
            if(found==null && (!entry.valid || entry.environment!=environment))found=entry;
        }
        if(found==null)found=context.entries[(hash&MASK)];
        found.valid=false;found.environment=environment;found.hash=hash;System.arraycopy(key,0,found.key,0,KEY_SIZE);
        context.recording=null;context.written=0;if(Diagnostics.ENABLED){misses++;bypasses++;}return 4;
        }catch(RuntimeException|Error error){context.recording=null;context.nesting=0;ShadeCache.end();throw error;}
    }
    public static void end(int mode,Throwable error){
        if(mode==0)return;
        Context context=LOCAL.get();
        if(--context.nesting!=0)return;
        if(context.recording!=null){context.recording.valid=error==null && context.written==VERTICES;context.recording=null;}
        ShadeCache.end();
    }
    public static void append(VBORenderer vbor,float x,float y,float z,float u,float v,float r,float g,float b,float a){
        Context context=LOCAL.get();Entry entry=context.recording;
        if(context.nesting==1 && entry!=null && context.written+9<=VERTICES){
            int i=context.written;float[] vertices=entry.vertices;
            vertices[i]=x;vertices[i+1]=y;vertices[i+2]=z;vertices[i+3]=u;vertices[i+4]=v;
            vertices[i+5]=r;vertices[i+6]=g;vertices[i+7]=b;vertices[i+8]=a;context.written=i+9;
            if(Diagnostics.ENABLED)recordedVertices++;
        }
        vbor.addElement(x,y,z,u,v,r,g,b,a);
    }
    public static boolean beginDraw(FBORenderTrees batch,Object draw){
        if(!Main.active)return false;
        Context context=LOCAL.get();
        if(context.drawNesting++==0){context.batch=batch;context.draw=draw;context.depthMask=0;}
        return true;
    }
    public static void endDraw(boolean opened){
        if(!opened)return;
        Context context=LOCAL.get();if(--context.drawNesting==0){context.batch=null;context.draw=null;context.depthMask=0;}
    }
    public static float depth(FBORenderTrees batch,Object tree,boolean second,boolean shader,boolean floor){
        Context context=LOCAL.get();int slot=(second?1:0)|(shader?2:0)|(floor?4:0),mask=1<<slot;
        boolean scoped=Main.active && context.drawNesting==1 && context.batch==batch && context.draw==tree;
        if(scoped && (context.depthMask&mask)!=0){if(Diagnostics.ENABLED)depthHits++;return context.depths[slot];}
        try{
            float value=(float)DEPTH.invoke(batch,tree,second,shader,floor);
            if(scoped){context.depths[slot]=value;context.depthMask|=mask;}
            return value;
        }catch(Throwable error){if(error instanceof RuntimeException runtime)throw runtime;if(error instanceof Error fatal)throw fatal;throw new IllegalStateException(error);}
    }
}
