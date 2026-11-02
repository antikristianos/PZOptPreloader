package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import net.bytebuddy.jar.asm.*;

/** Carries existing loose game-root classes into the managed JAR-only classpath. */
public final class InstalledClasspath {
    private static final Set<String> EXCLUDED_ROOTS=Set.of("media","mods","workshop","jre64","launcher","license",".git",".codex","pzopt");
    private InstalledClasspath() { }
    public static Path prepare(Path gameDirectory, Path cacheDirectory) throws Exception {
        Path game=gameDirectory.toAbsolutePath().normalize();
        boolean windows=System.getProperty("os.name","").startsWith("Windows");
        Map<String,byte[]> files=new TreeMap<>(); long[] size={0};
        Files.walkFileTree(game,new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes) {
                if(!path.equals(game)&&path.getParent().equals(game)
                    &&EXCLUDED_ROOTS.contains(path.getFileName().toString().toLowerCase(Locale.ROOT)))return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException {
                if(!path.getFileName().toString().endsWith(".class"))return FileVisitResult.CONTINUE;
                if(!attributes.isRegularFile()||attributes.size()>16*1024*1024)throw new IOException("Unsafe installed class: "+game.relativize(path));
                byte[] bytes=Files.readAllBytes(path); String relative=game.relativize(path).toString().replace('\\','/');
                ClassReader reader;
                try{reader=new ClassReader(bytes);}catch(RuntimeException invalid){throw new IOException("Invalid installed class: "+relative,invalid);}
                // A misplaced backup is not resolvable by its declared binary name through '.'.
                String binary=reader.getClassName()+".class";
                if(!(windows?relative.equalsIgnoreCase(binary):relative.equals(binary)))return FileVisitResult.CONTINUE;
                if(binary.startsWith("pzopt/")||binary.startsWith("antikristianos/pzoptpreloader/"))return FileVisitResult.CONTINUE;
                size[0]+=bytes.length;
                if(files.size()>=16384||size[0]>256*1024*1024)throw new IOException("Installed class snapshot exceeds supported size");
                byte[] previous=files.putIfAbsent(binary,bytes);
                if(previous!=null&&!Arrays.equals(previous,bytes))throw new IOException("Ambiguous installed class: "+binary);
                return FileVisitResult.CONTINUE;
            }
        });
        if(files.isEmpty())return null;
        validatePzfb(files);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(var entry:files.entrySet()){
            digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));digest.update((byte)0);
            digest.update(MessageDigest.getInstance("SHA-256").digest(entry.getValue()));
        }
        Path root=cacheDirectory.toAbsolutePath().normalize().resolve("installed-classes");Files.createDirectories(root);
        Path jar=root.resolve("classes-"+HexFormat.of().formatHex(digest.digest())+".jar");
        if(Files.exists(jar,LinkOption.NOFOLLOW_LINKS)){
            if(!matches(jar,files))throw new IOException("Installed classpath cache changed: "+jar);
            return jar;
        }
        Path temporary=Files.createTempFile(root,"classes-",".tmp");
        try{
            try(JarOutputStream output=new JarOutputStream(Files.newOutputStream(temporary))){
                for(var entry:files.entrySet()){
                    JarEntry member=new JarEntry(entry.getKey());member.setTime(0);
                    output.putNextEntry(member);output.write(entry.getValue());output.closeEntry();
                }
            }
            if(!matches(temporary,files))throw new IOException("Installed classpath cache verification failed");
            try{Files.move(temporary,jar,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,jar);}
        }finally{Files.deleteIfExists(temporary);}
        return jar;
    }
    private static void validatePzfb(Map<String,byte[]> files)throws IOException {
        String family="zombie/core/Color";byte[] primary=files.get(family+".class");if(primary==null)return;
        Set<String> required=new TreeSet<>();boolean[] methods=new boolean[2];
        new ClassReader(primary).accept(new ClassVisitor(Opcodes.ASM9){
            private void member(String name){if(name!=null&&name.startsWith(family+"$"))required.add(name+".class");}
            @Override public void visitNestMember(String name){member(name);}
            @Override public void visitInnerClass(String name,String outer,String simple,int access){member(name);}
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                if((access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC))==(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC)){
                    if(name.equals("fbPing")&&desc.equals("()Ljava/lang/String;"))methods[0]=true;
                    if(name.equals("fbApiLevel")&&desc.equals("()I"))methods[1]=true;
                }return null;
            }
        },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        if(!methods[0]&&!methods[1])return;
        if(!methods[0]||!methods[1]||!files.keySet().containsAll(required))throw new IOException("Incomplete installed PZFB class family");
    }
    private static boolean matches(Path jar,Map<String,byte[]> files)throws IOException {
        if(!Files.isRegularFile(jar,LinkOption.NOFOLLOW_LINKS))return false;
        try(JarFile input=new JarFile(jar.toFile())){
            if(input.size()!=files.size())return false;
            for(var entry:files.entrySet()){
                JarEntry member=input.getJarEntry(entry.getKey());
                if(member==null||member.getSize()!=entry.getValue().length)return false;
                try(InputStream stream=input.getInputStream(member)){if(!Arrays.equals(stream.readAllBytes(),entry.getValue()))return false;}
            }
        }return true;
    }
    static boolean previousManagedClasspath(String value,List<String> baseJars,Path cacheDirectory){
        String[] entries=value.split(java.util.regex.Pattern.quote(File.pathSeparator),-1);
        if(Arrays.asList(entries).equals(baseJars))return true;
        if(entries.length!=baseJars.size()+1||!Arrays.asList(entries).subList(1,entries.length).equals(baseJars))return false;
        try{
            Path path=Path.of(entries[0]).toAbsolutePath().normalize(),root=cacheDirectory.toAbsolutePath().normalize();
            return(path.getParent().equals(root.resolve("installed-classes"))&&path.getFileName().toString().matches("classes-[0-9a-f]{64}\\.jar"))
                ||(path.getParent().equals(root.resolve("pzfb"))&&path.getFileName().toString().matches("color-[0-9a-f]{64}\\.jar"));
        }catch(RuntimeException invalid){return false;}
    }
}
