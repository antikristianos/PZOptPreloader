package antikristianos.pzoptpreloader;

import org.joml.Matrix4f;
import zombie.core.opengl.ShaderProgram;

/** Restore the native matrix represented by ShaderProgram's cache before a shared CPU puddle draw can reuse it. */
public final class PuddleMatrixState {
    private static final Matrix4f BASE=new Matrix4f(); // render thread, reused across cached puddle draws
    private static float jiggleX,jiggleY;
    private PuddleMatrixState() { }

    public static Matrix4f position(Matrix4f matrix,float x,float y,float z){
        jiggleX=x;jiggleY=y;return matrix;
    }
    public static void jiggle(ShaderProgram program){program.setVector2("pzNativePuddleJiggle",jiggleX,jiggleY);}

    public static void restore(ShaderProgram program){
        if(program==null||RuntimeState.server||!Compatibility.currentGame())return;
        // PuddleVbo overrides only the GPU uniform with its translated matrix. The native metadata still describes BASE.
        // A later native setModelViewProjection may therefore skip its upload; make that cache hit truthful again.
        BASE.set(program.projection).mul(program.modelView);
        program.setValue("ModelViewProjection",BASE);
        program.setVector2("pzNativePuddleJiggle",0,0);
    }
}
