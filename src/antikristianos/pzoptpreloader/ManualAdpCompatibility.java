package antikristianos.pzoptpreloader;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Recognizes the installed ADP simulation; its original physics remains its owner. */
public final class ManualAdpCompatibility {
    public static final String SIMULATION="zombie/core/physics/WorldSimulation";
    private static final String CONTROLLER="zombie/core/physics/CarController";
    enum Kind { ABSENT, STANDARD, OPTCOMPAT }
    private static final Map<String,Kind> WORLD_HASHES=Map.of(
        "cd874e23b69c7c72067b7914f35f6029a07aa1e404631a9bb2f19d69a6591563",Kind.STANDARD,
        "17ee8f7c23f5820bf31dd94f88d7f349412e049364f526542e515ec02f5fffd3",Kind.OPTCOMPAT);
    private static final String CONTROLLER_HASH="c1e38b3dc3151b8dea3f96edd53f768cf2f4832991782a01b2a9f4213813ff57";
    private ManualAdpCompatibility() { }

    static Kind detect()throws IOException {
        ClassLoader loader=ManualAdpCompatibility.class.getClassLoader();
        byte[] world=resource(loader,SIMULATION),controller=resource(loader,CONTROLLER);
        if(world==null||controller==null)return Kind.ABSENT;
        boolean[] needsField={false};
        new ClassReader(controller).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM9){
                    @Override public void visitFieldInsn(int opcode,String owner,String field,String descriptor){
                        if(owner.equals(SIMULATION)&&field.equals("massScaler")&&descriptor.equals("F"))needsField[0]=true;
                    }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        if(!needsField[0])return Kind.ABSENT;
        Kind kind=WORLD_HASHES.get(hash(world));
        if(kind==null||!CONTROLLER_HASH.equals(hash(controller)))
            throw new IOException("Unverified manual ADP physics classes; refusing to overwrite their required massScaler schema");
        return kind;
    }
    private static byte[] resource(ClassLoader loader,String name)throws IOException {
        try(InputStream input=loader.getResourceAsStream(name+".class")){return input==null?null:input.readAllBytes();}
    }
    private static String hash(byte[] bytes)throws IOException {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}
    }
    static byte[] keepManualPhysics(String name,byte[] bytes)throws IOException {
        if(!name.equals("pzopt/VehicleSmooth"))return bytes;
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        Set<String> gated=new HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions){
                if(!Set.of("enabled","stepsChanged").contains(method)||!desc.equals("()Z"))return super.visitMethod(access,method,desc,signature,exceptions);
                if((access&Opcodes.ACC_STATIC)==0)throw new IllegalStateException("Unexpected vehicle physics gate");
                MethodVisitor target=super.visitMethod(access,method,desc,signature,exceptions);
                target.visitCode();target.visitInsn(Opcodes.ICONST_0);target.visitInsn(Opcodes.IRETURN);target.visitMaxs(1,0);target.visitEnd();
                gated.add(method);return null;
            }
        },0);
        if(gated.size()!=2)throw new IOException("Unverified PZOpt vehicle smoothing gates; manual ADP ownership cannot be preserved");
        return writer.toByteArray();
    }
}
