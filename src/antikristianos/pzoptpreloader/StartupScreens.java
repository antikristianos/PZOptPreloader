package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.jar.asm.*;

/** Retains installed Vanilla logos and the warning call; leaves other optimized boot work intact. */
public final class StartupScreens {
    public static final String LOGO="zombie/gameStates/TISLogoState",WINDOW="zombie/GameWindow";
    private StartupScreens(){}
    public static boolean nativeLogo(String name){return name.equals(LOGO)||name.startsWith(LOGO+"$");}
    public static void retainNativeLogos(Map<String,byte[]> definitions)throws IOException{
        byte[] logo=definitions.get(LOGO);if(logo==null)throw new IOException("Original logo override missing");
        var methods=Compatibility.methods(logo);
        if(!"600be2715e46a27aaa54cb140ecce115c81c81bc29bd1bfefdca7dc23893b10e".equals(methods.get("render()V"))
                ||!"11ae3731e6cefb055a8fa05cdbfc4b4808398deda8ffd4251a86fc9a96272299".equals(methods.get("update()Lzombie/gameStates/GameStateMachine$StateAction;")))throw new IOException("Original logo skip boundary changed");
        if(!definitions.keySet().stream().filter(StartupScreens::nativeLogo).collect(java.util.stream.Collectors.toSet()).equals(Set.of(LOGO,LOGO+"$LogoElement")))throw new IOException("Original logo family changed");
        definitions.keySet().removeIf(StartupScreens::nativeLogo);
    }
    public static byte[] patch(String name,byte[] bytes)throws IOException{
        if(!name.equals(WINDOW))return bytes;
        if(!"bc3409d0d7b6f57435030078469ce206cb281fc2d150216337d360ace54be742".equals(Compatibility.methods(bytes).get("init()V")))throw new IOException("Startup warning boundary changed");
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] calls={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                MethodVisitor target=super.visitMethod(access,method,descriptor,signature,exceptions);
                if(!method.equals("init")||!descriptor.equals("()V"))return target;
                return new MethodVisitor(Opcodes.ASM9,target){
                    @Override public void visitMethodInsn(int op,String owner,String call,String type,boolean itf){
                        if(op==Opcodes.INVOKESTATIC&&owner.equals("pzopt/Overrides")&&call.equals("enabled")&&type.equals("()Z")){
                            super.visitInsn(Opcodes.ICONST_0);calls[0]++;
                        }else super.visitMethodInsn(op,owner,call,type,itf);
                    }
                };
            }
        },0);
        if(calls[0]!=1)throw new IOException("Startup warning gate count="+calls[0]);return writer.toByteArray();
    }
}
