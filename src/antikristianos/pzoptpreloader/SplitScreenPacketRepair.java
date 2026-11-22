package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import net.bytebuddy.jar.asm.*;

/** Exact final native producer sites; legacy sources and server emission retain their prior contract. */
public final class SplitScreenPacketRepair {
    private static final String HELPER="antikristianos/pzoptpreloader/SplitScreenPacketPool";
    private static final class Pins {
        static final JSONObject PROFILES=read();
        private static JSONObject read(){try(InputStream in=SplitScreenPacketRepair.class.getResourceAsStream("split-screen-packets-42.22.json")){
            if(in==null)throw new IOException("Missing native packet selector profile");return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }catch(IOException error){throw new ExceptionInInitializerError(error);}}
    }
    private SplitScreenPacketRepair(){ }
    public static byte[] patch(String name,byte[] bytes,String commit)throws IOException {
        if(commit==null)return bytes;
        JSONObject profile=Pins.PROFILES.optJSONObject(commit);if(profile==null)throw new IOException("Unsupported native packet selector source: "+commit);
        JSONObject methods=profile.optJSONObject(name);if(methods==null)return bytes;
        ClassReader reader=new ClassReader(bytes);if(!reader.getClassName().equals(name))throw new IOException("Native packet selector identity differs");
        var actual=Compatibility.methods(bytes);
        for(String key:methods.keySet())if(!methods.getJSONObject(key).getString("sha256").equals(actual.get(key)))throw new IOException("Unsupported native packet selector boundary: "+name+"."+key);
        InputCode code=new InputCode(bytes);int[] changed={0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                MethodVisitor out=super.visitMethod(a,n,d,s,e);JSONObject pin=methods.optJSONObject(n+d);if(pin==null)return out;
                JSONObject sites=pin.getJSONObject("sites");Map<Integer,Integer> saved=new HashMap<>();int local=code.methods.get(n+d).maxLocals;
                for(String site:sites.keySet())if(sites.getJSONObject(site).has("reuseOf")){int first=sites.getJSONObject(site).getInt("reuseOf");if(!saved.containsKey(first))saved.put(first,local++);}
                JSONObject singleton=pin.optJSONObject("singleton"),leases=pin.optJSONObject("leases");final int singletonLocal=local;
                return new MethodVisitor(Opcodes.ASM9,out){
                    int ordinal,hits,singletonReads,writes,leaseHits;
                    @Override public void visitFieldInsn(int op,String owner,String field,String type){
                        if(op==Opcodes.PUTFIELD){JSONObject lease=leases==null?null:leases.optJSONObject(Integer.toString(writes++));if(lease!=null){
                            if(!owner.equals(lease.getString("owner"))||!field.equals(lease.getString("field"))||!type.equals(lease.getString("descriptor")))throw new IllegalStateException("Native frame consumer differs");
                            super.visitInsn(Opcodes.DUP);super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"lease","(Ljava/lang/Object;)V",false);leaseHits++;changed[0]++;
                        }}
                        if(singleton!=null&&op==Opcodes.GETSTATIC&&owner.equals(name)&&field.equals(singleton.getString("field"))){
                            if(singletonReads++==0){super.visitFieldInsn(op,owner,field,type);super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"singleton","(Ljava/lang/Object;)Ljava/lang/Object;",false);super.visitTypeInsn(Opcodes.CHECKCAST,singleton.getString("component"));super.visitInsn(Opcodes.DUP);super.visitVarInsn(Opcodes.ASTORE,singletonLocal);}
                            else super.visitVarInsn(Opcodes.ALOAD,singletonLocal);
                            hits++;changed[0]++;return;
                        }super.visitFieldInsn(op,owner,field,type);
                    }
                    @Override public void visitInsn(int op){
                        if(op==Opcodes.AALOAD){int current=ordinal++;JSONObject site=sites.optJSONObject(Integer.toString(current));if(site!=null){
                            if(site.has("reuseOf")){super.visitInsn(Opcodes.POP2);super.visitVarInsn(Opcodes.ALOAD,saved.get(site.getInt("reuseOf")));}
                            else super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"select","([Ljava/lang/Object;I)Ljava/lang/Object;",false);
                            super.visitTypeInsn(Opcodes.CHECKCAST,site.getString("component"));
                            if(saved.containsKey(current)){super.visitInsn(Opcodes.DUP);super.visitVarInsn(Opcodes.ASTORE,saved.get(current));}
                            hits++;changed[0]++;return;
                        }}super.visitInsn(op);
                    }
                    @Override public void visitEnd(){if(hits!=sites.length()+(singleton==null?0:2)||leaseHits!=(leases==null?0:leases.length()))throw new IllegalStateException("Native packet selector inventory differs: "+name+"."+n+d);super.visitEnd();}
                };
            }
        },0);
        if(changed[0]==0)throw new IOException("Native packet selector target has no patched sites: "+name);return writer.toByteArray();
    }
}
