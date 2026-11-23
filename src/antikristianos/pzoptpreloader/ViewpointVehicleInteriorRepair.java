package antikristianos.pzoptpreloader;

import java.io.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Optional bridge at the final, original vehicle-material cull boundary. */
public final class ViewpointVehicleInteriorRepair {
    public static final String TARGET="viewpoint/render/VehiclePass";
    public static final Map<String,String> RESOURCES=Map.of(
        TARGET,"72946e8323e41e980225d40374aba7864846e065756445f348ca652cb8c06c08",
        "viewpoint/render/ModelDraws","3998d735ad886ecb6f821603f54beae3c44ce148ccdd4a1fc9cbfe4c216928af",
        "viewpoint/render/CameraClear","bf86f9c827209c3090bd7fad90f2d00b0314b0b815d4bed10640da3ed61f2c84",
        "viewpoint/platform/GlProgram","d2f611b082c807bbd4159912adfcd984d658fd8be34454198ab10875e62fef33",
        "viewpoint/core/View","54cfb99bcc0f196196c8e22633b2051a7c40def681458daf149b0ee041e23876");
    private ViewpointVehicleInteriorRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!TARGET.equals(name))return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!TARGET.equals(reader.getClassName()))throw new IOException("Vehicle interior identity differs");
        try(InputStream in=ViewpointVehicleInteriorRepair.class.getClassLoader().getResourceAsStream(name+".class")) {
            if(in==null)throw new IOException("Missing original vehicle interior resource");
            byte[] original=in.readAllBytes();
            if(!RESOURCES.get(name).equals(Compatibility.hash(original))||!Compatibility.methods(original).equals(Compatibility.methods(bytes)))
                throw new IOException("Changed vehicle interior methods");
        }
        int[] sites={0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                MethodVisitor out=super.visitMethod(a,n,d,s,e);
                if(!n.equals("material")||!d.equals("(Lviewpoint/platform/GlProgram;Lviewpoint/render/ModelDraws;I)V"))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean face) {
                        super.visitMethodInsn(op,owner,method,desc,face);
                        if(op!=Opcodes.INVOKEVIRTUAL||!owner.equals("viewpoint/render/ModelPass")||!method.equals("surface")||!desc.equals("(I)V"))return;
                        sites[0]++;
                        super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ALOAD,0);
                        super.visitFieldInsn(Opcodes.GETFIELD,TARGET,"gbuffer","Lviewpoint/platform/GlProgram;");
                        super.visitVarInsn(Opcodes.ALOAD,2);super.visitVarInsn(Opcodes.ILOAD,3);
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"viewpoint/render/ModelDraws","flags","(I)I",false);
                        super.visitFieldInsn(Opcodes.GETSTATIC,"viewpoint/core/View","enabled","Z");
                        super.visitFieldInsn(Opcodes.GETSTATIC,"viewpoint/core/View","thirdPerson","Z");
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/ViewpointVehicleInteriorCompatibility","surface","(Ljava/lang/Object;Ljava/lang/Object;IZZ)V",false);
                    }
                };
            }
        },0);
        if(sites[0]!=1)throw new IOException("Vehicle interior material boundary differs");
        return writer.toByteArray();
    }
}
