package antikristianos.pzpeek42;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.jar.asm.*;

/** Admit only pure geometry helpers whose complete input set is captured by RenderTargets. */
public final class TargetPaths {
    private static final String PEEK="pzmod/peekaview/PeekAViewMod";
    private static final String TREE="Lzombie/iso/objects/IsoTree;",SQUARE="Lzombie/iso/IsoGridSquare;";
    private static final Map<String,String> METHODS=Map.of(
        "inCharReveal","("+TREE+"Z)Z","inVehicleCover","("+TREE+"Z)Z","inRoadView","("+TREE+"Z)Z",
        "inAimReveal","("+TREE+"Z)Z","inSeCorridor","("+TREE+"ZI)Z","cameraPlayerDotTo","("+SQUARE+")F",
        "treeBoxMeetsBody","("+TREE+"FFI)Z","treeBoxMeetsEllipse","(FFFFF)Z");
    private static final Map<String,Set<String>> INPUTS=Map.of(
        "inCharReveal",Set.of("treeFadeRevealRadius","camPlayerSnapshotValid","camPlayerX","camPlayerY"),
        "inVehicleCover",Set.of("camPlayerSnapshotValid","camPlayerX","camPlayerY"),
        "inRoadView",Set.of("camPlayerSnapshotValid","roadValid","camPlayerX","camPlayerY","camPlayerFwdX","camPlayerFwdY",
            "roadLen","roadTx","roadTy","roadNx","roadNy","roadHalfW","treeBoxX0","treeBoxX1","treeBoxY0","treeBoxY1"),
        "inAimReveal",Set.of("aimPointX","aimPointY"),
        "inSeCorridor",Set.of("camPlayerSnapshotValid","camPlayerX","camPlayerY","treeBoxX0","treeBoxX1","treeBoxY0","treeBoxY1"),
        "cameraPlayerDotTo",Set.of("camPlayerSnapshotValid","camPlayerX","camPlayerY","camPlayerFwdX","camPlayerFwdY"),
        "treeBoxMeetsBody",Set.of(),"treeBoxMeetsEllipse",Set.of("treeBoxX0","treeBoxX1","treeBoxY0","treeBoxY1"));
    public static void verify()throws Exception{
        try(var stream=TargetPaths.class.getClassLoader().getResourceAsStream(PEEK+".class")){
            if(stream==null)throw new IllegalStateException("PeekAView target geometry unavailable");
            inspect(stream.readAllBytes());
        }
    }
    static void inspect(byte[] bytes){
        Set<String> seen=new HashSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] errors){
                if(!METHODS.containsKey(name))return null;
                if(!METHODS.get(name).equals(desc) || (access&Opcodes.ACC_STATIC)==0 || !seen.add(name))throw changed(name+desc);
                return new MethodVisitor(Opcodes.ASM9){
                    @Override public void visitFieldInsn(int opcode,String owner,String field,String type){
                        boolean known=opcode==Opcodes.GETSTATIC && ((owner.equals(PEEK) && INPUTS.get(name).contains(field))
                            || (owner.equals("zombie/core/Core") && field.equals("tileScale") && type.equals("I")))
                            || opcode==Opcodes.GETFIELD && ((Set.of("zombie/iso/objects/IsoTree","zombie/iso/IsoObject").contains(owner) && field.equals("square") && type.equals(SQUARE))
                                || owner.equals("zombie/iso/IsoGridSquare") && Set.of("x","y").contains(field) && type.equals("I"));
                        if(!known)throw changed(name+" input "+owner+"."+field+type);
                    }
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String type,boolean itf){
                        boolean known=opcode==Opcodes.INVOKESTATIC && !itf && ((owner.equals(PEEK)
                            && Set.of("treeBoxMeetsBody","treeBoxMeetsEllipse").contains(method) && type.equals(METHODS.get(method)))
                            || owner.equals("java/lang/Math") && method.equals("sqrt") && type.equals("(D)D"));
                        if(!known)throw changed(name+" call "+owner+"."+method+type);
                    }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        if(!seen.equals(METHODS.keySet()))throw changed("missing helper "+seen);
    }
    private static IllegalStateException changed(String detail){return new IllegalStateException("PeekAView target geometry changed: "+detail);}
}
