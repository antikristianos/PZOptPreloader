package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Replaces only the verified key-sort boundary of the separately supplied scheduler. */
public final class SchedulerProgressRepair {
    public static final String TARGET="pzopt/BakeScheduler";
    public static final String PLAN_PIN="ff48ad46515b5b9ea01bf201a2bef7dd1838edf5d59e37ad98f4d51a2fad484b";
    private static final String POLICY="antikristianos/pzoptpreloader/SchedulerProgressPolicy";
    private SchedulerProgressRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!TARGET.equals(name))return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!TARGET.equals(reader.getClassName())||!PLAN_PIN.equals(Compatibility.methods(bytes).get("plan(I)V")))
            throw new IOException("Unsupported scheduler progress plan boundary");
        Set<String> fields=new HashSet<>();int[] hits={0};
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public FieldVisitor visitField(int access,String field,String desc,String sig,Object value){
                if((access&Opcodes.ACC_STATIC)==0&&desc.equals("[I")&&Set.of("cls","dist","wait","level").contains(field))fields.add(field);
                return super.visitField(access,field,desc,sig,value);
            }
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] exceptions){
                MethodVisitor base=super.visitMethod(access,method,desc,sig,exceptions);
                if(!method.equals("plan")||!desc.equals("(I)V"))return base;
                return new MethodVisitor(Opcodes.ASM9,base){
                    @Override public void visitMethodInsn(int op,String owner,String called,String d,boolean itf){
                        if(op==Opcodes.INVOKESTATIC&&owner.equals("java/util/Arrays")&&called.equals("sort")&&d.equals("([JII)V")){
                            for(String field:new String[]{"cls","dist","wait","level"}){
                                super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETFIELD,TARGET,field,"[I");
                            }
                            super.visitFieldInsn(Opcodes.GETSTATIC,"pzopt/Config","BAKE_MAX_WAIT_ARRIVAL","I");
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,POLICY,"sort","([JII[I[I[I[II)V",false);hits[0]++;return;
                        }
                        super.visitMethodInsn(op,owner,called,d,itf);
                    }
                };
            }
        },0);
        if(hits[0]!=1||fields.size()!=4)throw new IOException("Unsupported scheduler sort/layout boundary: "+hits[0]+" "+fields);
        return writer.toByteArray();
    }
}
