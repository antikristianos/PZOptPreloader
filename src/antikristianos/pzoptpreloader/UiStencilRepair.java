package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Exact newest-source UI cache boundaries, after the existing movement/dependency repairs. */
public final class UiStencilRepair {
    public static final String TARGET="pzopt/UiRetained", REC=TARGET+"$Rec";
    private static final String HELPER="antikristianos/pzoptpreloader/UiStencilReplay", OWNER=HELPER+"$Owner", SPAN=HELPER+"$Span";
    private static final String FIELD="pzoptPreloaderStencilSpan", BEGIN="pzoptPreloaderStencilBegin";
    private static final String ROOT="(Lzombie/ui/UIElementInterface;)I", CHILD="(Lzombie/ui/UIElement;)I";
    private static final String RECORD="(Lpzopt/UiRetained$Rec;Lzombie/core/sprite/GenericSpriteRenderState;IIJ)V";
    private static final Map<String,Map<String,String>> PINS=Map.of(
        TARGET,Map.of("childBegin"+CHILD,"62e0499bda6715b13fe25277f0f0fd2da739d0054e5af36c7f188882e769f83e",
            "freshBegin"+ROOT,"2a94c621ece652171c294306298a37c5b6a1fb7f266a290581e795860cf74fd0",
            "needsFresh"+UiMoveReplayRepair.METHOD,"7b742b12d92e2b86c064d61d24c8f10cde3cf6b46fd3ab15d9def572eea79026",
            "record"+RECORD,"77838daba21da1ecc632f9b283e649c928977ac6ceee830588822049cfdcf5aa",
            "copyOut(Lpzopt/UiRetained$Rec;)V","75768958154df459733415f18f37471eb347796cfee22fdc511a76cf7d8cee6d"),
        REC,Map.of("<init>()V","620ee1194cd0c1ab7bec6a10006874f63fe7ed2236fd5631625d29fb21909b0e"));
    private UiStencilRepair() { }
    public static boolean enabled(String revision,boolean newest,boolean server) {
        return HotfixCompatibility.REVISION_2.equals(revision)&&newest&&!server;
    }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        Map<String,String> pins=PINS.get(name); if(pins==null)return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!reader.getClassName().equals(name))throw new IOException("UI stencil class identity: "+name);
        Map<String,String> actual=Compatibility.methods(bytes);
        for(var pin:pins.entrySet())if(!pin.getValue().equals(actual.get(pin.getKey())))
            throw new IOException("Unsupported UI stencil boundary: "+name+"."+pin.getKey());
        int[] collisions={0},hits={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9){
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){if(n.startsWith("pzoptPreloaderStencil"))collisions[0]++;return null;}
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){if(n.startsWith("pzoptPreloaderStencil"))collisions[0]++;return null;}
        },ClassReader.SKIP_CODE);
        if(collisions[0]!=0)throw new IOException("UI stencil repair already present: "+name);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public void visit(int v,int a,String n,String s,String p,String[] interfaces){
                if(name.equals(REC)){interfaces=Arrays.copyOf(interfaces,interfaces.length+1);interfaces[interfaces.length-1]=OWNER;}
                super.visit(v,a,n,s,p,interfaces);
            }
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                MethodVisitor output=super.visitMethod(a,n,d,s,e);
                if(!pins.containsKey(n+d))return output;
                hits[0]++;
                return new MethodVisitor(Opcodes.ASM9,output){
                    @Override public void visitCode(){
                        super.visitCode();
                        if(n.equals("needsFresh")){
                            Label compatible=new Label();super.visitVarInsn(Opcodes.ALOAD,1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"requiresFresh","(L"+OWNER+";)Z",false);
                            super.visitJumpInsn(Opcodes.IFEQ,compatible);super.visitInsn(Opcodes.ICONST_1);super.visitInsn(Opcodes.IRETURN);
                            super.visitLabel(compatible);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
                        }
                    }
                    @Override public void visitInsn(int op){
                        if(op==Opcodes.IRETURN&&(n.equals("childBegin")||n.equals("freshBegin"))){
                            super.visitVarInsn(Opcodes.ALOAD,0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,TARGET,BEGIN,"(ILzombie/ui/UIElementInterface;)I",false);
                        } else if(op==Opcodes.RETURN&&name.equals(REC)){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitTypeInsn(Opcodes.NEW,SPAN);super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(Opcodes.INVOKESPECIAL,SPAN,"<init>","()V",false);super.visitFieldInsn(Opcodes.PUTFIELD,REC,FIELD,"L"+SPAN+";");
                        } else if(op==Opcodes.RETURN&&(n.equals("record")||n.equals("copyOut"))){
                            super.visitVarInsn(Opcodes.ALOAD,0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,n.equals("record")?"recorded":"replayed","(L"+OWNER+";)V",false);
                            // copyOut bypasses producer GL-state setters, so their deduplication cache is stale.
                            if(n.equals("copyOut"))super.visitMethodInsn(Opcodes.INVOKESTATIC,"zombie/core/opengl/GLState","startFrame","()V",false);
                        }
                        super.visitInsn(op);
                    }
                };
            }
            @Override public void visitEnd(){
                if(name.equals(REC)){
                    super.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL|Opcodes.ACC_SYNTHETIC,FIELD,"L"+SPAN+";",null,null).visitEnd();
                    MethodVisitor m=super.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_SYNTHETIC,FIELD,"()L"+SPAN+";",null,null);
                    m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,REC,FIELD,"L"+SPAN+";");
                    m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,0);m.visitEnd();
                }else{
                    MethodVisitor m=super.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC,BEGIN,"(ILzombie/ui/UIElementInterface;)I",null,null);
                    m.visitCode();Label inactive=new Label();m.visitVarInsn(Opcodes.ILOAD,0);m.visitJumpInsn(Opcodes.IFLT,inactive);
                    m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(Opcodes.INVOKESTATIC,TARGET,"rec","(Lzombie/ui/UIElementInterface;)L"+REC+";",false);
                    m.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"begin","(L"+OWNER+";)V",false);
                    m.visitLabel(inactive);m.visitFrame(Opcodes.F_SAME,0,null,0,null);m.visitVarInsn(Opcodes.ILOAD,0);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();
                }
                super.visitEnd();
            }
        },0);
        if(hits[0]!=pins.size())throw new IOException("UI stencil shape mismatch: "+name);
        return writer.toByteArray();
    }
}
