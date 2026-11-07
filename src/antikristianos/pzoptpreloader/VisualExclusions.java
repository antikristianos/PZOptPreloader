package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.jar.asm.commons.*;
import org.lwjgl.opengl.*;

/** Excludes the three user-selected visual implementations; adapters contain only our neutral API. */
public final class VisualExclusions {
    private static final Set<String> ROOTS = Set.of("pzopt/PixelLight", "pzopt/VoxelLight", "pzopt/GodRays");
    private static final Map<String,String> ALIASES = Map.of("pzopt/GodRays","pzopt/PreloaderSceneData", "pzopt/PixelLight","pzopt/PreloaderExcludedPixelLight", "pzopt/VoxelLight","pzopt/PreloaderExcludedVoxelLight");
    private static final String HELPER = "antikristianos/pzoptpreloader/VisualExclusions";
    private VisualExclusions() { }
    public static boolean excludedClass(String name) {
        return ROOTS.stream().anyMatch(root -> name.equals(root) || name.startsWith(root + "$"));
    }
    public static boolean excludedKey(String value) {
        String key = value.toLowerCase(Locale.ROOT);
        return key.startsWith("pixellight") || key.startsWith("voxellight") || key.startsWith("godrays")
            || key.startsWith("ppl") || key.startsWith("devppl") || key.startsWith("devvoxellight")
            || key.startsWith("devpixellight") || key.startsWith("devgodrays");
    }
    public static boolean excludedMedia(String path) {
        String name = path.toLowerCase(Locale.ROOT);
        return name.startsWith("media/ui/pzopt/compare/") && (name.contains("/voxel") || name.contains("/godrays") || name.contains("/pixel") || name.contains("/torch-"));
    }
    public static String mapped(String name) {
        for(var alias:ALIASES.entrySet())if(name.equals(alias.getKey())||name.startsWith(alias.getKey()+"$"))return alias.getValue()+name.substring(alias.getKey().length());
        return name;
    }
    private static final Remapper NAMES = new Remapper() {
        @Override public String map(String name) { return mapped(name); }
    };
    /** Installed data-only methods are sliced separately; excluded render/shader methods become own inert adapters. */
    public static byte[] adapter(byte[] source,boolean modern) { return SharedSceneSlices.adapter(source,NAMES,modern); }
    public static byte[] patch(String className, byte[] source) { return patch(className,source,false); }
    public static byte[] patch(String className, byte[] source,boolean modern) {
        if (excludedClass(className)) return adapter(source,modern);
        ClassReader reader = new ClassReader(source);
        // Fresh pool: removed type names must not survive as unused constants from the original pool.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassRemapper(writer, NAMES);
        if(className.equals("zombie/iso/LightingJNI$JNILighting"))visitor=new ClassVisitor(Opcodes.ASM9,visitor){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                MethodVisitor method=super.visitMethod(access,name,descriptor,signature,exceptions);
                if((name.equals("pzoptWhiten")||name.equals("pzoptUnwhiten"))&&descriptor.equals("()V")){
                    method.visitCode();method.visitInsn(Opcodes.RETURN);method.visitMaxs(0,0);method.visitEnd();return null;
                }
                if(name.equals("pzoptInfo")&&descriptor.equals("()Lzombie/core/textures/ColorInfo;")){
                    method.visitCode();method.visitVarInsn(Opcodes.ALOAD,0);
                    method.visitFieldInsn(Opcodes.GETFIELD,className,"lightInfo","Lzombie/core/textures/ColorInfo;");
                    method.visitInsn(Opcodes.ARETURN);method.visitMaxs(0,0);method.visitEnd();return null;
                }
                return method;
            }
        };
        if (className.equals("pzopt/Config")) visitor = new ClassVisitor(Opcodes.ASM9, visitor) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("register") || !descriptor.equals("(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) return method;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override public void visitCode() {
                        super.visitCode();
                        Label retained = new Label();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "excludedKey", "(Ljava/lang/String;)Z", false);
                        super.visitJumpInsn(Opcodes.IFEQ, retained);
                        super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "disabledValue", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                        super.visitInsn(Opcodes.ARETURN);
                        super.visitLabel(retained);
                        super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }
    public static Object disabledValue(Object value) {
        if (value instanceof Boolean) return Boolean.FALSE;
        if (value instanceof Integer) return 0;
        if (value instanceof Long) return 0L;
        if (value instanceof Float) return 0.0F;
        if (value instanceof Double) return 0.0D;
        if(value instanceof String text){
            try{Double.parseDouble(text);return "0";}catch(NumberFormatException ignored){return "";}
        }
        return value;
    }
    /** Generic shader compilation still used by unrelated enhancement helpers; no lighting algorithm. */
    public static int computeProgram(String source, String label) {
        if (!GL.getCapabilities().OpenGL43) return 0;
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER), program = 0;
        try {
            GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            program = GL20.glCreateProgram(); GL20.glAttachShader(program, shader); GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException error) {
            if (program != 0) GL20.glDeleteProgram(program);
            System.err.println("[PZOptPreloader] shared compute shader failed (" + label + "): " + error);
            return 0;
        } finally { GL20.glDeleteShader(shader); }
    }
    public static void prepareMedia(Payload payload, Path cache) throws IOException {
        payload.media.entrySet().removeIf(entry -> excludedMedia(entry.getKey()));
        String key = "media/lua/client/pzopt/pzopt_optimizations_options.lua";
        Path options = payload.media.get(key);
        if (options == null) return;
        Path target = cache.resolve("filtered-media").resolve(key);
        Files.createDirectories(target.getParent());
        String ownFilter;
        try (InputStream input = VisualExclusions.class.getResourceAsStream("removed-visuals.lua")) {
            if (input == null) throw new IOException("Own visual-exclusion filter missing");
            ownFilter = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Files.writeString(target, Files.readString(options) + "\n" + ownFilter);
        payload.media.put(key, target);
    }
}