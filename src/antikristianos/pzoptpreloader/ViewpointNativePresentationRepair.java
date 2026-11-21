package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Source-20-only native gates; the cached VP mode policy leaves non-VP and perspective behavior native. */
public final class ViewpointNativePresentationRepair {
    public static final String WORLD="pzopt/WorldPass",DARKNESS="pzopt/Darkness";
    private static final String HELPER="antikristianos/pzoptpreloader/ViewpointNativePresentationCompatibility";
    private static final Map<String,String> PINS=Map.of(WORLD,"23d22fab836da75b4df5019718f9befc2f4adec5ab9677736e5411a2269e7f91",DARKNESS,"a7bd3b51e136fad6b87d1de0ecb7a6fb28a2ab1bea974a2c70dcbcb4b18b070e");
    private ViewpointNativePresentationRepair(){ }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!PINS.containsKey(name))return bytes;
        String method=name.equals(WORLD)?"take":"frame",descriptor=name.equals(WORLD)?"()Z":"()V";
        ClassReader reader=new ClassReader(bytes);
        if(!name.equals(reader.getClassName())||!PINS.get(name).equals(Compatibility.methods(bytes).get(method+descriptor)))throw new IOException("Unsupported native Viewpoint presentation boundary: "+name);
        int[] sites={0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                MethodVisitor out=super.visitMethod(a,n,d,s,e);if(!n.equals(method)||!d.equals(descriptor))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    boolean queuedGrade;
                    @Override public void visitInsn(int op){
                        if(name.equals(WORLD)&&op==Opcodes.IRETURN){super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"worldJobs","(Z)Z",false);sites[0]++;}
                        super.visitInsn(op);
                    }
                    @Override public void visitFieldInsn(int op,String owner,String field,String type){super.visitFieldInsn(op,owner,field,type);if(name.equals(DARKNESS)&&op==Opcodes.GETSTATIC&&owner.equals(DARKNESS)&&field.equals("queuedGrade"))queuedGrade=true;}
                    @Override public void visitJumpInsn(int op,Label target){
                        if(queuedGrade){if(op!=Opcodes.IF_ICMPNE)throw new IllegalStateException("Native darkness queue comparison changed");queuedGrade=false;super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"publishDarkness","(ZZ)Z",false);super.visitJumpInsn(Opcodes.IFNE,target);sites[0]++;}
                        else super.visitJumpInsn(op,target);
                    }
                };
            }
        },0);
        if(sites[0]!=1)throw new IOException("Native presentation instruction inventory changed: "+name);
        return writer.toByteArray();
    }
}
