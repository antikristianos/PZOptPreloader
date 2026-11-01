package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.Set;
import java.util.Map;
import net.bytebuddy.jar.asm.*;

/** Connect the native deferred shore floor to the separately installed lighting implementation. */
public final class ShoreFloorRepair {
    public static final Set<String> TARGETS=Set.of("pzopt/ChunkAo","pzopt/ChunkAo$Gl","pzopt/CloudShadow","pzopt/TileBatch",
        "zombie/iso/fboRenderChunk/FBORenderCell","zombie/iso/sprite/IsoSprite","zombie/core/opengl/ShaderUnit");
    private static final String LIGHT="antikristianos/pzoptpreloader/ShoreFloorLighting";
    private static final String SHADERS="antikristianos/pzoptpreloader/ShoreFloorShaders";
    private static final Map<String,Map<String,String>> METHODS=Map.of(
        "pzopt/ChunkAo",Map.of("<clinit>()V","5a7ff5a4ef4620453b5e5fb6c84bdb28acc7b94ad4a1aa02059e6c73bf2a6077",
            "context(Lpzopt/ChunkAo$Job;Lzombie/iso/fboRenderChunk/FBORenderChunk;Lzombie/iso/IsoChunk;IFZ)I","3ee1b7a91abadb2faf5ad7a64fc8f36c57c366adaa7d0ed298a710cb12497382"),
        "pzopt/ChunkAo$Gl",Map.of("kernelUniforms([ILpzopt/ChunkAo$Job;F)V","0811eb991407a1c8ad28b7ce91097854f66a8da0c5943cada372558edef4f2ef"),
        "pzopt/CloudShadow",Map.of("waterUniforms()V","d08ec7eeb546e0b810df4e685429e14d3c52cfc37a7eefa89d495423bd9b9643"),
        "pzopt/TileBatch",Map.of("start(IFF)Z","8e914df264ebb22047245990a3379854cc9ff9526a9064bef260d7c681e0ef29"),
        "zombie/iso/fboRenderChunk/FBORenderCell",Map.of("renderFloor(Lzombie/iso/IsoObject;)V","2723610f0ac15ad0bb3766cd524a36f731969378e197ed6abeea1bf7f18cb70b"),
        "zombie/iso/sprite/IsoSprite",Map.of("startTileDepthShader(Lzombie/iso/IsoObject;FFFFZ)V","c067869f65eb5e88dfb5e1123c7073c4867fcb62f22131c3c421b5d1da25a66f"),
        "zombie/core/opengl/ShaderUnit",Map.of("compile()Z","b841076222dbbde924b79a4c378e18aa4c3ad1eb7e102867f0b685a1d6e36cda"));
    private ShoreFloorRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!TARGETS.contains(name))return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!reader.getClassName().equals(name))throw new IOException("Shore lighting class identity: "+name);
        Map<String,String> actual=Compatibility.methods(bytes);
        for(var expected:METHODS.get(name).entrySet())if(!expected.getValue().equals(actual.get(expected.getKey())))
            throw new IOException("Unsupported shore lighting fingerprint: "+name+"."+expected.getKey());
        int[] hits=new int[4];
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                MethodVisitor target=super.visitMethod(a,n,d,s,e);
                return new MethodVisitor(Opcodes.ASM9,target){
                    private void call(String owner,String method,String desc){super.visitMethodInsn(Opcodes.INVOKESTATIC,owner,method,desc,false);}
                    @Override public void visitCode(){
                        super.visitCode();
                        if(name.equals("pzopt/CloudShadow")&&n.equals("waterUniforms")&&d.equals("()V")){
                            hits[0]++;call(LIGHT,"waterCloudBind","()V");
                        }
                        if(name.equals("pzopt/TileBatch")&&n.equals("start")&&d.equals("(IFF)Z")){
                            // Every shore draw needs its own uniform boundary, including with tile batching enabled.
                            hits[0]++;call(LIGHT,"active","()Z");Label ordinary=new Label();super.visitJumpInsn(Opcodes.IFEQ,ordinary);
                            super.visitInsn(Opcodes.ICONST_0);super.visitInsn(Opcodes.IRETURN);super.visitLabel(ordinary);
                            super.visitFrame(Opcodes.F_SAME,0,null,0,null);
                        }
                        if(name.equals("zombie/iso/fboRenderChunk/FBORenderCell")&&n.equals("renderFloor")&&d.equals("(Lzombie/iso/IsoObject;)V")){
                            hits[0]++;super.visitVarInsn(Opcodes.ALOAD,1);call(LIGHT,"begin","(Lzombie/iso/IsoObject;)V");
                        }
                        if(name.equals("pzopt/ChunkAo")&&n.equals("context")&&d.equals("(Lpzopt/ChunkAo$Job;Lzombie/iso/fboRenderChunk/FBORenderChunk;Lzombie/iso/IsoChunk;IFZ)I")){
                            hits[0]++;super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,2);super.visitVarInsn(Opcodes.ALOAD,1);
                            call(LIGHT,"capture","(Ljava/lang/Object;Lzombie/iso/IsoChunk;Lzombie/iso/fboRenderChunk/FBORenderChunk;)V");
                        }
                    }
                    @Override public void visitInsn(int op){
                        if((op==Opcodes.RETURN||op==Opcodes.ATHROW)&&name.equals("zombie/iso/fboRenderChunk/FBORenderCell")&&n.equals("renderFloor")&&d.equals("(Lzombie/iso/IsoObject;)V"))call(LIGHT,"end","()V");
                        if(op==Opcodes.RETURN&&name.equals("pzopt/ChunkAo$Gl")&&n.equals("kernelUniforms")&&d.equals("([ILpzopt/ChunkAo$Job;F)V")){
                            hits[0]++;super.visitVarInsn(Opcodes.ALOAD,2);call(LIGHT,"uploadMask","(Ljava/lang/Object;)V");
                        }
                        super.visitInsn(op);
                    }
                    @Override public void visitFieldInsn(int op,String owner,String field,String desc){
                        if(name.equals("pzopt/ChunkAo")&&n.equals("<clinit>")&&op==Opcodes.PUTSTATIC&&field.equals("AO_FRAG")&&desc.equals("Ljava/lang/String;")){
                            hits[1]++;call(SHADERS,"kernel","(Ljava/lang/String;)Ljava/lang/String;");
                        }
                        super.visitFieldInsn(op,owner,field,desc);
                    }
                    @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){
                        boolean tile=name.equals("zombie/iso/sprite/IsoSprite")&&n.equals("startTileDepthShader")
                            &&d.equals("(Lzombie/iso/IsoObject;FFFFZ)V")&&owner.equals("zombie/IndieGL")&&method.equals("StartShader")
                            &&desc.equals("(ILzombie/core/opengl/ShaderUniformSetter;)V");
                        if(tile)super.visitInsn(Opcodes.DUP2);
                        if(name.equals("zombie/core/opengl/ShaderUnit")&&owner.equals("org/lwjgl/opengl/GL20")&&method.equals("glShaderSource")&&desc.equals("(ILjava/lang/CharSequence;)V")){
                            hits[0]++; // Preserve the complete installed shader chain; append our own narrow tile filter.
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETFIELD,name,"fileName","Ljava/lang/String;");
                            call(SHADERS,"tile","(Ljava/lang/CharSequence;Ljava/lang/String;)Ljava/lang/CharSequence;");
                        }
                        super.visitMethodInsn(op,owner,method,desc,itf);
                        if(tile){hits[0]++;super.visitInsn(Opcodes.POP);call(LIGHT,"tileStarted","(I)V");}
                    }
                };
            }
        },0);
        boolean valid=switch(name){
            case "pzopt/ChunkAo" -> hits[0]==1&&hits[1]==1;
            case "zombie/iso/sprite/IsoSprite" -> hits[0]==2;
            default -> hits[0]==1;
        };
        if(!valid)throw new IOException("Unsupported shore lighting boundary: "+name+" ("+java.util.Arrays.toString(hits)+")");
        return writer.toByteArray();
    }
}
