package antikristianos.pzoptpreloader;

import java.io.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** One owner for reviewed equivalent replacements; both advice phases retain native values. */
public final class ViewpointHotpathRepair {
    public static final String TARGET="viewpoint/FixHooks",RESOURCE="7bb2087961ebd51c15896edbb0a10fe93875bf9789b35ab03009daeb98439907";
    public static final Map<String,String> RESOURCES=Map.of(TARGET,RESOURCE,
        "viewpoint/game/GameFixes","0038d26c7a9eac558ca7a6d0809e3e97876df394ca633312e7b3cfa102d19cc5",
        "viewpoint/game/HotPaths","dbc78de8624b59a9279c57988becaa9d25ddfb486dc3860e22c68bd49ce4886f",
        "viewpoint/game/HotPaths$1","2f2731ef33cb8dacfa9b3c54c3ee9d6e11f0517c9f803bdf74f74792df195a80");
    private static final String HELPER="antikristianos/pzoptpreloader/ViewpointHotpathCompatibility";
    private static final Map<String,Integer> BITS=Map.of("skipStatsGet",1,"statsGet",1,"skipEcsClass",2,"ecsClass",2,"skipMinimumLevel",4,"minimumLevel",4,"skipBoneReparented",8,"boneReparented",8,"skipHeadReach",16,"skipFishNoise",32);
    private ViewpointHotpathRepair(){ }
    public static void verifyResource(String name,byte[] code)throws IOException{if(!Compatibility.hash(code).equals(RESOURCES.get(name)))throw new IOException("Unreviewed Viewpoint hotpath resource: "+name);}
    public static byte[] patch(byte[] code)throws IOException{
        if(!RESOURCE.equals(Compatibility.hash(code)))try(InputStream in=ViewpointHotpathRepair.class.getClassLoader().getResourceAsStream(TARGET+".class")){
            if(in==null)throw new IOException("Missing original hotpath resource");byte[] original=in.readAllBytes();if(!RESOURCE.equals(Compatibility.hash(original))||!Compatibility.methods(original).equals(Compatibility.methods(code)))throw new IOException("Unreviewed live Viewpoint hotpath methods");
        }
        ClassReader reader=new ClassReader(code);if(!TARGET.equals(reader.getClassName()))throw new IOException("Hotpath target identity differs");ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] sites={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){MethodVisitor out=super.visitMethod(a,n,d,s,e);Integer bit=BITS.get(n);if(bit==null)return out;
            return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitCode(){super.visitCode();sites[0]++;Label original=new Label();super.visitFieldInsn(Opcodes.GETSTATIC,HELPER,"owners","I");super.visitIntInsn(Opcodes.BIPUSH,bit);super.visitInsn(Opcodes.IAND);super.visitJumpInsn(Opcodes.IFEQ,original);
                if(n.startsWith("skip")){super.visitInsn(Opcodes.ICONST_0);super.visitInsn(Opcodes.IRETURN);}
                else if(n.equals("statsGet")){super.visitVarInsn(Opcodes.FLOAD,2);super.visitInsn(Opcodes.FRETURN);}
                else if(n.equals("boneReparented")){super.visitVarInsn(Opcodes.ILOAD,2);super.visitInsn(Opcodes.IRETURN);}
                else {super.visitVarInsn(Opcodes.ALOAD,1);super.visitInsn(Opcodes.ARETURN);}
                super.visitLabel(original);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
            }};
        }},0);if(sites[0]!=10)throw new IOException("Hotpath instruction boundary differs");return writer.toByteArray();
    }
}
