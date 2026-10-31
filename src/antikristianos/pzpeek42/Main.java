package antikristianos.pzpeek42;

import java.lang.instrument.*;
import java.lang.reflect.*;
import java.security.ProtectionDomain;
import java.util.*;
import me.zed_0xff.zombie_buddy.Exposer;
import net.bytebuddy.jar.asm.*;
import zombie.iso.fboRenderChunk.FBORenderCell;

public final class Main {
    public static final String CELL = "zombie/iso/fboRenderChunk/FBORenderCell";
    public static volatile boolean active;
    public static volatile boolean performanceReady;
    public static volatile int bridgedCalls, wrappedMethods, retainedCacheGates;
    public static volatile String status = "not initialized";
    private static volatile float visibility = .30f;
    private static boolean installed;
    private static boolean requested,attempted;
    private static volatile String bridgeFailure;
    private static boolean prepared;
    private static RenderPaths.Shape preparedShape;

    public static void setVisibility(double percent) {
        visibility = Double.isFinite(percent) ? (float)(Math.max(0, Math.min(100, Math.round(percent))) / 100.0) : .30f;
    }
    public static double getVisibility() { return Math.round(visibility * 100); }
    public static boolean isActive() { return active; }
    public static void resumeSelectedSession() {
        active=installed && performanceReady && bridgeFailure==null && status.startsWith("active;");
    }
    public static float opacity(float raw) {
        float progress = Math.max(0, Math.min(1, (raw - .05f) / .95f));
        return visibility + (1 - visibility) * progress;
    }
    public static synchronized void main(String[] args) {
        if (requested || installed) return;
        try {
            Class<?> preloader=Class.forName("antikristianos.pzoptpreloader.RuntimeState");
            if(preloader.getField("server").getBoolean(null)){status="client only";return;}
        } catch(Throwable error){status="inactive: "+error;System.out.println("[PZOptPreloader/PeekAView] "+status);return;}
        requested=true;
        status="waiting for Java mods; initialization at LuaManager.init";
        System.out.println("[PZOptPreloader/PeekAView] "+status);
    }
    public static synchronized void prepareAfterModsLoaded() throws Exception {
        if(prepared)return;
        Class<?> preloader=Class.forName("antikristianos.pzoptpreloader.RuntimeState");
        byte[] effective=(byte[])preloader.getMethod("getEffectiveClassBytes",String.class).invoke(null,CELL);
        preparedShape=RenderPaths.inspect(effective,false);
        TreeOpacity.initialize();TreeCache.initialize();FastPeek.initialize();WindowAccess.initialize();ShadeCache.initialize();
        PerformanceTransforms.prepare();prepared=true;
    }
    /** GameWindow calls this Lua lifecycle only after ZFS/Buddy finished loading every mod JAR. */
    public static synchronized void initializeAfterModsLoaded() {
        if (!requested || attempted || installed) return;
        attempted=true;
        try {
            Class<?> preloader = Class.forName("antikristianos.pzoptpreloader.RuntimeState");
            if (preloader.getField("server").getBoolean(null)) { status="client only"; return; }
            prepareAfterModsLoaded();
            RenderPaths.Shape expected=preparedShape;
            Field field = Class.forName("me.zed_0xff.zombie_buddy.Loader").getDeclaredField("g_instrumentation");
            field.setAccessible(true);
            Instrumentation instrumentation = (Instrumentation)field.get(null);
            instrumentation.addTransformer(new ClassFileTransformer() {
                @Override public byte[] transform(ClassLoader loader, String name, Class<?> cls, ProtectionDomain domain, byte[] bytes) {
                    if (!CELL.equals(name)) return null;
                    return bridgeTreeCache(bytes);
                }
            }, true);
            instrumentation.retransformClasses(FBORenderCell.class);
            PerformanceTransforms.install(instrumentation);
            performanceReady=true;
            if (bridgeFailure!=null)throw new IllegalStateException(bridgeFailure);
            if (bridgedCalls != expected.decisions() || wrappedMethods != expected.wrappers() || retainedCacheGates < expected.cacheGates())
                throw new IllegalStateException("tree-cache transformation did not complete");
            active = true; installed = true;
            status = "active; tree render paths verified, selective tree chunk caching retained, render-target recheck >="+(RenderTargets.INTERVAL_NANOS/1_000_000L)+"ms (unchanged answers retained), mod detection=event-driven, ordinary-tree visibility=" + getVisibility() + "%";
        } catch (Throwable error) { active=false;status="inactive: " + error; }
        System.out.println("[PZOptPreloader/PeekAView] " + status);
    }
    public static byte[] bridgeTreeCache(byte[] bytes) {
        try { return transformTreeCache(bytes); }
        catch(RuntimeException error) {
            active=false;bridgeFailure=error.getMessage();status="inactive: "+bridgeFailure;
            bridgedCalls=wrappedMethods=retainedCacheGates=0;
            System.err.println("[PZOptPreloader/PeekAView] "+status+"; renderer retained");return null;
        }
    }
    private static byte[] transformTreeCache(byte[] bytes) {
        RenderPaths.Shape original=RenderPaths.inspect(bytes,false);
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        String helper = "antikristianos/pzpeek42/TreeCache";
        String object = "Lzombie/iso/IsoObject;", tree = "Lzombie/iso/objects/IsoTree;", cell = "L"+CELL+";";
        Map<String,String> wrappers = RenderPaths.WRAPPERS;
        int[] count={0,0,0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                String wrapper = wrappers.get(name+desc);
                if (wrapper != null) count[1]++;
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access,name,desc,sig,exceptions)) {
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf) {
                        // Leave the public method's original predicate intact: PeekAView wraps that method.
                        if (owner.equals(CELL) && method.equals("pzoptIsTranslucentTree") && descriptor.equals("("+object+"Z)Z") && !name.equals("isTranslucentTree")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,helper,"decision","("+cell+object+"Z)Z",false);count[0]++;
                        } else if (owner.equals(CELL) && method.equals("isTranslucentTree") && descriptor.equals("("+object+")Z")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,helper,"decision","("+cell+object+")Z",false);count[0]++;
                        } else super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                    }
                    @Override public void visitInsn(int opcode) {
                        if (opcode==Opcodes.IRETURN && wrapper!=null) {
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);
                            String argument = name.startsWith("pzopt") ? tree : object;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,helper,wrapper,"(Z"+cell+argument+")Z",false);
                        }
                        super.visitInsn(opcode);
                    }
                    @Override public void visitFieldInsn(int opcode,String owner,String name,String desc) {
                        if (opcode==Opcodes.GETSTATIC && owner.equals("pzopt/Config") && name.equals("TREES_IN_CHUNK_TEXTURE") && desc.equals("Z")) {
                            count[2]++;
                        }
                        super.visitFieldInsn(opcode,owner,name,desc);
                    }
                };
            }
        },0);
        byte[] result=writer.toByteArray();
        RenderPaths.verifyPreserved(original,RenderPaths.inspect(result,true));
        bridgedCalls=count[0];wrappedMethods=count[1];retainedCacheGates=count[2];bridgeFailure=null;
        return result;
    }
}
