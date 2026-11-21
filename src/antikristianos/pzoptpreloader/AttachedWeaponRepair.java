package antikristianos.pzoptpreloader;

import java.io.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Retain original outfit selection and replace only its three shared-scratch reads. */
public final class AttachedWeaponRepair {
    public static final String TARGET="zombie/characters/AttachedItems/AttachedWeaponDefinitions";
    public static final Map<String,String> PINS=Map.of(TARGET,"dd157898f5b8a093ae887ab4fd3b64ac8c57fc67fb4a8241f81dcce9111df7d0",TARGET+"$L_addRandomAttachedWeapon","fc41b5094c87254ed2bbd4b6cd9d44ccd10685f5ae5e6d474624356beb6a18c2");
    private static final Set<String> METHODS=Set.of("addRandomAttachedWeapon","alwaysAddARandomAttachedWeapon","pickRandomInList");
    private AttachedWeaponRepair() { }
    public static byte[] patch(String name,byte[] code)throws IOException {
        if(!TARGET.equals(name))throw new IOException("Unknown attached-weapon target");
        try(InputStream in=AttachedWeaponRepair.class.getClassLoader().getResourceAsStream(TARGET+".class")){
            if(in==null)throw new IOException("Missing attached-weapon original");byte[] original=in.readAllBytes();
            if(!PINS.get(TARGET).equals(Compatibility.hash(original))||!Compatibility.methods(original).equals(Compatibility.methods(code)))throw new IOException("Unreviewed attached-weapon methods");
        }
        ClassReader reader=new ClassReader(code);if(!TARGET.equals(reader.getClassName()))throw new IOException("Attached-weapon class identity differs");
        ClassWriter writer=new ClassWriter(reader,0);Set<String> sites=new HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){MethodVisitor out=super.visitMethod(a,n,d,s,e);if(!METHODS.contains(n))return out;
            return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitFieldInsn(int op,String owner,String field,String desc){super.visitFieldInsn(op,owner,field,desc);
                if(op==Opcodes.GETSTATIC&&owner.equals(TARGET+"$L_addRandomAttachedWeapon")&&desc.equals("Ljava/util/ArrayList;")&&field.equals(n.equals("pickRandomInList")?"possibilities":"definitions")){
                    if(!sites.add(n))throw new IllegalStateException("Duplicate attached-weapon scratch read");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/AttachedWeaponCompatibility","local","(Ljava/util/ArrayList;)Ljava/util/ArrayList;",false);
                }
            }};
        }},0);if(!sites.equals(METHODS))throw new IOException("Attached-weapon scratch boundary differs");return writer.toByteArray();
    }
}
