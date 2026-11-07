package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Strict native metadata and render scope; no original game files or vertex formats change. */
public final class PuddleFootprintRepair {
    public static final String PUDDLES="zombie/iso/IsoPuddles",DATA=PUDDLES+"$RenderData";
    private static final String STATE="antikristianos/pzoptpreloader/PuddleFootprintState",BODY="pzoptPreloaderFootprintRenderSome";
    private static final String ADD="(ILzombie/iso/IsoPuddlesGeometry;Lzombie/tileDepth/TileSeamManager$Tiles;)V",DRAW="(IIZ)I";
    public static final Set<String> TARGETS=Set.of(PUDDLES,DATA);
    private PuddleFootprintRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException{
        if(!TARGETS.contains(name))return bytes;boolean data=name.equals(DATA);
        String method=data?"addSquare":"renderSome",descriptor=data?ADD:DRAW;
        String pin=data?"0cb62b94fa28a06340d7929503f6bd0c6ae3fd5b785859432fb00f08101b1c63":"f34fafe1df69c2d3c93a5d30809065d133ad96950b5ba50a7fe6b45724a61a9c";
        ClassReader reader=new ClassReader(bytes);
        if(!name.equals(reader.getClassName())||!pin.equals(Compatibility.methods(bytes).get(method+descriptor)))throw new IOException("Unsupported native shore footprint boundary: "+name);
        int[] hits={0,0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                if(n.equals(BODY))throw new IllegalStateException("Puddle footprint scope already present");
                boolean target=n.equals(method)&&d.equals(descriptor);
                if(target&&!data){wrapper(super.visitMethod(a,n,d,s,e));n=BODY;hits[0]++;}
                MethodVisitor base=super.visitMethod(a,n,d,s,e);if(!target)return base;
                if(!data)return new MethodVisitor(Opcodes.ASM9,base){
                    @Override public void visitMethodInsn(int op,String owner,String called,String desc,boolean itf){
                        if(op==Opcodes.INVOKEVIRTUAL&&owner.equals("java/nio/FloatBuffer")&&called.equals("put")&&desc.equals("([FII)Ljava/nio/FloatBuffer;")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"pack","(Ljava/nio/FloatBuffer;[FII)Ljava/nio/FloatBuffer;",false);hits[1]++;
                        }else super.visitMethodInsn(op,owner,called,desc,itf);
                    }
                };
                return new MethodVisitor(Opcodes.ASM9,base){
                    @Override public void visitInsn(int op){if(op==Opcodes.RETURN){super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,2);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"mark","(Ljava/lang/Object;Lzombie/iso/IsoPuddlesGeometry;)V",false);hits[0]++;}super.visitInsn(op);}
                };
            }
        },0);if(hits[0]!=1||(!data&&hits[1]!=1))throw new IOException("Unsupported native shore footprint shape: "+name);return writer.toByteArray();
    }
    private static void wrapper(MethodVisitor m){
        Label start=new Label(),end=new Label(),handler=new Label();m.visitCode();m.visitTryCatchBlock(start,end,handler,"java/lang/Throwable");m.visitLabel(start);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"begin","()V",false);m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ILOAD,1);m.visitVarInsn(Opcodes.ILOAD,2);m.visitVarInsn(Opcodes.ILOAD,3);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,PUDDLES,BODY,DRAW,false);m.visitVarInsn(Opcodes.ISTORE,4);m.visitLabel(end);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"end","()V",false);m.visitVarInsn(Opcodes.ILOAD,4);m.visitInsn(Opcodes.IRETURN);
        m.visitLabel(handler);m.visitFrame(Opcodes.F_SAME1,0,null,1,new Object[]{"java/lang/Throwable"});m.visitVarInsn(Opcodes.ASTORE,4);m.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"end","()V",false);m.visitVarInsn(Opcodes.ALOAD,4);m.visitInsn(Opcodes.ATHROW);m.visitMaxs(0,0);m.visitEnd();
    }
}
