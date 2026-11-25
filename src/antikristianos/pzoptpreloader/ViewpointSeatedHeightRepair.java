package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.Map;
import net.bytebuddy.jar.asm.*;

/** Keep the scene origin and seated relative-eye calculation in the same logical coordinates. */
public final class ViewpointSeatedHeightRepair {
    public static final String TARGET="viewpoint/FrameFill";
    public static final String EYE="viewpoint/input/Eye";
    public static final Map<String,String> RESOURCES=Map.of(
        TARGET,"7cbd4916575bbb8bc71d572bfb9b616569237e30e1c3359d350ec4fd247d9d7f",
        EYE,"586a0f54453814c7295084e6c0fa7dccbe5b5fb7fc88c52527544282bc45c2d9",
        "viewpoint/core/Frame","c8680e44de376f822d383ba5a20380ad9b7ed267b40d29908c817266195381ef",
        "viewpoint/models/Characters","d6897f27da3e78fd844025cb58331850880159356798883f3708d6d6f7118ec8");
    private ViewpointSeatedHeightRepair(){ }
    public static byte[] patch(String name,byte[] bytes)throws IOException{
        if(!TARGET.equals(name)&&!EYE.equals(name))return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!name.equals(reader.getClassName()))throw new IOException("Seated height target differs");
        int[] sites={0,0};ClassWriter writer=new ClassWriter(reader,0);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                MethodVisitor out=super.visitMethod(access,name,descriptor,signature,exceptions);
                int index=reader.getClassName().equals(TARGET)?
                    (name.equals("fill")&&descriptor.equals("(Lviewpoint/core/Frame;JLviewpoint/core/Frame;Lzombie/iso/IsoCell;Lzombie/characters/IsoGameCharacter;IZ)V")?0:-1):
                    name.equals("atHead")&&descriptor.equals("(Lzombie/characters/IsoGameCharacter;Lviewpoint/core/Frame;FFF)V")?0:
                    name.equals("put")&&descriptor.equals("(Lzombie/characters/IsoGameCharacter;Lviewpoint/core/Frame;FF)V")?1:-1;
                if(index<0)return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean face){
                        if(opcode==Opcodes.INVOKEVIRTUAL&&owner.equals("zombie/characters/IsoGameCharacter")&&method.equals("getZ")&&desc.equals("()F")){
                            sites[index]++;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/ViewpointSeatedHeightCompatibility","z","(Lzombie/characters/IsoGameCharacter;)F",false);
                        }else super.visitMethodInsn(opcode,owner,method,desc,face);
                    }
                };
            }
        },0);
        if(sites[0]!=1||sites[1]!=(name.equals(EYE)?1:0))throw new IOException("Reviewed seated height read sites differ");
        return writer.toByteArray();
    }
}
