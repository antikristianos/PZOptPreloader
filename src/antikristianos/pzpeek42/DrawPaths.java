package antikristianos.pzpeek42;

import java.util.Set;
import net.bytebuddy.jar.asm.*;

/** Admit the original append-only quad and depth inputs before reusing either output. */
public final class DrawPaths {
    static final String OWNER="zombie/iso/fboRenderChunk/FBORenderTrees",RECORD=OWNER+"$Tree";
    static final String QUAD="(Lzombie/core/opengl/VBORenderer;L"+RECORD+";Lzombie/core/textures/Texture;FFFFFFFFFFII)V";
    static final String DEPTH="(L"+RECORD+";ZZZ)F";
    static void verify(byte[] bytes){
        int[] seen={0,0},strips={0},vertices={0};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] errors){
                boolean quad=name.equals("pzoptShadedQuad") && desc.equals(QUAD),depth=name.equals("calculateDepth") && desc.equals(DEPTH);
                if(!quad && !depth)return null;seen[quad?0:1]++;
                return new MethodVisitor(Opcodes.ASM9){
                    @Override public void visitIntInsn(int opcode,int value){if(quad && opcode==Opcodes.BIPUSH && value==6)strips[0]++;}
                    @Override public void visitFieldInsn(int opcode,String owner,String field,String type){
                        boolean known=opcode==Opcodes.GETFIELD && (quad
                            ? owner.equals(RECORD) && Set.of("r","g","b","a").contains(field) && type.equals("F")
                            : owner.equals(OWNER) && Set.of("playerX","playerY").contains(field)
                              || owner.equals(RECORD) && Set.of("x","y","z","depthOffset").contains(field)
                              || owner.equals("zombie/iso/IsoDepthHelper$Results") && field.equals("depthStart")
                              || owner.equals("zombie/iso/fboRenderChunk/FBORenderChunkManager") && field.equals("renderThreadCurrent")
                              || owner.equals("zombie/iso/fboRenderChunk/FBORenderChunk") && field.equals("chunk")
                              || owner.equals("zombie/iso/IsoChunk") && Set.of("wx","wy").contains(field));
                        known|=depth && opcode==Opcodes.GETSTATIC && owner.equals("zombie/iso/fboRenderChunk/FBORenderChunkManager") && field.equals("instance");
                        if(!known)throw changed(name+" field "+owner+"."+field+type);
                    }
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String type,boolean itf){
                        boolean own=opcode==Opcodes.INVOKESTATIC && owner.equals("antikristianos/pzpeek42/DrawGeometry")
                            && Set.of("begin","end","append").contains(method);
                        boolean known=quad
                            ? owner.equals("pzopt/TreeShade") && Set.of("crownRadius(I)F","shade(FFFF)F").contains(method+type)
                              || owner.equals("zombie/core/textures/Texture") && Set.of("getXStart()F","getXEnd()F").contains(method+type)
                              || owner.equals("zombie/core/opengl/VBORenderer") && Set.of("reserve(I)V","addElement(FFFFFFFFF)V").contains(method+type)
                            : owner.equals("zombie/core/skinnedmodel/model/VertexBufferObject") && (method+type).equals("getDepthValueAt(FFF)F")
                              || owner.equals("zombie/core/math/PZMath") && (method+type).equals("fastfloor(F)I")
                              || owner.equals("zombie/iso/IsoDepthHelper") && Set.of("getSquareDepthData(IIFFF)Lzombie/iso/IsoDepthHelper$Results;","getChunkDepthData(IIIII)Lzombie/iso/IsoDepthHelper$Results;").contains(method+type)
                              || owner.equals("pzmod/peekaview/PeekAViewMod") && (method+type).equals("jumboLayerDepthOffset(Ljava/lang/Object;Z)F");
                        if(!known && !own)throw changed(name+" call "+owner+"."+method+type);
                        if(quad && (method.equals("addElement") || own && method.equals("append")))vertices[0]++;
                    }
                    @Override public void visitInvokeDynamicInsn(String method,String type,Handle bootstrap,Object... arguments){throw changed(name+" dynamic call");}
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        if(seen[0]!=1 || seen[1]!=1 || strips[0]!=1 || vertices[0]!=4)throw changed("missing quad/depth/six-strip path");
    }
    private static IllegalStateException changed(String detail){return new IllegalStateException("tree drawing inputs changed: "+detail);}
}
