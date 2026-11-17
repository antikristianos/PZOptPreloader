package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.Map;
import net.bytebuddy.jar.asm.*;

/** Narrow repairs for the verified fad57862 source, before early-schema adaptation. */
public final class IssueSafetyRepair {
    private static final String MAP="zombie/iso/IsoChunkMap", PRODUCER=MAP+"$1";
    private static final String LUA="se/krka/kahlua/integration/LuaCaller";
    private static final String ARRAY="(Lse/krka/kahlua/vm/KahluaThread;Ljava/lang/Object;[Ljava/lang/Object;)V";
    private static final String LIFE="antikristianos/pzoptpreloader/ChunkHandoffLifetime";
    private static final Map<String,Map<String,String>> PINS=Map.of(
        MAP,Map.of("Dispose()V","44207f951c419872eec253693ea0a307aafdf2b9e5f63cdf179a33139538806b",
            "updateInternal()V","6f1785d65b2d999d69712356b70c017b5b5d17f7218b7a75fdbcd936ef2896b7"),
        PRODUCER,Map.of("pending()Z","ca5e08b287ede255eefe4c69b17be1a0a03de0c11e383e8ed1c5cc9dbd3b7d6d",
            "runNext()V","5c84f60767bd24c83bdee1bfc407fc65ac353de6833481007831b351d220b0e8"),
        LUA,Map.of("pcallvoid"+ARRAY,"8d9b2fc26fdd8f031f19c90c804c4978c83f623870330e336363d6baebca8738"));
    private IssueSafetyRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        var pins=PINS.get(name);if(pins==null)return bytes;
        var actual=Compatibility.methods(bytes);
        for(var pin:pins.entrySet())if(!pin.getValue().equals(actual.get(pin.getKey())))
            throw new IOException("Unsupported issue safety boundary: "+name+"."+pin.getKey());
        ClassReader reader=new ClassReader(bytes);
        if(!name.equals(reader.getClassName()))throw new IOException("Issue safety class identity: "+name);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        int[] snapshots={0},offers={0},requests={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] exceptions){
                MethodVisitor output=super.visitMethod(access,method,desc,sig,exceptions);
                if(!pins.containsKey(method+desc))return output;
                return new MethodVisitor(Opcodes.ASM9,output){
                    @Override public void visitCode(){
                        super.visitCode();
                        if(name.equals(MAP)&&method.equals("Dispose")){
                            super.visitInsn(Opcodes.ACONST_NULL);
                            super.visitFieldInsn(Opcodes.PUTSTATIC,MAP,"pzoptStaged","Lzombie/iso/IsoChunk;");
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,LIFE,"reset","()V",false);
                        }else if(name.equals(PRODUCER)){
                            Label active=new Label();
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"pzopt/SlackWork","frame","()I",false);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,LIFE,"live","(I)Z",false);
                            super.visitJumpInsn(Opcodes.IFNE,active);
                            if(method.equals("pending")){super.visitInsn(Opcodes.ICONST_0);super.visitInsn(Opcodes.IRETURN);}
                            else super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(active);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
                            if(method.equals("runNext")){
                                Label staged=new Label();super.visitFieldInsn(Opcodes.GETSTATIC,MAP,"pzoptStaged","Lzombie/iso/IsoChunk;");
                                super.visitJumpInsn(Opcodes.IFNONNULL,staged);super.visitInsn(Opcodes.RETURN);
                                super.visitLabel(staged);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
                            }
                        }
                    }
                    @Override public void visitFieldInsn(int op,String owner,String field,String descriptor){
                        super.visitFieldInsn(op,owner,field,descriptor);
                        if(name.equals(MAP)&&method.equals("updateInternal")&&op==Opcodes.GETSTATIC
                                &&owner.equals("pzopt/Config")&&field.equals("CHUNK_HANDOFF_SLACK_WORK")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,LIFE,"eligible","(Z)Z",false);offers[0]++;
                        }
                    }
                    @Override public void visitInvokeDynamicInsn(String n,String d,Handle bootstrap,Object... args){
                        if(name.equals(LUA)&&d.endsWith("[Ljava/lang/Object;)Ljava/lang/Runnable;")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/DeferredLuaArguments",
                                "snapshot","([Ljava/lang/Object;)[Ljava/lang/Object;",false);snapshots[0]++;
                        }
                        super.visitInvokeDynamicInsn(n,d,bootstrap,args);
                    }
                    @Override public void visitMethodInsn(int op,String owner,String n,String d,boolean itf){
                        if(name.equals(MAP)&&method.equals("updateInternal")&&op==Opcodes.INVOKESTATIC
                                &&owner.equals("pzopt/SlackWork")&&n.equals("register")&&d.equals("(Lpzopt/SlackWork$Producer;)V")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"pzopt/SlackWork","frame","()I",false);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,LIFE,"request","(I)V",false);requests[0]++;
                        }
                        super.visitMethodInsn(op,owner,n,d,itf);
                    }
                };
            }
        },0);
        if(name.equals(MAP)&&(offers[0]!=1||requests[0]!=1)||name.equals(LUA)&&snapshots[0]!=1)
            throw new IOException("Issue safety instruction shape: "+name);
        return writer.toByteArray();
    }
}
