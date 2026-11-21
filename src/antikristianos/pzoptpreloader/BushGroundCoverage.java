package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.lang.invoke.*;
import net.bytebuddy.jar.asm.*;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoTree;

/** Keep ground-bush soft coverage out of the opaque cached depth footprint. */
public final class BushGroundCoverage {
    private static final String CELL="zombie/iso/fboRenderChunk/FBORenderCell";
    private static final String METHOD="pzoptPerFrameTranslucentTile",DESC="(Lzombie/iso/IsoObject;)Z";
    private static final String PIN="9063c6fd7fa355b83e2a03a215ccb343f0ee04a5cb03c371dabb37f3b6249c3e";
    private BushGroundCoverage(){ }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!name.equals(CELL)||!NativeSource.currentRepair())return bytes;
        if(!PIN.equals(Compatibility.methods(bytes).get(METHOD+DESC)))throw new IOException("Unsupported ground-bush admission boundary");
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] hits={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String n,String d,String signature,String[] exceptions){
                MethodVisitor next=super.visitMethod(access,n,d,signature,exceptions);
                if(!n.equals(METHOD)||!d.equals(DESC))return next;
                return new MethodVisitor(Opcodes.ASM9,next){@Override public void visitInsn(int opcode){
                    if(opcode==Opcodes.IRETURN){super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/BushGroundCoverage","perFrame","(ZLzombie/iso/IsoObject;)Z",false);hits[0]++;}
                    super.visitInsn(opcode);
                }};
            }
        },0);
        if(hits[0]!=7)throw new IOException("Ground-bush admission return shape: "+hits[0]);
        return writer.toByteArray();
    }
    public static boolean perFrame(boolean original,IsoObject object){
        if(original)return true;
        if(object==null||object.square==null||object.square.z!=0||object instanceof IsoTree)return false;
        var sprite=object.getSprite();if(sprite==null||!sprite.isBush||sprite.solidfloor||!object.getDoRender())return false;
        Payload payload=RuntimeState.payload;
        if(RuntimeState.server||payload==null||!NativeSource.is(payload)||Native.ENABLED==null)return false;
        try{return (boolean)Native.ENABLED.invokeExact();}catch(Throwable unavailable){return false;}
    }
    private static final class Native {
        static final MethodHandle ENABLED=enabled();
        static MethodHandle enabled(){try{return MethodHandles.publicLookup().unreflect(Class.forName("pzopt.Overrides").getMethod("enabled"));}catch(ReflectiveOperationException|LinkageError unavailable){return null;}}
    }
}
