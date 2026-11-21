package antikristianos.pzoptpreloader;

import java.io.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Carry the ordinary perspective look with the driver's vehicle before fresh mouse input. */
public final class ViewpointVehicleRepair {
    public static final String TARGET="viewpoint/input/Look";
    public static final Map<String,String> RESOURCES=Map.of(
        TARGET,"532580d6674fb5b5029c2d7e9798da0ee49e4c54f64a278d0fc777c17c768b6f",
        "viewpoint/core/View","54cfb99bcc0f196196c8e22633b2051a7c40def681458daf149b0ee041e23876",
        "viewpoint/input/FreeCam","92dee79f816adc67a0073ff2023217051504dd5bf610b7648017d29836befdbf",
        "viewpoint/input/ClaimedCamera","b398466c4779bd30e7363275f772956fbb5e80988e960811d5b08e146cda1b66");
    private ViewpointVehicleRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!TARGET.equals(name))return bytes;
        ClassReader reader=new ClassReader(bytes);if(!TARGET.equals(reader.getClassName()))throw new IOException("Vehicle look identity differs");
        try(InputStream in=ViewpointVehicleRepair.class.getClassLoader().getResourceAsStream(name+".class")){
            if(in==null)throw new IOException("Missing original vehicle look");byte[] original=in.readAllBytes();
            if(!RESOURCES.get(name).equals(Compatibility.hash(original))||!Compatibility.methods(original).equals(Compatibility.methods(bytes)))throw new IOException("Changed vehicle look methods");
        }
        int[] sites={0,0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){MethodVisitor out=super.visitMethod(a,n,d,s,e);
            if(n.equals("framePresented")&&d.equals("()V"))return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitCode(){super.visitCode();sites[1]++;super.visitFieldInsn(Opcodes.GETSTATIC,"viewpoint/core/View","enabled","Z");super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/ViewpointVehicleCompatibility","viewMode","(Z)V",false);}};
            if(!n.equals("readBeforeDrawing")||!d.equals("()V"))return out;
            return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitCode(){super.visitCode();sites[0]++;
                super.visitFieldInsn(Opcodes.GETSTATIC,TARGET,"yaw","F");super.visitFieldInsn(Opcodes.GETSTATIC,"viewpoint/core/View","enabled","Z");
                super.visitMethodInsn(Opcodes.INVOKESTATIC,"viewpoint/input/FreeCam","centre","()Lviewpoint/input/FreeCam$Place;",false);
                super.visitFieldInsn(Opcodes.GETSTATIC,"viewpoint/input/FreeCam","active","Z");super.visitFieldInsn(Opcodes.GETSTATIC,TARGET,"followed","J");super.visitFieldInsn(Opcodes.GETSTATIC,TARGET,"addonFollowed","J");
                super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/ViewpointVehicleCompatibility","yaw","(FZLjava/lang/Object;ZJJ)F",false);super.visitFieldInsn(Opcodes.PUTSTATIC,TARGET,"yaw","F");
            }};
        }},0);if(sites[0]!=1||sites[1]!=1)throw new IOException("Vehicle look boundary differs");return writer.toByteArray();
    }
}
