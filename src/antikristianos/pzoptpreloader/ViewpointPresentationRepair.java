package antikristianos.pzoptpreloader;

import java.io.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Source-pinned no-consumer gates; original profiling/presentation/audio bodies remain authoritative. */
public final class ViewpointPresentationRepair {
    private static final String MAIN="viewpoint/game/MainPhases",TICK="viewpoint/game/GameTick",LUA="viewpoint/game/LuaTimes",FRAME="viewpoint/render/FrameGenPass",SOUND="viewpoint/game/SoundLift";
    private static final String HELPER="antikristianos/pzoptpreloader/ViewpointPresentationCompatibility";
    public static final Set<String> TARGETS=Set.of(MAIN,TICK,LUA,FRAME,SOUND);
    public static final Map<String,String> RESOURCES=Map.ofEntries(
        Map.entry(MAIN,"8a2387d962da8b63087a186aea16bb453b3782c2ee56d5a0b95cfebab851b0cc"),
        Map.entry(TICK,"2a9bd06cb8f772a4cc5c3e27229cab37f66e1067e8c971bca70e082179f4b4be"),
        Map.entry(LUA,"6675065c90066e5882f7819fd589fc5af68a16b9717c8f9318d8a21f0fab508b"),
        Map.entry(FRAME,"81af2c6da62cf30cf4f56c70c796c18637b0a2cf60bd15b425c237b27f3f94b4"),
        Map.entry(SOUND,"5d1921354c9deaff1d6c78d632cabaa236334c18eceb50a6887cac806b677328"),
        Map.entry("viewpoint/platform/Scopes","844c59ea52c9f33a21e6d1a10d38a34ef0e0ae4cf08eb0c1e52b1ad4bce8ca9a"),
        Map.entry("viewpoint/platform/PerformanceOverlay","049ba61825ec9ce94e4b4a5db0620e35b82913e7f19e1123ad41e07ef794bc9e"),
        Map.entry("viewpoint/platform/PerformanceOverlay$Mode","8d29039c8ee6a299dc40c3900552a3751c339970ac9bfbf8b8ec2f3f25ff8ee9"),
        Map.entry("viewpoint/core/View","54cfb99bcc0f196196c8e22633b2051a7c40def681458daf149b0ee041e23876"));
    private ViewpointPresentationRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!TARGETS.contains(name))return bytes;ClassReader reader=new ClassReader(bytes);if(!name.equals(reader.getClassName()))throw new IOException("Presentation target identity differs");
        try(InputStream in=ViewpointPresentationRepair.class.getClassLoader().getResourceAsStream(name+".class")){
            if(in==null)throw new IOException("Missing original presentation resource");byte[] original=in.readAllBytes();if(!RESOURCES.get(name).equals(Compatibility.hash(original))||!Compatibility.methods(original).equals(Compatibility.methods(bytes)))throw new IOException("Changed presentation methods: "+name);
        }
        int[] sites={0,0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,n,d,s,e)){
            private void call(String method,String desc){super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,method,desc,false);}
            private void consumers(){super.visitFieldInsn(Opcodes.GETSTATIC,"viewpoint/core/View","enabled","Z");super.visitFieldInsn(Opcodes.GETSTATIC,"viewpoint/platform/Scopes","recording","Z");}
            private void returnIfTrue(){Label original=new Label();super.visitJumpInsn(Opcodes.IFEQ,original);super.visitInsn(Opcodes.RETURN);super.visitLabel(original);super.visitFrame(Opcodes.F_SAME,0,null,0,null);}
            @Override public void visitCode(){super.visitCode();
                if(name.equals(MAIN)&&(n.equals("entered")||n.equals("left"))&&d.equals("(I)V")){
                    sites[0]++;super.visitVarInsn(Opcodes.ILOAD,0);if(n.equals("entered")){consumers();call("skipStart","(IZZ)Z");}else call("skipEnd","(I)Z");returnIfTrue();
                }else if(name.equals(TICK)&&Set.of("updating","updated","animating","animated").contains(n)&&d.equals("()V")){
                    sites[0]++;super.visitIntInsn(Opcodes.BIPUSH,n.startsWith("anim")?12:11);if(n.equals("updating")||n.equals("animating")){consumers();call("skipStart","(IZZ)Z");}else call("skipEnd","(I)Z");returnIfTrue();
                }else if(name.equals(LUA)){
                    boolean guard=true;
                    if(n.equals("triggering")&&d.equals("(Ljava/lang/String;)V")){consumers();call("skipLuaBegin","(ZZ)Z");}
                    else if(n.equals("triggered")&&d.equals("()V"))call("skipLuaEnd","()Z");
                    else if(n.equals("calling")&&d.equals("()V")||n.equals("called")&&d.equals("(Ljava/lang/Object;)V"))call("skipLuaCall","()Z");
                    else if(n.equals("frame")&&d.equals("()V")){super.visitFieldInsn(Opcodes.GETSTATIC,LUA,"asked","Z");consumers();call("skipLuaFrame","(ZZZ)Z");}else guard=false;
                    if(guard){sites[0]++;returnIfTrue();}
                }else if(name.equals(FRAME)&&n.equals("present")&&d.equals("()V")){
                    sites[0]++;super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETFIELD,FRAME,"drawn","Z");super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETFIELD,FRAME,"copied","Z");call("present","(ZZ)Z");Label original=new Label();super.visitJumpInsn(Opcodes.IFNE,original);
                    for(String field:List.of("kept","drawn","copied","joined")){super.visitVarInsn(Opcodes.ALOAD,0);super.visitInsn(Opcodes.ICONST_0);super.visitFieldInsn(Opcodes.PUTFIELD,FRAME,field,"Z");}super.visitInsn(Opcodes.RETURN);super.visitLabel(original);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
                }
            }
            @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){super.visitMethodInsn(op,owner,method,desc,itf);
                if(name.equals(SOUND)&&n.equals("ticked")&&d.equals("(Lfmod/fmod/FMODSoundEmitter;)V")&&op==Opcodes.INVOKESTATIC&&owner.equals(SOUND)&&method.equals("lift")&&desc.equals("()Lviewpoint/render/FarRelief$Lift;")){
                    sites[0]++;super.visitInsn(Opcodes.DUP);super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETSTATIC,SOUND,"raised","Z");super.visitFieldInsn(Opcodes.GETSTATIC,"zombie/network/GameServer","server","Z");call("soundStart","(Ljava/lang/Object;Lfmod/fmod/FMODSoundEmitter;ZZ)Z");Label original=new Label();super.visitJumpInsn(Opcodes.IFNE,original);super.visitInsn(Opcodes.POP);super.visitInsn(Opcodes.RETURN);super.visitLabel(original);super.visitFrame(Opcodes.F_SAME1,0,null,1,new Object[]{"viewpoint/render/FarRelief$Lift"});
                }
            }
            @Override public void visitInsn(int op){if(name.equals(SOUND)&&n.equals("ticked")&&op==Opcodes.RETURN){sites[1]++;super.visitVarInsn(Opcodes.ALOAD,0);call("soundEnd","(Lfmod/fmod/FMODSoundEmitter;)V");}super.visitInsn(op);}
        };}},0);
        int count=switch(name){case MAIN->2;case TICK->4;case LUA->5;default->1;};if(sites[0]!=count||name.equals(SOUND)&&sites[1]!=2)throw new IOException("Presentation boundary differs: "+name+Arrays.toString(sites));return writer.toByteArray();
    }
}
