package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Reviewed owner boundaries; never rewrites either mod's preferences. */
public final class ViewpointCoreRepair {
    public static final String TABLE="viewpoint/game/TableReads",CAPS="viewpoint/game/FrameCaps";
    public static final Map<String,String> RESOURCES=Map.of(TABLE,"166eef71fb35bd7e8f49a95a40ecfee7740cb07cc69dc43e6e8b2886a63d6175",CAPS,"13f75d427e77b85a662e3a834e3896bccac59ef38f81baca420752f5f22fd022");
    private static final String HELPER="antikristianos/pzoptpreloader/ViewpointCoreCompatibility";
    private ViewpointCoreRepair(){ }
    public static void verify(String name,byte[] code)throws IOException{
        var methods=Compatibility.methods(code);boolean ok=new ClassReader(code).getClassName().equals(name);
        if(name.equals(TABLE))ok&="108660b25e7eae89e5774833c02872abd06912d12116fc96619e9df6a2eaa551".equals(methods.get("ours(Ljava/lang/Object;)Z"));
        else if(name.equals(CAPS))ok&="8d2d8eed619900416b43ba5bac2a50289c47372ef043b22f28c1e87aafe73b92".equals(methods.get("optionsLoaded()V"))&&"0ef727f905e3153f7871dca4ea9abf5bfa6b386f51f026dd20693c2e1e83ced5".equals(methods.get("optionsLoading()V"));
        else throw new IOException("Unknown core target");if(!ok)throw new IOException("Unreviewed Viewpoint core method: "+name);
    }
    public static byte[] patch(String name,byte[] code)throws IOException{
        if(!RESOURCES.containsKey(name))return code;verify(name,code);ClassReader reader=new ClassReader(code);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] sites={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){MethodVisitor out=super.visitMethod(a,n,d,s,e);
            if(name.equals(TABLE)&&n.equals("ours")&&d.equals("(Ljava/lang/Object;)Z"))return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitCode(){super.visitCode();sites[0]++;Label original=new Label(),nativeOwner=new Label();super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"active","()Z",false);super.visitJumpInsn(Opcodes.IFEQ,original);super.visitMethodInsn(Opcodes.INVOKESTATIC,"pzopt/Overrides","enabled","()Z",false);super.visitJumpInsn(Opcodes.IFEQ,original);super.visitFieldInsn(Opcodes.GETSTATIC,"pzopt/Config","LUA_INDEX_CACHE","Z");super.visitJumpInsn(Opcodes.IFNE,nativeOwner);super.visitFieldInsn(Opcodes.GETSTATIC,"pzopt/Config","UI_RETAINED","Z");super.visitJumpInsn(Opcodes.IFNE,nativeOwner);super.visitFieldInsn(Opcodes.GETSTATIC,"pzopt/UiDeps","on","Z");super.visitJumpInsn(Opcodes.IFEQ,original);super.visitLabel(nativeOwner);super.visitFrame(Opcodes.F_SAME,0,null,0,null);super.visitInsn(Opcodes.ICONST_0);super.visitInsn(Opcodes.IRETURN);super.visitLabel(original);super.visitFrame(Opcodes.F_SAME,0,null,0,null);}};
            if(name.equals(CAPS)&&n.equals("optionsLoaded")&&d.equals("()V"))return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitVarInsn(int op,int slot){super.visitVarInsn(op,slot);if(op==Opcodes.ISTORE&&slot==4){sites[0]++;super.visitFieldInsn(Opcodes.GETSTATIC,CAPS,"saved","I");super.visitVarInsn(Opcodes.ILOAD,4);super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"gameCap","(II)I",false);super.visitVarInsn(Opcodes.ISTORE,4);}}};return out;
        }},0);if(sites[0]!=1)throw new IOException("Core instruction boundary differs: "+name);return writer.toByteArray();
    }
}
