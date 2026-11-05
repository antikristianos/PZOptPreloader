package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Captures actual deferred-floor receivers and consumes them after all world receivers exist. */
public final class ShoreReceiverRepair {
    private static final String STATE="antikristianos/pzoptpreloader/ShoreReceiverState", SHADERS="antikristianos/pzoptpreloader/ShoreReceiverShaders";
    private static final String SHADOW="pzopt/CapsuleShadow", FRAME=SHADOW+"$Frame", CELL="zombie/iso/fboRenderChunk/FBORenderCell";
    private static final String FLOORS="(IILzombie/core/opengl/Shader;Lzombie/core/opengl/Shader;J)V";
    private static final String DRAW="(L"+FRAME+";)V";
    private static final String WORK="(L"+CELL+";Lzombie/iso/IsoChunk;II)Z";
    private static final String START="(L"+CELL+";Ljava/util/List;Ljava/util/List;IIILzombie/core/opengl/Shader;Lzombie/core/opengl/Shader;J)V";
    private static final String PREFIX="pzoptPreloaderReceiver";
    public static final Set<String> TARGETS=Set.of(SHADOW,SHADOW+"$Gl",CELL,"pzopt/TileRecord","zombie/core/opengl/ShaderUnit");
    private static final Map<String,Map<String,String>> PINS=Map.of(
        SHADOW,Map.of("<clinit>()V","eaf96b057f717c456cbbf9112905c0aa83e262960c06959db0d014c1c6513a95",
            "queue(I)V","30347dbb2fd54c41aa6363c4c8dc69f132a7c3663b21ffc6fde890cdff5591e3",
            "afterMoving(I)V","beae96e6622e26d74da0c4b957b4d9b0cf488d3101211bc937d449f71bbfed42"),
        SHADOW+"$Gl",Map.of("draw"+DRAW,"b2c4eb89b266ce790ed72f6aedfced775e3cf6f7bf8b7f7737b5b59c35f316af"),
        CELL,Map.of("renderTranslucentFloorObjects"+FLOORS,"3af2778bc5c1f87f093a605061b986404d43c6384a28122083c2634807fc767a"),
        "pzopt/TileRecord",Map.of("startFrame"+START,"f825aaa9151be8e66e7965a82cb9619839b4d2cec09fd8d010b89fdc0734a650"),
        "zombie/core/opengl/ShaderUnit",Map.of("compile()Z","3710bc72360b6f9d6ab1c940ec32b59c20dd04fd9c396839da6f28ff7b4577eb"));
    private ShoreReceiverRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        Map<String,String> pins=PINS.get(name);if(pins==null)return bytes;
        ClassReader reader=new ClassReader(bytes);if(!reader.getClassName().equals(name))throw new IOException("Shore receiver class identity: "+name);
        Map<String,String> actual=Compatibility.methods(bytes);
        for(var pin:pins.entrySet())if(!RepairProfiles.matches(pin.getValue(),actual.get(pin.getKey())))throw new IOException("Unsupported shore receiver boundary: "+name+"."+pin.getKey());
        int[] collision={0};reader.accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){if(n.startsWith(PREFIX))collision[0]++;return null;}
        },ClassReader.SKIP_CODE);
        if(collision[0]!=0)throw new IOException("Shore receiver adapter already present: "+name);
        int[] hits=new int[7];ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                boolean floors=name.equals(CELL)&&n.equals("renderTranslucentFloorObjects")&&d.equals(FLOORS);
                boolean draw=name.equals(SHADOW+"$Gl")&&n.equals("draw")&&d.equals(DRAW);
                if(floors||draw){
                    hits[0]++;wrapper(super.visitMethod(a,n,d,s,e),name,d,floors);
                    a=(a&~(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PRIVATE;
                    n=PREFIX+(floors?"Floors":"Draw");
                }
                MethodVisitor base=super.visitMethod(a,n,d,s,e);
                final String method=n,descriptor=d;
                return new MethodVisitor(Opcodes.ASM9,base){
                    int silhouettes;
                    @Override public void visitCode(){
                        super.visitCode();
                        if(name.equals(SHADOW)&&method.equals("queue")&&descriptor.equals("(I)V")){
                            super.visitInsn(Opcodes.ACONST_NULL);super.visitFieldInsn(Opcodes.PUTSTATIC,SHADOW,"deferred","L"+FRAME+";");
                            super.visitVarInsn(Opcodes.ILOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"preparePlayerFrame","(I)V",false);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"startFrame","()V",false);hits[1]++;
                        }
                        if(name.equals("pzopt/TileRecord")&&method.equals("startFrame")&&descriptor.equals(START)){
                            super.visitVarInsn(Opcodes.ALOAD,2);super.visitVarInsn(Opcodes.ILOAD,3);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"prepareFrame","(Ljava/util/List;I)V",false);hits[1]++;
                        }
                    }
                    @Override public void visitFieldInsn(int op,String owner,String field,String desc){
                        if(name.equals(SHADOW)&&method.equals("<clinit>")&&op==Opcodes.PUTSTATIC&&owner.equals(SHADOW)&&Set.of("FRAG","SIL_FRAG","LIGHT_FRAG").contains(field)){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,SHADERS,"shadow","(Ljava/lang/String;)Ljava/lang/String;",false);hits[2]++;
                        }
                        super.visitFieldInsn(op,owner,field,desc);
                        if(name.equals(SHADOW)&&method.equals("queue")&&op==Opcodes.GETFIELD&&owner.equals(FRAME)&&field.equals("silhouette")){
                            if(++silhouettes==2){super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"needsLateReceiver","()Z",false);super.visitInsn(Opcodes.IOR);hits[3]++;}
                        }
                        if(name.equals(SHADOW)&&method.equals("afterMoving")&&op==Opcodes.GETSTATIC&&owner.equals("pzopt/Config")&&field.equals("SUN_SHADOW_PASS_LATE")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"needsLateReceiver","()Z",false);super.visitInsn(Opcodes.IOR);hits[4]++;
                        }
                    }
                    @Override public void visitMethodInsn(int op,String owner,String called,String desc,boolean itf){
                        if(name.equals("pzopt/TileRecord")&&method.equals("startFrame")&&op==Opcodes.INVOKEVIRTUAL&&owner.equals(CELL)&&called.equals("pzoptTranslucentFloorWork")&&desc.equals("(Lzombie/iso/IsoChunk;II)Z")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,name,PREFIX+"FloorWork",WORK,false);hits[5]++;return;
                        }
                        boolean program=name.equals(SHADOW+"$Gl")&&method.equals(PREFIX+"Draw")&&owner.equals("org/lwjgl/opengl/GL20")&&called.equals("glUseProgram")&&desc.equals("(I)V");
                        if(program)super.visitInsn(Opcodes.DUP);
                        if(name.equals("zombie/core/opengl/ShaderUnit")&&owner.equals("org/lwjgl/opengl/GL20")&&called.equals("glShaderSource")&&desc.equals("(ILjava/lang/CharSequence;)V")){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETFIELD,name,"fileName","Ljava/lang/String;");
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,SHADERS,"tile","(Ljava/lang/CharSequence;Ljava/lang/String;)Ljava/lang/CharSequence;",false);hits[6]++;
                        }
                        super.visitMethodInsn(op,owner,called,desc,itf);
                        if(program){super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"shadowProgram","(I)V",false);hits[6]++;}
                    }
                };
            }
            @Override public void visitEnd(){
                if(name.equals("pzopt/TileRecord")){
                    MethodVisitor m=super.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC,PREFIX+"FloorWork",WORK,null,null);m.visitCode();
                    m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ILOAD,2);m.visitVarInsn(Opcodes.ILOAD,3);
                    m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,CELL,"pzoptTranslucentFloorWork","(Lzombie/iso/IsoChunk;II)Z",false);
                    m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ILOAD,2);m.visitVarInsn(Opcodes.ILOAD,3);
                    m.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"allowRecordedFloor","(Lzombie/iso/IsoChunk;II)Z",false);m.visitInsn(Opcodes.IAND);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();
                }
                super.visitEnd();
            }
        },0);
        boolean valid=switch(name){
            case SHADOW -> hits[1]==1&&hits[2]==3&&hits[3]==1&&hits[4]==1;
            case SHADOW+"$Gl" -> hits[0]==1&&hits[6]==4;
            case CELL -> hits[0]==1;
            case "pzopt/TileRecord" -> hits[1]==1&&hits[5]==1;
            default -> hits[6]==1;
        };
        if(!valid)throw new IOException("Unsupported shore receiver shape: "+name+" "+Arrays.toString(hits));return writer.toByteArray();
    }
    private static void wrapper(MethodVisitor m,String owner,String desc,boolean floors){
        int exception=floors?7:2;Label start=new Label(),end=new Label(),handler=new Label();
        m.visitCode();m.visitTryCatchBlock(start,end,handler,"java/lang/Throwable");m.visitLabel(start);
        if(floors)m.visitVarInsn(Opcodes.ILOAD,2);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,floors?"startPass":"shadowBegin",floors?"(I)V":"()V",false);
        m.visitVarInsn(Opcodes.ALOAD,0);
        if(floors){m.visitVarInsn(Opcodes.ILOAD,1);m.visitVarInsn(Opcodes.ILOAD,2);m.visitVarInsn(Opcodes.ALOAD,3);m.visitVarInsn(Opcodes.ALOAD,4);m.visitVarInsn(Opcodes.LLOAD,5);}
        else m.visitVarInsn(Opcodes.ALOAD,1);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,owner,PREFIX+(floors?"Floors":"Draw"),desc,false);m.visitLabel(end);
        cleanup(m,floors);m.visitInsn(Opcodes.RETURN);m.visitLabel(handler);m.visitFrame(Opcodes.F_SAME1,0,null,1,new Object[]{"java/lang/Throwable"});
        m.visitVarInsn(Opcodes.ASTORE,exception);cleanup(m,floors);m.visitVarInsn(Opcodes.ALOAD,exception);m.visitInsn(Opcodes.ATHROW);m.visitMaxs(0,0);m.visitEnd();
    }
    private static void cleanup(MethodVisitor m,boolean floors){if(floors)m.visitVarInsn(Opcodes.ILOAD,2);m.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,floors?"endPass":"shadowEnd",floors?"(I)V":"()V",false);}
}
