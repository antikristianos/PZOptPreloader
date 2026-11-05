package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Original, fingerprint-gated guards around the separately installed UI cache. B42.22 client only. */
public final class UiDependencyRepair {
    public static final String DEPS="pzopt/UiDeps", LOG=DEPS+"$Log", RETAINED="pzopt/UiRetained";
    private static final String SAFETY="antikristianos/pzoptpreloader/UiDependencySafety";
    private static final String STATE=SAFETY+"$LogState", FLAG="pzoptPreloaderUiUnsafe";
    private static final String VALIDATE="(Lpzopt/UiDeps$Log;J)I";
    private static final String RESET="(ILzombie/ui/UIElement;)V";
    private static final Map<String,Map<String,String>> PINS=Map.of(
        DEPS,Map.of("validate"+VALIDATE,"28a6f3f77920d99c70e05bedd93b719fb4c29628b70a1df64ff897a2b2bcb927"),
        LOG,Map.of("size()I","eb4e25a911c6405779df9104af5e75e23d781d95bbd95e8fe0ae344585044ffd",
                      "reset"+RESET,"fbe677b8124e22173bdb6339b2b1bf55bb959cf8d771bc1aa7101f501cc4d888"),
        RETAINED,Map.of("needsFresh"+UiMoveReplayRepair.METHOD,"b573bf7c6512a2e0a5b9692ab71c1e7aec22c5334a3e60010631f9406ac49af1"));
    private UiDependencyRepair() { }

    public static byte[] patch(String name,byte[] bytes)throws IOException {
        Map<String,String> pins=PINS.get(name);
        if(pins==null)return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!reader.getClassName().equals(name))throw new IOException("UI dependency class identity: "+name);
        Map<String,String> actual=Compatibility.methods(bytes);
        for(var pin:pins.entrySet())if(!RepairProfiles.matches(pin.getValue(),actual.get(pin.getKey())))
            throw new IOException("Unsupported UI dependency boundary: "+name+"."+pin.getKey());
        int[] collisions={0},hits={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9){
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){if(n.startsWith("pzoptPreloaderUi"))collisions[0]++;return null;}
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){if(n.startsWith("pzoptPreloader"))collisions[0]++;return null;}
        },ClassReader.SKIP_CODE);
        if(collisions[0]!=0)throw new IOException("UI dependency repair already present: "+name);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public void visit(int v,int a,String n,String s,String parent,String[] interfaces){
                if(name.equals(LOG)){
                    interfaces=Arrays.copyOf(interfaces,interfaces.length+1);
                    interfaces[interfaces.length-1]=STATE;
                }
                super.visit(v,a,n,s,parent,interfaces);
            }
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                boolean validate=name.equals(DEPS)&&n.equals("validate")&&d.equals(VALIDATE);
                boolean size=name.equals(LOG)&&n.equals("size")&&d.equals("()I");
                if(validate||size){
                    hits[0]++;
                    wrapper(super.visitMethod(a,n,d,s,e),name,n,d,size);
                    return super.visitMethod((a&~(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PRIVATE,
                        "pzoptPreloaderUnchecked"+n,d,s,e);
                }
                MethodVisitor target=super.visitMethod(a,n,d,s,e);
                boolean reset=name.equals(LOG)&&n.equals("reset")&&d.equals(RESET);
                boolean fresh=name.equals(RETAINED)&&n.equals("needsFresh")&&d.equals(UiMoveReplayRepair.METHOD);
                if(!reset&&!fresh)return target;
                hits[0]++;
                return new MethodVisitor(Opcodes.ASM9,target){
                    @Override public void visitCode(){
                        super.visitCode();
                        if(reset){
                            // A new recording replaces the old graph; never retain a permanent per-window opt-out.
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitInsn(Opcodes.ICONST_0);
                            super.visitFieldInsn(Opcodes.PUTFIELD,LOG,FLAG,"Z");
                        }else{
                            Label healthy=new Label();
                            super.visitVarInsn(Opcodes.ALOAD,1);
                            super.visitFieldInsn(Opcodes.GETFIELD,RETAINED+"$Rec","deps","L"+LOG+";");
                            super.visitMethodInsn(Opcodes.INVOKEINTERFACE,STATE,FLAG,"()Z",true);
                            super.visitJumpInsn(Opcodes.IFEQ,healthy);super.visitInsn(Opcodes.ICONST_1);super.visitInsn(Opcodes.IRETURN);
                            super.visitLabel(healthy);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
                        }
                    }
                };
            }
            @Override public void visitEnd(){
                if(name.equals(LOG)){
                    super.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_SYNTHETIC,FLAG,"Z",null,null).visitEnd();
                    MethodVisitor get=super.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_SYNTHETIC,FLAG,"()Z",null,null);
                    get.visitCode();get.visitVarInsn(Opcodes.ALOAD,0);get.visitFieldInsn(Opcodes.GETFIELD,LOG,FLAG,"Z");
                    get.visitInsn(Opcodes.IRETURN);get.visitMaxs(0,0);get.visitEnd();
                    MethodVisitor set=super.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_SYNTHETIC,FLAG,"(Z)V",null,null);
                    set.visitCode();set.visitVarInsn(Opcodes.ALOAD,0);set.visitVarInsn(Opcodes.ILOAD,1);set.visitFieldInsn(Opcodes.PUTFIELD,LOG,FLAG,"Z");
                    // An unsafe graph is not a changed getter: do not learn noise from a stale mismatch.
                    Label clean=new Label();set.visitVarInsn(Opcodes.ILOAD,1);set.visitJumpInsn(Opcodes.IFEQ,clean);
                    for(String field:new String[]{"mismatch","mismatchLog","mismatchA","mismatchB"}){
                        String type=field.equals("mismatch")?"Ljava/lang/String;":field.equals("mismatchLog")?"L"+LOG+";":"Ljava/lang/Object;";
                        set.visitVarInsn(Opcodes.ALOAD,0);set.visitInsn(Opcodes.ACONST_NULL);set.visitFieldInsn(Opcodes.PUTFIELD,LOG,field,type);
                    }
                    set.visitVarInsn(Opcodes.ALOAD,0);set.visitInsn(Opcodes.ICONST_0);set.visitFieldInsn(Opcodes.PUTFIELD,LOG,"mismatchNoisy","Z");
                    set.visitLabel(clean);set.visitFrame(Opcodes.F_SAME,0,null,0,null);
                    set.visitInsn(Opcodes.RETURN);set.visitMaxs(0,0);set.visitEnd();
                }
                super.visitEnd();
            }
        },0);
        if(hits[0]!=(name.equals(LOG)?2:1))throw new IOException("Unsupported UI dependency shape: "+name);
        return writer.toByteArray();
    }
    private static void wrapper(MethodVisitor m,String owner,String method,String descriptor,boolean counting){
        int result=counting?1:3,exception=result+1;
        Label entered=new Label(),start=new Label(),end=new Label(),handler=new Label();
        m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitInsn(counting?Opcodes.ICONST_1:Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,SAFETY,"enter","(L"+STATE+";Z)Z",false);
        m.visitJumpInsn(Opcodes.IFNE,entered);
        // INVALID, rather than OPAQUE, makes the caller reject replay. Count a rejected recursive edge as zero.
        m.visitInsn(counting?Opcodes.ICONST_0:Opcodes.ICONST_2);m.visitInsn(Opcodes.IRETURN);
        m.visitLabel(entered);m.visitFrame(Opcodes.F_SAME,0,null,0,null);
        m.visitTryCatchBlock(start,end,handler,"java/lang/Throwable");m.visitLabel(start);
        m.visitVarInsn(Opcodes.ALOAD,0);if(!counting)m.visitVarInsn(Opcodes.LLOAD,1);
        m.visitMethodInsn(counting?Opcodes.INVOKESPECIAL:Opcodes.INVOKESTATIC,owner,"pzoptPreloaderUnchecked"+method,descriptor,false);
        m.visitVarInsn(Opcodes.ISTORE,result);m.visitLabel(end);
        m.visitInsn(counting?Opcodes.ICONST_1:Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,SAFETY,"leave","(Z)V",false);
        m.visitVarInsn(Opcodes.ILOAD,result);m.visitInsn(Opcodes.IRETURN);
        m.visitLabel(handler);m.visitFrame(Opcodes.F_SAME1,0,null,1,new Object[]{"java/lang/Throwable"});
        m.visitVarInsn(Opcodes.ASTORE,exception);m.visitInsn(counting?Opcodes.ICONST_1:Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,SAFETY,"leave","(Z)V",false);
        m.visitVarInsn(Opcodes.ALOAD,exception);m.visitInsn(Opcodes.ATHROW);m.visitMaxs(0,0);m.visitEnd();
    }
}
