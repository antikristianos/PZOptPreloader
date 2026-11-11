package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Narrow, fingerprinted weather capture hooks on the original chunk bake stream. */
public final class ShoreWeatherRepair {
    private static final String CELL="zombie/iso/fboRenderChunk/FBORenderCell",COVER="pzopt/GroundCover";
    private static final String STATE="antikristianos/pzoptpreloader/ShoreWeatherState";
    public static final Set<String> TARGETS=Set.of(CELL,COVER,COVER+"$Gpu");
    private static final Map<String,Set<String>> CELL_PINS=Map.of(
        "pzoptBakeFloors(Lzombie/iso/IsoChunk;ILjava/util/ArrayList;)V",Set.of("e2ceb9ecfd461f959b2f940aefdff7d89c114f782143915201efa205778eefb9","ab02d03887d938f5cec53621f29251279ca735c0466ecd600127834c4dcd30e7"),
        "pzoptBakeSnow(Lzombie/iso/IsoChunk;I)V",Set.of("ff2128b6b7924aabd7676d282418823f6d281fbaf60804d7fd61ca5689184ea1","d25063fa231003dcefc5c394c95042caabad9629d68ff58ee92adca518040a7b"));
    private ShoreWeatherRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!TARGETS.contains(name))return bytes;
        Map<String,String> methods=Compatibility.methods(bytes);
        if(name.equals(CELL)){
            for(var pin:CELL_PINS.entrySet())if(pin.getValue().stream().noneMatch(previous->RepairProfiles.matches(previous,methods.get(pin.getKey()))))
                throw new IOException("Unsupported shore weather bake boundary: "+pin.getKey());
        }else if(name.endsWith("$Gpu")){
            if(!RepairProfiles.matches("9b392135ebc389d4d508ea6284dd288a5d2a033d829046d0c8de23daee621bbb",methods.get("draw(Lpzopt/GroundCover$Drawer;)V"))||!RepairProfiles.matches("c77753e989a863353b92dcdeb30f133938d7796778e23a455a9642b69cbcd74c",methods.get("init()Z")))throw new IOException("Unsupported cover blend/program boundary");
        }else if(!RepairProfiles.matches("aa2c2aca36078eed381cf97bc58d422efed7f24100b366e151294464b946c728",methods.get("bake(Lzombie/iso/IsoChunk;II)V"))
            )
            throw new IOException("Unsupported installed cover weather boundary");
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] hits=new int[3];
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,method,desc,sig,exceptions)){
                    @Override public void visitCode(){super.visitCode();
                        if(name.equals(CELL)&&method.equals("pzoptBakeFloors")){
                            super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ILOAD,2);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"bakeStart","(Lzombie/iso/IsoChunk;I)V",false);hits[0]++;
                        }
                    }
                    @Override public void visitMethodInsn(int op,String owner,String called,String d,boolean itf){
                        if(name.endsWith("$Gpu")&&method.equals("init")&&owner.equals("pzopt/Shaders")&&called.equals("program")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/ShoreWeatherShaders","cover","(Ljava/lang/String;)Ljava/lang/String;",false);hits[0]++;
                        }
                        if(name.equals(CELL)&&method.equals("pzoptBakeSnow")&&owner.equals("zombie/iso/fboRenderChunk/FBORenderSnow")&&called.equals("RenderSnow")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"snow","(Lzombie/iso/fboRenderChunk/FBORenderSnow;Lzombie/iso/IsoChunk;I)V",false);hits[1]++;return;
                        }
                        boolean cover=name.equals(COVER)&&method.equals("bake")&&owner.equals("zombie/core/SpriteRenderer")&&called.equals("drawGeneric");
                        if(cover){super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ILOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"begin","(Lzombie/iso/IsoChunk;I)V",false);hits[0]++;hits[1]++;}
                        super.visitMethodInsn(op,owner,called,d,itf);
                        if(name.endsWith("$Gpu")&&method.equals("draw")&&called.equals("glBlendFuncSeparate")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"coverBlend","()V",false);hits[1]++;
                        }
                        if(cover)super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"end","()V",false);
                    }
                };
            }
        },0);
        if(hits[0]!=1||hits[1]!=1)throw new IOException("Unexpected weather hook count: "+name+Arrays.toString(hits));
        return writer.toByteArray();
    }
}
