package antikristianos.pzoptpreloader;

import java.util.*;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.jar.asm.commons.*;

/** Keeps installed shared scene-data routines, never the excluded lighting/shader algorithms. */
final class SharedSceneSlices {
    static final String ROOT="pzopt/GodRays",GL=ROOT+"$Gl",FRAME=ROOT+"$Frame",UPLOAD=ROOT+"$Upload";
    private static final Set<String> ROOT_ENTRIES=Set.of("occupancyOnly","chunkChanged","outsideCovered","aperturesOf","occCpu","openDoorway","occZ0","occScanComplete");
    private static final Set<String> GL_ENTRIES=Set.of("ensureOcc","uploadOcc","uploadBuf");
    private SharedSceneSlices(){}
    static Set<String> methods(InputCode code){
        Set<String> names=code.name.equals(ROOT)?ROOT_ENTRIES:code.name.equals(GL)?GL_ENTRIES:Set.of();
        Set<String> keep=new HashSet<>();Deque<String> pending=new ArrayDeque<>();
        for(var m:code.methods.values())if(names.contains(m.name)||((code.name.equals(FRAME)||code.name.equals(UPLOAD))&&m.name.equals("<init>"))){keep.add(m.key());pending.add(m.key());}
        if((code.name.equals(ROOT)||code.name.equals(GL))&&names.stream().anyMatch(n->code.methods.values().stream().noneMatch(m->m.name.equals(n))))
            throw new IllegalStateException("Installed shared scene-data API changed: "+code.name);
        while(!pending.isEmpty()){
            InputCode.Method method=code.methods.get(pending.remove());
            for(var i:method.instructions){
                if(i.kind.equals("ldc")&&i.arguments[0] instanceof String text&&(text.contains("#version")||text.contains("void main(")||text.contains("uniform sampler")))
                    throw new IllegalStateException("Lighting shader cannot enter shared scene-data slice");
                if(i.kind.equals("method")&&i.arguments[0].equals(code.name))addMethod(code,keep,pending,(String)i.arguments[1]+i.arguments[2]);
                if(i.kind.equals("indy"))for(Object arg:(Object[])i.arguments[3])if(arg instanceof Handle h&&h.getOwner().equals(code.name))addMethod(code,keep,pending,h.getName()+h.getDesc());
            }
        }
        return keep;
    }
    private static void addMethod(InputCode code,Set<String> keep,Deque<String> pending,String key){
        if(!code.methods.containsKey(key))throw new IllegalStateException("Missing shared input method: "+key);
        if(keep.add(key))pending.add(key);
    }
    private static Set<String> initializedFields(InputCode code,Set<String> methods){
        Set<String> needed=new HashSet<>();
        for(String key:methods)for(var i:code.methods.get(key).instructions)if(i.kind.equals("field")&&i.arguments[0].equals(code.name))needed.add((String)i.arguments[1]);
        if(code.name.equals(ROOT))needed.addAll(Set.of("light","slotData","levelTops"));
        if(code.name.equals(GL))needed.add("occZ0Now");
        // Initializers may read another field; close over those dependencies before copying any block.
        InputCode.Method clinit=code.methods.get("<clinit>()V");
        if(clinit!=null){boolean added;do{added=false;for(List<InputCode.Instruction> block:initializerBlocks(code)){
            var last=block.getLast();if(!needed.contains(last.arguments[1]))continue;
            for(var i:block)if(i.kind.equals("field")&&i.arguments[0].equals(code.name)&&i.opcode==Opcodes.GETSTATIC)added|=needed.add((String)i.arguments[1]);
        }}while(added);}
        return needed;
    }
    private static List<List<InputCode.Instruction>> initializerBlocks(InputCode code){
        List<List<InputCode.Instruction>> blocks=new ArrayList<>();InputCode.Method method=code.methods.get("<clinit>()V");
        if(method==null)return blocks;int start=0;
        for(int i=0;i<method.instructions.size();i++){
            var instruction=method.instructions.get(i);
            if(instruction.kind.equals("field")&&instruction.opcode==Opcodes.PUTSTATIC&&instruction.arguments[0].equals(code.name)){
                blocks.add(method.instructions.subList(start,i+1));start=i+1;
            }
        }return blocks;
    }
    static byte[] adapter(byte[] source,Remapper names,boolean modern){
        InputCode code=new InputCode(source);Set<String> keep=methods(code),needed=initializedFields(code,keep);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor remapped=new ClassRemapper(writer,names);
        new ClassReader(source).accept(new ClassVisitor(Opcodes.ASM9,remapped){
            @Override public void visit(int v,int a,String n,String s,String p,String[] itfs){
                super.visit(v,(a|Opcodes.ACC_PUBLIC)&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED|Opcodes.ACC_ABSTRACT),n,s,p,itfs);
            }
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object value){
                // Excluded constant shader fields become empty declarations. Never retain their values.
                return super.visitField((a|Opcodes.ACC_PUBLIC)&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED),n,d,s,null);
            }
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] ex){
                int access=(a|Opcodes.ACC_PUBLIC)&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT);
                MethodVisitor out=super.visitMethod(access,n,d,s,ex);
                if(n.equals("<clinit>")){
                    out.visitCode();
                    for(var block:initializerBlocks(code))if(needed.contains(block.getLast().arguments[1])){
                        for(var i:block){
                            if(i.kind.equals("jump")||i.kind.equals("table")||i.kind.equals("lookup"))throw new IllegalStateException("Changed shared scene-data initializer requires review");
                            if(i.kind.equals("ldc")&&i.arguments[0] instanceof String value&&(value.contains("#version")||value.contains("void main(")||value.contains("uniform sampler")))throw new IllegalStateException("Lighting shader initializer cannot enter shared scene-data slice");
                            i.emit(out,new Label[0]);
                        }
                    }
                    out.visitInsn(Opcodes.RETURN);out.visitMaxs(0,0);out.visitEnd();return null;
                }
                if(keep.contains(n+d))return sharedOptions(out,modern);
                out.visitCode();
                if(code.name.equals(ROOT)&&n.equals("queue")&&d.equals("(I)V"))queue(out);
                else if(code.name.equals(ROOT)&&n.equals("lightNow")&&d.equals("()V"))light(out);
                else if(code.name.equals(GL)&&n.equals("frame")&&d.equals("(L"+FRAME+";)V"))uploadFrame(out);
                else if(code.name.equals(GL)&&n.equals("computeProgram")&&d.equals("(Ljava/lang/String;Ljava/lang/String;)I")){
                    out.visitVarInsn(Opcodes.ALOAD,0);out.visitVarInsn(Opcodes.ALOAD,1);
                    out.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/VisualExclusions","computeProgram",d,false);out.visitInsn(Opcodes.IRETURN);
                }else if(code.name.equals(FRAME)&&n.equals("render")&&d.equals("()V")){
                    out.visitVarInsn(Opcodes.ALOAD,0);out.visitMethodInsn(Opcodes.INVOKESTATIC,GL,"frame","(L"+FRAME+";)V",false);
                    out.visitVarInsn(Opcodes.ALOAD,0);out.visitFieldInsn(Opcodes.GETFIELD,FRAME,"uploads","Ljava/util/ArrayList;");out.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/ArrayList","clear","()V",false);out.visitInsn(Opcodes.RETURN);
                }else neutral(out,code.parent,n,d,(a&Opcodes.ACC_STATIC)!=0);
                out.visitMaxs(0,0);out.visitEnd();return null;
            }
        },0);
        return writer.toByteArray();
    }
    private static MethodVisitor sharedOptions(MethodVisitor out,boolean modern){
        return new MethodVisitor(Opcodes.ASM9,out){
            @Override public void visitFieldInsn(int opcode,String owner,String name,String descriptor){
                if(opcode==Opcodes.GETSTATIC&&owner.equals("pzopt/Config")&&(name.startsWith("GOD_RAYS")||name.startsWith("DEV_GOD_RAYS"))){
                    // Fixed data-maintenance policy, with no excluded effect options or render path.
                    switch(name){
                        case "GOD_RAYS_DOOR_GLASS","GOD_RAYS_DOOR_WATCH","GOD_RAYS_ROOF_RULE" -> super.visitInsn(Opcodes.ICONST_1);
                        case "GOD_RAYS","GOD_RAYS_ROOF_EXACT","DEV_GOD_RAYS_AP_LOG" -> super.visitInsn(Opcodes.ICONST_0);
                        case "GOD_RAYS_CHUNK_BUDGET_US" -> super.visitIntInsn(Opcodes.SIPUSH,400);
                        default -> throw new IllegalStateException("Unreviewed lighting option in shared scene-data slice: "+name);
                    }return;
                }
                super.visitFieldInsn(opcode,owner,name,modern?descriptor.replace("Lzombie/util/list/PZArrayList;","Ljava/util/List;"):descriptor);
            }
            @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf){
                if(modern){
                    descriptor=descriptor.replace("Lzombie/util/list/PZArrayList;","Ljava/util/List;");
                    if(owner.equals("zombie/util/list/PZArrayList")){
                        if(name.equals("<init>")||name.equals("getElements"))throw new IllegalStateException("Unreviewed shared collection operation");
                        owner="java/util/List";opcode=Opcodes.INVOKEINTERFACE;itf=true;
                    }
                }
                super.visitMethodInsn(opcode,owner,name,descriptor,itf);
            }
            @Override public void visitTypeInsn(int opcode,String type){super.visitTypeInsn(opcode,modern&&type.equals("zombie/util/list/PZArrayList")?"java/util/List":type);}
            @Override public void visitFrame(int type,int localCount,Object[] locals,int stackCount,Object[] stack){
                if(modern){locals=replaceTypes(locals);stack=replaceTypes(stack);}super.visitFrame(type,localCount,locals,stackCount,stack);
            }
        };
    }
    private static Object[] replaceTypes(Object[] values){if(values==null)return null;Object[] copy=values.clone();for(int i=0;i<copy.length;i++)if("zombie/util/list/PZArrayList".equals(copy[i]))copy[i]="java/util/List";return copy;}
    private static void queue(MethodVisitor out){
        Label done=new Label();out.visitMethodInsn(Opcodes.INVOKESTATIC,"pzopt/EntityShadow","wantsOccupancy","()Z",false);out.visitJumpInsn(Opcodes.IFEQ,done);
        out.visitVarInsn(Opcodes.ILOAD,0);out.visitMethodInsn(Opcodes.INVOKESTATIC,ROOT,"occupancyOnly","(I)V",false);
        out.visitLabel(done);out.visitFrame(Opcodes.F_SAME,0,null,0,null);out.visitInsn(Opcodes.RETURN);
    }
    private static void light(MethodVisitor out){
        out.visitFieldInsn(Opcodes.GETSTATIC,"pzopt/Config","DEV_SUN_HOUR","F");out.visitMethodInsn(Opcodes.INVOKESTATIC,"pzopt/Sky","update","(F)V",false);
        Label moon=new Label(),ready=new Label();out.visitFieldInsn(Opcodes.GETSTATIC,"pzopt/Sky","sunElevDeg","D");out.visitInsn(Opcodes.DCONST_0);out.visitInsn(Opcodes.DCMPL);out.visitJumpInsn(Opcodes.IFLT,moon);
        out.visitFieldInsn(Opcodes.GETSTATIC,"pzopt/Sky","sun","[D");out.visitJumpInsn(Opcodes.GOTO,ready);
        out.visitLabel(moon);out.visitFrame(Opcodes.F_SAME,0,null,0,null);out.visitFieldInsn(Opcodes.GETSTATIC,"pzopt/Sky","moon","[D");
        out.visitLabel(ready);out.visitFrame(Opcodes.F_SAME1,0,null,1,new Object[]{"[D"});out.visitVarInsn(Opcodes.ASTORE,0);
        for(int i=0;i<3;i++){out.visitFieldInsn(Opcodes.GETSTATIC,ROOT,"light","[F");out.visitInsn(Opcodes.ICONST_0+i);out.visitVarInsn(Opcodes.ALOAD,0);out.visitInsn(Opcodes.ICONST_0+i);out.visitInsn(Opcodes.DALOAD);out.visitInsn(Opcodes.D2F);out.visitInsn(Opcodes.FASTORE);}
        out.visitInsn(Opcodes.RETURN);
    }
    private static void uploadFrame(MethodVisitor out){
        Label done=new Label();out.visitMethodInsn(Opcodes.INVOKESTATIC,GL,"ensureOcc","()Z",false);out.visitJumpInsn(Opcodes.IFEQ,done);
        out.visitVarInsn(Opcodes.ALOAD,0);out.visitMethodInsn(Opcodes.INVOKESTATIC,GL,"uploadOcc","(L"+FRAME+";)V",false);
        out.visitVarInsn(Opcodes.ALOAD,0);out.visitFieldInsn(Opcodes.GETFIELD,FRAME,"occZ0","I");out.visitFieldInsn(Opcodes.PUTSTATIC,GL,"occZ0Now","I");
        out.visitVarInsn(Opcodes.ALOAD,0);out.visitFieldInsn(Opcodes.GETFIELD,FRAME,"maxTop","I");out.visitInsn(Opcodes.I2F);out.visitFieldInsn(Opcodes.PUTSTATIC,GL,"occMaxTop","F");
        out.visitFieldInsn(Opcodes.GETSTATIC,GL,"occOnlyFrames","J");out.visitInsn(Opcodes.LCONST_1);out.visitInsn(Opcodes.LADD);out.visitFieldInsn(Opcodes.PUTSTATIC,GL,"occOnlyFrames","J");
        out.visitFieldInsn(Opcodes.GETSTATIC,GL,"occUploads","J");out.visitVarInsn(Opcodes.ALOAD,0);out.visitFieldInsn(Opcodes.GETFIELD,FRAME,"uploads","Ljava/util/ArrayList;");out.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/ArrayList","size","()I",false);out.visitInsn(Opcodes.I2L);out.visitInsn(Opcodes.LADD);out.visitFieldInsn(Opcodes.PUTSTATIC,GL,"occUploads","J");
        out.visitLabel(done);out.visitFrame(Opcodes.F_SAME,0,null,0,null);out.visitInsn(Opcodes.RETURN);
    }
    static void neutral(MethodVisitor out,String parent,String name,String descriptor,boolean isStatic){
        if(name.equals("<init>")){
            out.visitVarInsn(Opcodes.ALOAD,0);out.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);out.visitInsn(Opcodes.RETURN);return;
        }
        Type result=Type.getReturnType(descriptor);Type[] args=Type.getArgumentTypes(descriptor);int local=isStatic?0:1;
        if((name.equals("patchShader")||name.equals("patchChunk"))&&result.equals(Type.getType(String.class))){
            // Upstream passes (fileName, shaderCode): retain the CODE, not the filename.
            if(args.length<2||!args[1].equals(result))throw new IllegalStateException("Changed excluded shader adapter signature");
            out.visitVarInsn(Opcodes.ALOAD,local+args[0].getSize());out.visitInsn(Opcodes.ARETURN);return;
        }
        if(name.equals("remap")&&result.equals(Type.INT_TYPE)&&args.length>0&&args[0].equals(result)){
            out.visitVarInsn(Opcodes.ILOAD,local);out.visitInsn(Opcodes.IRETURN);return;
        }
        if(name.equals("forceAmbient")&&descriptor.endsWith("F)F")){
            for(int i=0;i<args.length-1;i++)local+=args[i].getSize();out.visitVarInsn(Opcodes.FLOAD,local);out.visitInsn(Opcodes.FRETURN);return;
        }
        if(result.equals(Type.getType(String.class))){out.visitLdcInsn("");out.visitInsn(Opcodes.ARETURN);return;}
        int sort=result.getSort();if(sort==Type.VOID){out.visitInsn(Opcodes.RETURN);return;}
        out.visitInsn(sort==Type.LONG?Opcodes.LCONST_0:sort==Type.DOUBLE?Opcodes.DCONST_0:sort==Type.FLOAT?Opcodes.FCONST_0:sort>=Type.ARRAY?Opcodes.ACONST_NULL:Opcodes.ICONST_0);
        out.visitInsn(result.getOpcode(Opcodes.IRETURN));
    }
}