package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.jar.asm.*;

/** Keep the installed sampler cache coherent across native render-command scopes. B42.22 client only. */
public final class TextureSamplingRepair {
    public static final Set<String> TARGETS=Set.of("zombie/core/textures/TextureID","zombie/core/textures/TextureDraw");
    private static final String STATE="antikristianos/pzoptpreloader/TextureSamplingState";
    private static final String STAMP="pzoptPreloaderSamplerGeneration";
    private static final Map<String,String> HASHES=Map.of(
        "zombie/core/textures/TextureID","3dcd0b5899702a9ff1c703f6982e1eb1a57273274136218f9aefe5e2c66bc902",
        "zombie/core/textures/TextureDraw","28bee5c4d371558bf9068c5ace6e133db296418045fb775919459c36e29401ca");
    private TextureSamplingRepair() { }

    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!TARGETS.contains(name))return bytes;
        boolean texture=name.endsWith("/TextureID");
        String method=texture?"assignFilteringFlags":"run";
        if(!RepairProfiles.matches(HASHES.get(name),Compatibility.methods(bytes).get(method+"()V")))
            throw new IOException("Unsupported sampler-cache boundary: "+name+"."+method);
        ClassReader reader=new ClassReader(bytes);
        if(!reader.getClassName().equals(name))throw new IOException("Sampler class identity: "+name);
        int[] fields=new int[3],hits=new int[3];
        reader.accept(new ClassVisitor(Opcodes.ASM9){
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){
                if(n.equals(STAMP))fields[0]++;
                if(n.equals("pzoptSentMin")&&d.equals("I"))fields[1]++;
                if(n.equals("pzoptSentMag")&&d.equals("I"))fields[2]++;
                return null;
            }
        },ClassReader.SKIP_CODE);
        if(fields[0]!=0||(texture&&(fields[1]!=1||fields[2]!=1)))throw new IOException("Unsupported sampler fields: "+name);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public void visitEnd(){
                if(texture)super.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_SYNTHETIC,STAMP,"J",null,null).visitEnd();
                super.visitEnd();
            }
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                MethodVisitor target=super.visitMethod(a,n,d,s,e);
                if(!n.equals(method)||!d.equals("()V"))return target;
                return new MethodVisitor(Opcodes.ASM9,target){
                    @Override public void visitCode(){
                        super.visitCode();hits[0]++;
                        if(!texture)return;
                        // No state query or reflection in a bind. Refresh once per texture after an unsafe command.
                        Label valid=new Label();
                        super.visitVarInsn(Opcodes.ALOAD,0);
                        super.visitFieldInsn(Opcodes.GETFIELD,name,STAMP,"J");
                        super.visitFieldInsn(Opcodes.GETSTATIC,STATE,"generation","J");
                        super.visitInsn(Opcodes.LCMP);super.visitJumpInsn(Opcodes.IFEQ,valid);
                        for(String field:new String[]{"pzoptSentMin","pzoptSentMag"}){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitInsn(Opcodes.ICONST_M1);
                            super.visitFieldInsn(Opcodes.PUTFIELD,name,field,"I");
                        }
                        super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETSTATIC,STATE,"generation","J");
                        super.visitFieldInsn(Opcodes.PUTFIELD,name,STAMP,"J");super.visitLabel(valid);
                        super.visitFrame(Opcodes.F_SAME,0,null,0,null);
                    }
                    @Override public void visitFieldInsn(int op,String owner,String f,String desc){
                        super.visitFieldInsn(op,owner,f,desc);
                        if(texture&&op==Opcodes.GETSTATIC&&owner.equals("pzopt/Config")&&f.equals("TEX_PARAM_CACHE")&&desc.equals("Z")){
                            hits[1]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,"pzopt/Overrides","enabled","()Z",false);
                            super.visitInsn(Opcodes.IAND);
                        }
                    }
                    @Override public void visitInsn(int op){
                        if(!texture&&(op==Opcodes.RETURN||op==Opcodes.ATHROW)){
                            hits[2]++;super.visitVarInsn(Opcodes.ALOAD,0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"after","(Lzombie/core/textures/TextureDraw;)V",false);
                        }
                        super.visitInsn(op);
                    }
                };
            }
        },0);
        if(hits[0]!=1||(texture?hits[1]!=1:hits[2]==0))throw new IOException("Unsupported sampler patch shape: "+name);
        return writer.toByteArray();
    }
}
