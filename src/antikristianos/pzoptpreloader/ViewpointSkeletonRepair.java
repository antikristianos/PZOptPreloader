package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Original VP ownership gates and the reviewed native animation completion boundary. */
public final class ViewpointSkeletonRepair {
    public static final String PORT="viewpoint/sim/SkeletonPort",RATE="viewpoint/models/CharacterRate",WORLD="zombie/iso/IsoWorld";
    private static final String HELPER="antikristianos/pzoptpreloader/ViewpointSkeletonCompatibility";
    public static final Map<String,String> RESOURCES=Map.of(
        PORT,"dd501e5a593a3500c55ae51bd2a0e66f5ae8c5b7589c8a9225c674a25e504fff",
        RATE,"ea24e54c232a14ea54ca2a85d9cac467766b280fc4c9b28b4e7940b91ab0525c",
        "viewpoint/sim/SkeletonSlots","ea210e104edb985577251f911bb1aa1e61190baec97621140147e42e0ee915c2",
        "viewpoint/sim/SimPool","81f15e4f8a622578267f2b58238b92f89b61bac2ee1e86aba79d9f491b31879c",
        "viewpoint/sim/SkeletonApply","7e3919f08603f6607426f5dcbbf13f9d5d4d02c92689c941f20797efff3fe368");
    private static boolean gate(String owner,String method){return owner.equals(PORT)?Set.of("updating","updatingUnmodelled","updated","animated","read","modelsRead","rows","model").contains(method):owner.equals(RATE)&&Set.of("updating","updated").contains(method);}
    private ViewpointSkeletonRepair(){ }
    public static byte[] patch(String name,byte[] code)throws IOException{
        if(!name.equals(WORLD)&&!name.equals(PORT)&&!name.equals(RATE))return code;
        if(name.equals(WORLD)){if(!"06bc5f3d19a395d4e03d2866f6053c30cd9b8ce6cb6e067445cb9ac637ef38b4".equals(Compatibility.methods(code).get("FinishAnimation()V")))throw new IOException("Unreviewed native skeleton completion boundary");}
        else if(!RESOURCES.get(name).equals(Compatibility.hash(code))){
            // Retransformation may reorder the constant pool without changing any method.
            try(var in=ViewpointSkeletonRepair.class.getClassLoader().getResourceAsStream(name+".class")){
                if(in==null)throw new IOException("Missing original skeleton resource");byte[] original=in.readAllBytes();
                if(!RESOURCES.get(name).equals(Compatibility.hash(original))||!Compatibility.methods(original).equals(Compatibility.methods(code)))throw new IOException("Unreviewed Viewpoint skeleton methods: "+name);
            }
        }
        ClassReader reader=new ClassReader(code);if(!name.equals(reader.getClassName()))throw new IOException("Skeleton target identity differs");ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] sites={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){MethodVisitor out=super.visitMethod(a,n,d,s,e);
            if(name.equals(WORLD)&&n.equals("FinishAnimation")&&d.equals("()V"))return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitInsn(int op){if(op==Opcodes.RETURN){sites[0]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"animationFinished","()V",false);}super.visitInsn(op);}};
            if(gate(name,n))return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitCode(){super.visitCode();sites[0]++;Label original=new Label();super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"nativeOwner","()Z",false);super.visitJumpInsn(Opcodes.IFEQ,original);if(d.endsWith("Z")){super.visitInsn(Opcodes.ICONST_0);super.visitInsn(Opcodes.IRETURN);}else super.visitInsn(Opcodes.RETURN);super.visitLabel(original);super.visitFrame(Opcodes.F_SAME,0,null,0,null);}};return out;
        }},0);if(sites[0]!=(name.equals(WORLD)?1:name.equals(PORT)?8:2))throw new IOException("Skeleton instruction boundary differs: "+name);return writer.toByteArray();
    }
}
