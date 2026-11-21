package antikristianos.pzoptpreloader;

import java.io.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Bound only the original weather time and its render/wind extrapolation, not the renderer. */
public final class ViewpointWeatherRepair {
    private static final String AIR="viewpoint/environment/Atmosphere",WEATHER="viewpoint/environment/Weather",FRAME="viewpoint/render/FrameContext",SCENE="viewpoint/render/SceneData",HELPER="antikristianos/pzoptpreloader/ViewpointWeatherCompatibility";
    public static final Map<String,String> PINS=Map.of(AIR,"658e6d7aab7463ea5434b894c5ee0992dd723c5f8a843a9136186b240bd58c2f",WEATHER,"f00b9374924628d0f82263a62b2c923d032cf5bc928ad6b099b69afa0cc7a9f8",FRAME,"83b528aba343908386bf68f747cea27f7db201c68a802b6bd3ac63fc85745d7d");
    private ViewpointWeatherRepair(){ }
    public static byte[] patch(String name,byte[] code)throws IOException{
        if(!PINS.containsKey(name))throw new IOException("Unknown weather target");
        try(InputStream in=ViewpointWeatherRepair.class.getClassLoader().getResourceAsStream(name+".class")){
            if(in==null)throw new IOException("Missing original weather resource");byte[] original=in.readAllBytes();
            if(!PINS.get(name).equals(Compatibility.hash(original))||!Compatibility.methods(original).equals(Compatibility.methods(code)))throw new IOException("Unreviewed weather methods: "+name);
        }
        ClassReader reader=new ClassReader(code);if(!name.equals(reader.getClassName()))throw new IOException("Weather class identity differs");
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] clock={0},reset={0},elapsed={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){MethodVisitor out=super.visitMethod(a,n,d,s,e);
            boolean loaded=name.equals(AIR)&&n.equals("gameLoaded")&&d.equals("()V");
            boolean wind=name.equals(WEATHER)&&n.equals("carry")&&d.equals("(Lviewpoint/render/SceneData;)V");
            boolean carry=name.equals(FRAME)&&n.equals("carry")&&d.equals("(J)V");
            if(!loaded&&!wind&&!carry)return out;
            return new MethodVisitor(Opcodes.ASM9,out){
                @Override public void visitCode(){super.visitCode();if(loaded){super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"reset","()V",false);reset[0]++;}}
                @Override public void visitFieldInsn(int op,String owner,String field,String desc){
                    if(carry&&op==Opcodes.PUTFIELD&&owner.equals(FRAME)&&field.equals("since")&&desc.equals("F")){
                        scene();super.visitFieldInsn(Opcodes.GETFIELD,SCENE,"timeNanos","J");
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"carryElapsed","(FJ)F",false);elapsed[0]++;
                    }
                    if(carry&&op==Opcodes.PUTFIELD&&owner.equals(FRAME)&&field.equals("time")&&desc.equals("F")){
                        scene();super.visitFieldInsn(Opcodes.GETFIELD,SCENE,"time","F");scene();super.visitFieldInsn(Opcodes.GETFIELD,SCENE,"timeNanos","J");super.visitVarInsn(Opcodes.LLOAD,1);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"frameTime","(FFJJ)F",false);clock[0]++;
                    }
                    super.visitFieldInsn(op,owner,field,desc);
                }
                private void scene(){super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETFIELD,FRAME,"scene","L"+SCENE+";");}
                @Override public void visitVarInsn(int op,int slot){if(wind&&op==Opcodes.FSTORE&&slot==3){super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"elapsed","(F)F",false);elapsed[0]++;}super.visitVarInsn(op,slot);}
            };
        }},0);
        if(name.equals(AIR)?clock[0]!=0||reset[0]!=1||elapsed[0]!=0:name.equals(FRAME)?clock[0]!=1||reset[0]!=0||elapsed[0]!=1:clock[0]!=0||reset[0]!=0||elapsed[0]!=1)throw new IOException("Weather instruction boundary differs: "+name);
        return writer.toByteArray();
    }
}
