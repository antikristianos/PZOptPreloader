package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;


/** Exact original Viewpoint replay gate and two verified once-only drawer boundaries. */
public final class ViewpointFrameRepair {
    public static final String BETWEEN="viewpoint/FrameBetween", UPKEEP="viewpoint/IsoUpkeep$Job", SHADOW="pzopt/ShadowAtlas$Flush", DARKNESS="pzopt/Darkness$FrameState";
    private static final String HELPER="antikristianos/pzoptpreloader/ViewpointFrameCompatibility";
    public static final Map<String,String> RESOURCES=Map.of(BETWEEN,"2dad57fbe53abd052a3f9450a1c35ec06150c832c336cb6de4048360d51f31f5",UPKEEP,"5af37a689abec39246ef7431cbb701bce709338a3f9f59b3c4a42a4208227936",SHADOW,"6c4a307c29274d30ec5f4db6f221bcb7efe8c647e32cb005d933b36483ebd4a2",DARKNESS,"65e6595b9371830c64ac7c3e77dae3e8f665c1a8c2f08a407214edc7499c78fc");
    public static final Map<String,Map<String,String>> METHODS=Map.of(BETWEEN,Map.of("check(Lzombie/core/sprite/SpriteRenderState;)Z","1b788e44e41a7bbbc54411e600f9c079c01caae18261c34ae4f614880947c5d4","play(Lzombie/core/sprite/SpriteRenderState;)V","4a93116f42f7707ec1ead7b28bac2e37af1dc009384d7dfe20551c0c019ff065"),UPKEEP,Map.of("render()V","266fd00fd78fe1e776aa6abe4cbe2801c91fdc8363ad8865500221d130d4218d"),SHADOW,Map.of("render()V","b441afdaf59c3b1940dbe7faf0f543b00f5efdf8eb49d09bb979b365994e7b3a"),DARKNESS,Map.of("render()V","b34bf6b1a28440b1c8d667922a3b6ca299b152d270662d68f5ea00bc14c64085"));
    // EarlyClassAdapter rewrites constant pools even when these helpers need no schema calls.
    private static final Map<String,String> EMITTED_RESOURCES=Map.of(SHADOW,"9f57d02ee4d3b678a4fcb267bb79ad2bdd984f635b0fc9249a8d21d97278c063",DARKNESS,"08751decc7767115d8340a7c1394364cc8f9ef908075d02e5b5a61b5024e7258");
    public static boolean resourceMatches(String name,byte[] bytes){
        String hash=Compatibility.hash(bytes);
        if(hash.equals(RESOURCES.get(name))||hash.equals(EMITTED_RESOURCES.get(name)))return true;
        return DARKNESS.equals(name)&&NativeSource.is(RuntimeState.payload)&&NativeSource.NEXT_COMMIT.equals(NativeSource.sourceCommit(RuntimeState.payload))
            &&(hash.equals("8200cb7f0df67f8d9dff6cfc04c47d3d24a4d5c8bcf87853384a6f7adf18f6e5")||hash.equals("e63dbb6fda2d0d02743f17861ce5a16e6125c8d6e220eb7c95581131fd69abe5"));
    }
    private ViewpointFrameRepair(){ }
    public static void verify(String name,byte[] bytes)throws IOException{
        if(!METHODS.containsKey(name)||!name.equals(new ClassReader(bytes).getClassName()))throw new IOException("Unreviewed Viewpoint frame class: "+name);
        var actual=Compatibility.methods(bytes);for(var pin:METHODS.get(name).entrySet())if(!pin.getValue().equals(actual.get(pin.getKey())))throw new IOException("Unsupported Viewpoint frame method: "+name+'.'+pin.getKey());
    }
    public static byte[] patch(String name,byte[] bytes)throws IOException{
        if(!METHODS.containsKey(name))return bytes;verify(name,bytes);if(name.equals(DARKNESS))return bytes;
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){@Override protected ClassLoader getClassLoader(){return ViewpointFrameRepair.class.getClassLoader();}};int[] sites={0,0,0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                MethodVisitor out=super.visitMethod(access,method,descriptor,signature,exceptions);
                if(name.equals(BETWEEN)&&method.equals("check")&&descriptor.equals("(Lzombie/core/sprite/SpriteRenderState;)Z"))return new MethodVisitor(Opcodes.ASM9,out){
                    boolean picture;
                    @Override public void visitTypeInsn(int opcode,String type){super.visitTypeInsn(opcode,type);if(opcode==Opcodes.INSTANCEOF&&type.equals("viewpoint/ViewSwitch$Picture"))picture=true;}
                    @Override public void visitJumpInsn(int opcode,Label label){super.visitJumpInsn(opcode,label);if(picture){if(opcode!=Opcodes.IFNE)throw new IllegalStateException("Viewpoint replay guard shape changed");picture=false;sites[0]++;super.visitVarInsn(Opcodes.ALOAD,4);super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"admitted","(Ljava/lang/Object;)Z",false);super.visitJumpInsn(Opcodes.IFNE,label);}}
                };
                if(name.equals(BETWEEN)&&method.equals("play")&&descriptor.equals("(Lzombie/core/sprite/SpriteRenderState;)V"))return new MethodVisitor(Opcodes.ASM9,out){
                    final Label start=new Label(),end=new Label(),handler=new Label();
                    // Verified original play(state) uses only local 0 and its caught exception in local 1.
                    @Override public void visitCode(){super.visitCode();super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"enterReplay","()I",false);super.visitVarInsn(Opcodes.ISTORE,2);super.visitLabel(start);sites[1]++;}
                    @Override public void visitInsn(int opcode){if(opcode==Opcodes.RETURN){super.visitVarInsn(Opcodes.ILOAD,2);super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"exitReplay","(I)V",false);}super.visitInsn(opcode);}
                    @Override public void visitMaxs(int stack,int locals){if(locals!=2)throw new IllegalStateException("Viewpoint play local layout changed");super.visitLabel(end);super.visitTryCatchBlock(start,end,handler,"java/lang/Throwable");super.visitLabel(handler);super.visitVarInsn(Opcodes.ASTORE,3);super.visitVarInsn(Opcodes.ILOAD,2);super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"exitReplay","(I)V",false);super.visitVarInsn(Opcodes.ALOAD,3);super.visitInsn(Opcodes.ATHROW);super.visitMaxs(stack,locals);}
                };
                if(!name.equals(BETWEEN)&&method.equals("render")&&descriptor.equals("()V"))return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitCode(){super.visitCode();sites[2]++;Label normal=new Label();super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"skipOnce","(Ljava/lang/Object;)Z",false);super.visitJumpInsn(Opcodes.IFEQ,normal);super.visitInsn(Opcodes.RETURN);super.visitLabel(normal);}};
                return out;
            }
        },ClassReader.EXPAND_FRAMES);
        if(name.equals(BETWEEN)?sites[0]!=1||sites[1]!=1||sites[2]!=0:sites[0]!=0||sites[1]!=0||sites[2]!=1)throw new IOException("Viewpoint replay instruction inventory changed: "+name);
        return writer.toByteArray();
    }
}
