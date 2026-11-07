package antikristianos.pzoptpreloader;

import java.io.*;
import java.util.*;
import java.util.jar.JarFile;
import net.bytebuddy.jar.asm.*;
import org.json.*;

/** Exact 42.22.1/42.22.2 menu rebases; other input and emitted-method checks remain in force. */
public final class HotfixCompatibility {
    public static final String REVISION="71e8410c42", REVISION_2="3d39148846";
    private static final String MENU="zombie/gameStates/MainScreenState";
    private static final Map<String,String> BEFORE=Map.of(
        MENU,"5396302bb52db409dc71e6a933bf30671714d2a9d02a1d72371b1fd1232dbf53",
        MENU+"$Credit","28394f9c32ba3a377dc2f971a9e96bb0c3e43dff8dc9f07e30a8ef51bf6ed4a3",
        MENU+"$ScreenElement","8a6345b71824fe69dfc6b895a445b539e463b0a525858b964019032d26ee0535");
    private static final Map<String,String> AFTER=Map.of(
        MENU,"aa34d69f7f424b27b3833d278958f8f5d4f021177fd524db838d61557b309eb6",
        MENU+"$Credit","d9cce8951804108b8228aed058e84cf039ac5e3ce0ac772427163e7f4d9b488e",
        MENU+"$ScreenElement","18c7a980c509b8c53488cab1b5f139e7eb1cc681f54e119ec3de5f70907b6623");
    private static final Map<String,String> AFTER_2=Map.of(
        MENU,"9f17e1516c62eca74e83d8a7cb838221f72d9e6514ad2fac1f200fb7834ee5c1",
        MENU+"$Credit",AFTER.get(MENU+"$Credit"),MENU+"$ScreenElement",AFTER.get(MENU+"$ScreenElement"));
    private HotfixCompatibility(){ }
    public static boolean supports(String revision){return REVISION.equals(revision)||REVISION_2.equals(revision);}
    private static Map<String,String> hashes(String revision){return REVISION_2.equals(revision)?AFTER_2:AFTER;}
    public static String stockHash(String revision,String name,String baseline)throws IOException{
        if(!supports(revision)||!BEFORE.containsKey(name))return baseline;
        if(!BEFORE.get(name).equals(baseline))throw new IOException("Hotfix baseline changed: "+name);
        return hashes(revision).get(name);
    }
    public static JSONObject recipes(JSONObject original,String revision)throws IOException{
        if(!supports(revision))return original;
        JSONObject plan=new JSONObject(original.toString());
        if(!BEFORE.get(MENU).equals(plan.getJSONObject("inputs").getString("g:"+MENU)))throw new IOException("Hotfix recipe input changed");
        // The only use of this native input is the old version writer. Supply its complete new native
        // definition after replay rather than applying old instruction offsets to the new method.
        JSONObject menu=plan.getJSONObject("classes").getJSONObject(MENU);JSONArray previous=menu.getJSONArray("methods"),kept=new JSONArray();int removed=0;
        for(int i=0;i<previous.length();i++){
            JSONObject method=previous.getJSONObject(i);
            if(method.getString("name").equals("writeOutCurrentVersion")&&method.getString("descriptor").equals("()V")){
                if(!method.getString("fingerprint").equals("6f247d14b27e9771af66676e3351468066f327a5633d52b6177a9c3ef53dd00b"))throw new IOException("Hotfix version writer boundary changed");removed++;
            }else kept.put(method);
        }
        if(removed!=1)throw new IOException("Hotfix version writer count changed");
        menu.put("methods",kept);plan.getJSONObject("inputs").remove("g:"+MENU);
        if(plan.getJSONObject("classes").toString().contains("g:"+MENU))throw new IOException("Additional native menu recipe references need review");
        return plan;
    }
    public static void rebase(Map<String,byte[]> classes,String revision,JarFile game)throws IOException{
        if(!supports(revision))return;
        var entry=game.getJarEntry(MENU+".class");if(entry==null)throw new IOException("Hotfix native menu missing");
        byte[] nativeBytes;try(InputStream in=game.getInputStream(entry)){nativeBytes=in.readAllBytes();}
        if(!hashes(revision).get(MENU).equals(Compatibility.hash(nativeBytes)))throw new IOException("Hotfix native menu/revision pair changed");
        classes.put(MENU,menu(classes.get(MENU),nativeBytes));
    }
    public static byte[] menu(byte[] ported,byte[] nativeBytes)throws IOException{
        String nativeHash=Compatibility.hash(nativeBytes);
        if(!AFTER.get(MENU).equals(nativeHash)&&!AFTER_2.get(MENU).equals(nativeHash))throw new IOException("Hotfix native menu changed");
        ClassReader nativeReader=new ClassReader(nativeBytes);Object[][] kickoff={null};int[] nativeCalls={0};
        nativeReader.accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                if(!n.equals("main")||!d.equals("([Ljava/lang/String;)V"))return null;
                return new MethodVisitor(Opcodes.ASM9){
                    Object[] pending;
                    @Override public void visitInvokeDynamicInsn(String name,String desc,Handle bootstrap,Object... arguments){pending=new Object[]{name,desc,bootstrap,arguments.clone()};}
                    @Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
                        if(owner.equals("zombie/core/IntegrityReport")&&name.equals("collectInBackground")&&desc.equals("(Ljava/lang/Runnable;)V")){
                            if(op!=Opcodes.INVOKESTATIC||pending==null)throw new IllegalStateException("Hotfix integrity kickoff changed");kickoff[0]=pending;nativeCalls[0]++;
                        }pending=null;
                    }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        if(nativeCalls[0]!=1)throw new IOException("Hotfix integrity kickoff count changed");
        ClassReader source=new ClassReader(ported);ClassWriter writer=new ClassWriter(source,ClassWriter.COMPUTE_MAXS);int[] hits=new int[4];
        source.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){if(n.equals("versionWrittenWhilePending"))throw new IllegalStateException("Duplicate hotfix field");return super.visitField(a,n,d,s,v);}
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                if(n.equals("writeOutCurrentVersion")||n.equals("rewriteVersionIfPending"))throw new IllegalStateException("Duplicate hotfix version method");
                MethodVisitor out=super.visitMethod(a,n,d,s,e);if(!n.equals("main")||!d.equals("([Ljava/lang/String;)V"))return out;
                return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
                    super.visitMethodInsn(op,owner,name,desc,itf);
                    if(op==Opcodes.INVOKESTATIC&&owner.equals("zombie/core/logger/LoggerManager")&&name.equals("init")&&desc.equals("()V")){
                        Object[] call=kickoff[0];super.visitInvokeDynamicInsn((String)call[0],(String)call[1],(Handle)call[2],(Object[])call[3]);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,"zombie/core/IntegrityReport","collectInBackground","(Ljava/lang/Runnable;)V",false);hits[0]++;
                    }
                }};
            }
            @Override public void visitEnd(){
                nativeReader.accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){if(!n.equals("versionWrittenWhilePending"))return null;hits[1]++;return writer.visitField(a,n,d,s,v);}
                    @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                        if(d.equals("()V")&&n.equals("writeOutCurrentVersion")){hits[2]++;return writer.visitMethod(a,n,d,s,e);}
                        if(d.equals("()V")&&n.equals("rewriteVersionIfPending")){hits[3]++;return writer.visitMethod(a,n,d,s,e);}return null;
                    }
                },0);super.visitEnd();
            }
        },0);
        if(!Arrays.equals(hits,new int[]{1,1,1,1}))throw new IOException("Hotfix menu rebase shape changed: "+Arrays.toString(hits));
        return writer.toByteArray();
    }
}
