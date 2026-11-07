package antikristianos.pzoptpreloader;

import java.io.IOException;
import net.bytebuddy.jar.asm.*;

/** B42.22 client: preserve native position rounding and the shared puddle matrix boundary. */
public final class PuddleMatrixRepair {
    public static final String TARGET="pzopt/PuddleVbo$Gl";
    public static final String DESCRIPTOR="(Lpzopt/PuddleVbo$Frame;)V";
    private static final String HASH="7b1abb43bb9658619de0bef13a693d46dcfccacde8194422fa0d60f7eb6f530c";
    private static final String STATE="antikristianos/pzoptpreloader/PuddleMatrixState";
    public static final String CACHE="pzopt/PuddleCache";
    private static final String BATCH="pzopt/PuddleCache$Batch";
    private static final String BUILD="(Lzombie/iso/IsoPuddles;Lpzopt/PuddleCache$Batch;Lzombie/iso/IsoChunk;IILjava/util/List;Lzombie/iso/fboRenderChunk/FBORenderCutaways$ChunkLevelData;JFFIII)V";
    private static final String RENDER="(Lzombie/iso/IsoPuddles;ILjava/util/ArrayList;I)V";
    private PuddleMatrixRepair() { }

    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(CACHE.equals(name))return cache(bytes);
        if(!TARGET.equals(name))return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!TARGET.equals(reader.getClassName())||!HASH.equals(Compatibility.methods(bytes).get("draw"+DESCRIPTOR)))
            throw new IOException("Unsupported cached puddle matrix boundary: "+name);
        int[] hits=new int[4];ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                MethodVisitor target=super.visitMethod(access,method,descriptor,signature,exceptions);
                if(!method.equals("draw")||!descriptor.equals(DESCRIPTOR))return target;
                return new MethodVisitor(Opcodes.ASM9,target){
                    int program=-1;boolean fromProgram,zero;
                    @Override public void visitVarInsn(int opcode,int index){
                        if(fromProgram&&opcode==Opcodes.ASTORE){program=index;hits[0]++;}
                        fromProgram=false;zero=false;super.visitVarInsn(opcode,index);
                    }
                    @Override public void visitInsn(int opcode){zero=opcode==Opcodes.ICONST_0;fromProgram=false;super.visitInsn(opcode);}
                    @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf){
                        if(owner.equals("zombie/core/ShaderHelper")&&name.equals("glUseProgramObjectARB")&&descriptor.equals("(I)V")&&zero){
                            if(program<0)throw new IllegalStateException("Cached puddle shader program local missing");
                            // The original zero remains on the stack; restore BASE while the actual puddle program is bound.
                            super.visitVarInsn(Opcodes.ALOAD,program);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"restore","(Lzombie/core/opengl/ShaderProgram;)V",false);hits[1]++;
                        }
                        if(owner.equals("org/joml/Matrix4f")&&name.equals("translate")&&descriptor.equals("(FFF)Lorg/joml/Matrix4f;")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"position","(Lorg/joml/Matrix4f;FFF)Lorg/joml/Matrix4f;",false);hits[2]++;zero=false;fromProgram=false;return;
                        }
                        super.visitMethodInsn(opcode,owner,name,descriptor,itf);
                        if(owner.equals("zombie/core/opengl/ShaderProgram")&&name.equals("setValue")&&descriptor.equals("(Ljava/lang/String;Lorg/joml/Matrix4f;)V")){
                            super.visitVarInsn(Opcodes.ALOAD,program);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"jiggle","(Lzombie/core/opengl/ShaderProgram;)V",false);hits[3]++;
                        }
                        fromProgram=owner.equals("zombie/core/opengl/Shader")&&name.equals("getProgram")&&descriptor.equals("()Lzombie/core/opengl/ShaderProgram;");zero=false;
                    }
                };
            }
        },0);
        if(hits[0]!=1||hits[1]!=1||hits[2]!=1||hits[3]!=1)throw new IOException("Unsupported cached puddle matrix shape");
        return writer.toByteArray();
    }
    private static byte[] cache(byte[] bytes)throws IOException{
        ClassReader reader=new ClassReader(bytes);var methods=Compatibility.methods(bytes);
        if(!CACHE.equals(reader.getClassName())||!"28dbdc4bbe61cafde7d7ae1befcefe5c076f9b942e1a5633b64865106bbf34fc".equals(methods.get("build"+BUILD))
            ||!"03b279b23a45cc58b493e4408d74fdb03544c17419900bfa538935a54eaf6a7a".equals(methods.get("renderVbo"+RENDER)))
            throw new IOException("Unsupported native puddle cache position boundary");
        int[] hits=new int[3];ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                MethodVisitor target=super.visitMethod(access,name,descriptor,signature,exceptions);
                if(name.equals("build")&&descriptor.equals(BUILD))return new MethodVisitor(Opcodes.ASM9,target){
                    @Override public void visitInsn(int opcode){
                        if(opcode==Opcodes.RETURN){
                            for(String field:new String[]{"data","squares","count"}){
                                super.visitVarInsn(Opcodes.ALOAD,1);super.visitFieldInsn(Opcodes.GETFIELD,BATCH,field,field.equals("data")?"[F":field.equals("squares")?"[Lzombie/iso/IsoGridSquare;":"I");
                            }
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/PuddlePositionState","canonical","([F[Lzombie/iso/IsoGridSquare;I)V",false);
                            for(String field:new String[]{"jx","jy"}){super.visitVarInsn(Opcodes.ALOAD,1);super.visitInsn(Opcodes.FCONST_0);super.visitFieldInsn(Opcodes.PUTFIELD,BATCH,field,"F");}hits[0]++;
                        }
                        super.visitInsn(opcode);
                    }
                };
                if(!name.equals("renderVbo")||!descriptor.equals(RENDER))return target;
                return new MethodVisitor(Opcodes.ASM9,target){
                    int stores,jx=-1,jy=-1,lastLoad=-1;
                    @Override public void visitVarInsn(int opcode,int index){
                        if(opcode==Opcodes.FSTORE){if(stores==0)jx=index;else if(stores==1)jy=index;stores++;}
                        lastLoad=opcode==Opcodes.FLOAD?index:-1;super.visitVarInsn(opcode,index);
                    }
                    @Override public void visitInsn(int opcode){
                        if(opcode==Opcodes.FSUB&&lastLoad>=0&&(lastLoad==jx||lastLoad==jy)){
                            super.visitInsn(Opcodes.POP);hits[lastLoad==jx?1:2]++;
                        }else super.visitInsn(opcode);
                        lastLoad=-1;
                    }
                };
            }
        },0);
        if(hits[0]!=1||hits[1]!=1||hits[2]!=1)throw new IOException("Unsupported native puddle cache arithmetic shape");
        return writer.toByteArray();
    }
}
