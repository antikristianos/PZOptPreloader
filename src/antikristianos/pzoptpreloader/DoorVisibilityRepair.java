package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.Map;
import net.bytebuddy.jar.asm.*;

/** Chains the existing target/layer calculation and its ordinary obscuring invalidation. */
public final class DoorVisibilityRepair {
    public static final String CELL="zombie/iso/fboRenderChunk/FBORenderCell",SQUARE="zombie/iso/IsoGridSquare";
    private static final String STATE="antikristianos/pzoptpreloader/DoorVisibilityState";
    private static final Map<String,Map<String,String>> PINS=Map.of(
        CELL,Map.of("calculateObjectTargetAlpha(Lzombie/iso/IsoObject;)F","17fbe88fd3fcd65969361d421bd9af24b870520c225384ad0abe057591782711",
            "checkObjectsObscuringPlayer(I)V","cb5c18f50d4bc4b361acdeae78aeaabf80d1437b2128e9ae5a1d14e04f72cf97"),
        SQUARE,Map.of("calculateWallAlphaCommon(Lzombie/iso/IsoObject;ZZZIZZ)V","1f597a048ff2b0bef9e0393147eabf21e1842cc6efe2ac6a2f31813feab58971"));
    private DoorVisibilityRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        Map<String,String> pins=PINS.get(name);if(pins==null)return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!name.equals(reader.getClassName()))throw new IOException("Door visibility class identity: "+name);
        Map<String,String> actual=Compatibility.methods(bytes);
        for(var pin:pins.entrySet())if(!RepairProfiles.matches(pin.getValue(),actual.get(pin.getKey())))
            throw new IOException("Unsupported door visibility boundary: "+name+"."+pin.getKey());
        int[] hits={0,0,0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions){
                MethodVisitor base=super.visitMethod(access,method,desc,signature,exceptions);
                if(!pins.containsKey(method+desc))return base;
                return new MethodVisitor(Opcodes.ASM9,base){
                    @Override public void visitInsn(int opcode){
                        if(opcode==Opcodes.FRETURN&&method.equals("calculateObjectTargetAlpha")){
                            super.visitVarInsn(Opcodes.ALOAD,1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"target","(FLzombie/iso/IsoObject;)F",false);hits[0]++;
                        }
                        if(opcode==Opcodes.RETURN&&method.equals("checkObjectsObscuringPlayer")){
                            super.visitVarInsn(Opcodes.ILOAD,1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"refresh","(I)V",false);hits[1]++;
                        }
                        if(opcode==Opcodes.RETURN&&method.equals("calculateWallAlphaCommon")){
                            super.visitVarInsn(Opcodes.ALOAD,1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"wallAlpha","(Lzombie/iso/IsoObject;)V",false);hits[2]++;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        },0);
        if(name.equals(CELL)?hits[0]!=3||hits[1]!=1:hits[2]!=1)
            throw new IOException("Unsupported door visibility return shape: "+name+" "+java.util.Arrays.toString(hits));
        return writer.toByteArray();
    }
}
