package antikristianos.pzpeek42;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Set;
import net.bytebuddy.jar.asm.*;

/** Narrow call-site optimization of the existing wall algorithm; no copied dependency body. */
public final class PerformanceTransforms {
    private static final String GRID="pzmod/peekaview/Patch_IsoCell$Patch_GetSquaresAroundPlayerSquare";
    private static final String TREE="zombie/iso/objects/IsoTree";
    public static volatile int gridCalls,crownCalls;
    private static volatile String failure;
    public static void prepare()throws Exception{
        TargetPaths.verify();verifyShade();DrawGeometry.initialize();
    }
    public static void install(Instrumentation instrumentation)throws Exception{
        prepare();
        instrumentation.addTransformer(new ClassFileTransformer(){
            @Override public byte[] transform(ClassLoader loader,String name,Class<?> cls,ProtectionDomain domain,byte[] bytes){
                if(!GRID.equals(name) && !TREE.equals(name) && !DrawPaths.OWNER.equals(name))return null;
                try{return GRID.equals(name)?grid(bytes):TREE.equals(name)?tree(bytes):drawing(bytes);}
                catch(RuntimeException error){failure=error.toString();Main.active=false;Main.status="inactive: performance path changed: "+error;System.err.println("[PZOptPreloader/PeekAView] "+Main.status);return null;}
            }
        },true);
        instrumentation.retransformClasses(Class.forName(GRID.replace('/','.')),zombie.iso.objects.IsoTree.class,zombie.iso.fboRenderChunk.FBORenderTrees.class);
        if(failure!=null || gridCalls==0 || crownCalls!=2)throw new IllegalStateException("Performance integration failed: "+failure);
    }
    static byte[] drawing(byte[] input){
        DrawPaths.verify(input);
        ClassReader reader=new ClassReader(input);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        int[] appends={0},depth={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] errors){
                return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,name,desc,signature,errors)){
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String type,boolean itf){
                        if(name.equals("pzoptShadedQuad") && desc.equals(DrawPaths.QUAD) && owner.equals("zombie/core/opengl/VBORenderer")
                                && method.equals("addElement") && type.equals("(FFFFFFFFF)V")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzpeek42/DrawGeometry","append","(Lzombie/core/opengl/VBORenderer;FFFFFFFFF)V",false);appends[0]++;
                        }else if(name.equals("renderTexture") && owner.equals(DrawPaths.OWNER) && method.equals("calculateDepth") && type.equals(DrawPaths.DEPTH)){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzpeek42/DrawGeometry","depth","(L"+DrawPaths.OWNER+";Ljava/lang/Object;ZZZ)F",false);depth[0]++;
                        }else super.visitMethodInsn(opcode,owner,method,type,itf);
                    }
                };
            }
        },0);
        if(appends[0]!=4 || depth[0]!=1)throw new IllegalStateException("tree vertex/depth call sites changed");
        return writer.toByteArray();
    }
    static byte[] grid(byte[] input){
        ClassReader reader=new ClassReader(input);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        int[] calls={0},scopes={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
                boolean scope=name.equals("enter") && desc.equals("(Lzombie/iso/IsoCell;Lzombie/characters/IsoPlayer;Lzombie/iso/IsoGridSquare;Ljava/util/ArrayList;Ljava/util/ArrayList;)Z");
                if(scope)scopes[0]++;
                return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,name,desc,sig,exceptions)){
                    @Override public void visitCode(){
                        super.visitCode();
                        if(scope){super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzpeek42/GridCache","begin","(Lzombie/iso/IsoCell;)V",false);}
                    }
                    @Override public void visitInsn(int opcode){
                        if(scope && opcode==Opcodes.IRETURN)super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzpeek42/GridCache","end","()V",false);
                        super.visitInsn(opcode);
                    }
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                        if(!scope && opcode==Opcodes.INVOKEVIRTUAL && owner.equals("zombie/iso/IsoCell") && method.equals("getGridSquare")
                                && descriptor.equals("(III)Lzombie/iso/IsoGridSquare;")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzpeek42/GridCache","get","(Lzombie/iso/IsoCell;III)Lzombie/iso/IsoGridSquare;",false);calls[0]++;
                        }else super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                    }
                };
            }
        },0);
        if(calls[0]==0 || scopes[0]!=1)throw new IllegalStateException("missing wall entry/lookup signatures");
        gridCalls=calls[0];return writer.toByteArray();
    }

    static byte[] tree(byte[] input){
        ClassReader reader=new ClassReader(input);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        int[] crowns={0},ordinary={0},methods={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
                MethodVisitor target=super.visitMethod(access,name,desc,sig,exceptions);
                if(!name.equals("render") || !desc.equals("(FFFLzombie/core/textures/ColorInfo;ZIZ)V"))return target;
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM9,target){
                    boolean material;
                    @Override public void visitVarInsn(int opcode,int variable){if(!material)super.visitVarInsn(opcode,variable);}
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                        if(material){
                            if(owner.equals("zombie/iso/sprite/IsoSprite") && method.equals("getTextureForCurrentFrame"))material=false;
                            else if(!method.equals("getSprite") && !method.equals("getDir"))throw new IllegalStateException("jumbo material sequence changed");
                            return;
                        }
                        if(owner.equals("zombie/iso/IsoTreeJumbo$TreeDescription") && method.equals("treetop") && descriptor.equals("()Ljava/lang/String;")){
                            // Stack already contains manager + description. The helper resolves direction only for a visible crown.
                            super.visitVarInsn(Opcodes.ALOAD,0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzpeek42/TreeDrawing","crownTexture",
                                "(Lzombie/iso/sprite/IsoSpriteManager;Lzombie/iso/IsoTreeJumbo$TreeDescription;Lzombie/iso/objects/IsoTree;)Lzombie/core/textures/Texture;",false);
                            material=true;crowns[0]++;return;
                        }
                        if(owner.equals(TREE) && method.equals("getSprite") && descriptor.equals("()Lzombie/iso/sprite/IsoSprite;")){
                            // Native fade/cutaway animation has completed at this point. Preserve it, omit only drawing.
                            Label visible=new Label();
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzpeek42/TreeDrawing","hiddenOrdinary","(Lzombie/iso/objects/IsoTree;)Z",false);
                            super.visitJumpInsn(Opcodes.IFEQ,visible);
                            super.visitInsn(Opcodes.POP);super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(visible);ordinary[0]++;
                        }
                        super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                    }
                };
            }
        },ClassReader.SKIP_FRAMES);
        if(methods[0]!=1 || crowns[0]!=2 || ordinary[0]!=1)throw new IllegalStateException("native tree material paths changed");
        crownCalls=crowns[0];return writer.toByteArray();
    }

    static void verifyShade()throws Exception{
        try(var input=ClassLoader.getSystemResourceAsStream("pzopt/TreeShade.class")){
            if(input==null)throw new IllegalStateException("TreeShade input unavailable");
            verifyShade(input.readAllBytes());
        }
    }
    static void verifyShade(byte[] bytes){
        Set<String> expected=Set.of("pzopt/Config.SUN_SHADOWS:Z","pzopt/Config.SUN_SHADOW_TREES:Z",
            "pzopt/Config.SUN_SHADOW_CANOPY_PCT:I","pzopt/Config.AO:Z","pzopt/Config.AO_TREE_CANOPY_PCT:I",
            "pzopt/Config.AO_STRENGTH_VEGETATION_PCT:I","pzopt/SunShadow.world:[F","pzopt/SunShadow.dir:[F");
        Set<String> methods=Set.of("shade(FFFF)F","sunOn()Z","skyOn()Z","chord(FFFFFFFF)F");
        Set<String> actual=new java.util.HashSet<>(),arrays=new java.util.HashSet<>(),seen=new java.util.HashSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
                if(!methods.contains(name+desc))return null;
                seen.add(name+desc);
                return new MethodVisitor(Opcodes.ASM9){
                    final java.util.Map<Integer,String> locals=new java.util.HashMap<>();
                    String array;
                    Integer index;
                    @Override public void visitFieldInsn(int opcode,String owner,String field,String descriptor){
                        if(opcode==Opcodes.GETFIELD)throw new IllegalStateException("TreeShade new instance dependency");
                        if(opcode==Opcodes.GETSTATIC)actual.add(owner+"."+field+":"+descriptor);
                        array=owner.equals("pzopt/SunShadow")&&descriptor.equals("[F")?field:null;index=null;
                    }
                    @Override public void visitVarInsn(int opcode,int variable){
                        if(opcode==Opcodes.ASTORE){if(array!=null)locals.put(variable,array);array=null;}
                        else if(opcode==Opcodes.ALOAD){array=locals.get(variable);index=null;}
                        else if(opcode==Opcodes.ILOAD)index=null;
                    }
                    @Override public void visitIntInsn(int opcode,int operand){index=operand;}
                    @Override public void visitInsn(int opcode){
                        if(opcode>=Opcodes.ICONST_0 && opcode<=Opcodes.ICONST_5)index=opcode-Opcodes.ICONST_0;
                        if(opcode==Opcodes.FALOAD){
                            if(array==null || index==null)throw new IllegalStateException("TreeShade unknown array input");
                            arrays.add(array+"["+index+"]");array=null;index=null;
                        }
                    }
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                        if(!owner.equals("java/lang/Math") && !(owner.equals("pzopt/TreeShade")&&methods.contains(method+descriptor)))
                            throw new IllegalStateException("TreeShade unknown helper dependency: "+owner+"."+method);
                    }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        if(!seen.equals(methods) || !actual.equals(expected) || !arrays.equals(Set.of("world[0]","world[1]","world[2]","dir[3]")))
            throw new IllegalStateException("TreeShade dependencies changed: "+actual+" "+arrays);
    }
}
