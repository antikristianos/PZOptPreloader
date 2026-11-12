package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.lang.invoke.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.jar.asm.*;
import zombie.core.properties.IsoPropertyType;
import zombie.iso.*;
import zombie.iso.objects.*;
import zombie.iso.sprite.IsoSprite;

/** Opaque door leaves participate at their current native edge in the physical height query. */
public final class DoorShadowRepair {
    private static final String TARGET="pzopt/CapsuleShadow", METHOD="edgeHeight", DESC="(Lzombie/iso/IsoGridSquare;Z)F";
    private static final String HASH="7770874651434f9c62f4b95ed2c669c2d68a69d96f31c820016b01682409adaa";
    private DoorShadowRepair(){ }
    public static byte[] patch(String name,byte[] bytes)throws IOException{
        if(name.equals("pzopt/ChunkAo"))return sunShader(bytes);
        if(!TARGET.equals(name))return bytes;
        if(!RepairProfiles.matches(HASH,Compatibility.methods(bytes).get(METHOD+DESC)))throw new IOException("Unsupported physical door shadow boundary");
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] hits={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String n,String d,String signature,String[] exceptions){
                MethodVisitor out=super.visitMethod(access,n,d,signature,exceptions);if(!n.equals(METHOD)||!d.equals(DESC))return out;
                return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitInsn(int op){
                    if(op==Opcodes.FRETURN){super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ILOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/DoorShadowRepair","height","(FLzombie/iso/IsoGridSquare;Z)F",false);hits[0]++;}
                    super.visitInsn(op);
                }};
            }
        },0);
        if(hits[0]!=2)throw new IOException("Physical door shadow return shape changed: "+hits[0]);return writer.toByteArray();
    }
    private static byte[] sunShader(byte[] bytes)throws IOException{
        String hash=Compatibility.methods(bytes).get("<clinit>()V");
        if(!Set.of("8b54ab4db3b18a55b4b9fa8dfb802cd70171a8b841cbd4a7ad4553166509860c","26c4d9101535b44d209ebba29f7edf0f2e37d55d51eb171c0e0cfaa59201a0a0").contains(hash))throw new IOException("Unsupported physical sun source shader");
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] hits={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,n,d,s,e)){
            @Override public void visitFieldInsn(int op,String owner,String field,String desc){if(n.equals("<clinit>")&&op==Opcodes.PUTSTATIC&&owner.equals("pzopt/ChunkAo")&&field.equals("AO_FRAG")){super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/PhysicalShadowSources","shader","(Ljava/lang/String;)Ljava/lang/String;",false);hits[0]++;}super.visitFieldInsn(op,owner,field,desc);}
        };}},0);
        if(hits[0]!=1)throw new IOException("Physical sun source shader assignment changed");return writer.toByteArray();
    }
    private static final class Shapes {
        static final MethodHandle EDGE;
        static final ConcurrentHashMap<IsoSprite,float[]> HEIGHTS=new ConcurrentHashMap<>();
        static{try{var method=Class.forName("pzopt.CapsuleShadow").getDeclaredMethod("edgeShape",IsoSprite.class,boolean.class);method.setAccessible(true);EDGE=MethodHandles.lookup().unreflect(method);}catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
        static float sample(IsoSprite sprite,boolean north){try{return (float)EDGE.invokeExact(sprite,north);}catch(Throwable e){throw new IllegalStateException("Installed physical door shape",e);}}
    }
    public static float height(float original,IsoGridSquare square,boolean north){
        if(square==null)return original;
        // Wall runs and sunlight marching store only N/W edges. A leaf on the adjacent
        // square's S/E edge occupies the same plane, even though its doorway is open.
        IsoDirections edge=north?IsoDirections.N:IsoDirections.W;
        float result=leaves(original,square,edge,north);
        return leaves(result,square.getAdjacentSquare(edge),north?IsoDirections.S:IsoDirections.E,north);
    }
    private static float leaves(float original,IsoGridSquare square,IsoDirections edge,boolean north){
        if(square==null)return original;
        var objects=square.getSpecialObjects();float result=original;
        for(int i=0;i<objects.size();i++){
            IsoObject object=objects.get(i);IsoDirections current;
            if(object instanceof IsoDoor door){
                // Garage doors lift out of the standing edge rather than swinging into one.
                if(door.IsOpen()&&door.getProperties()!=null&&door.getProperties().has(IsoPropertyType.GARAGE_DOOR))continue;
                current=door.getSpriteEdge(false);
            }else if(object instanceof IsoThumpable gate&&gate.isDoor())current=gate.getSpriteEdge(false);
            else continue;
            if(current==edge)result=Math.max(result,leafHeight(object,north));
        }
        return result;
    }
    private static float leafHeight(IsoObject object,boolean north){
        IsoSprite sprite=object.getSprite();if(sprite==null)return 0;
        String name=sprite.getName();
        boolean opaqueGate=name!=null&&(name.startsWith("fixtures_doors_fences")||name.startsWith("walls_logs"));
        if(!opaqueGate&&(sprite.getProperties().has(IsoPropertyType.DOOR_TRANS)||object.getProperties()!=null&&object.getProperties().has(IsoPropertyType.DOOR_TRANS)))return 0;
        // Keep unready texture data eligible for a later measurement rather than freezing a fallback.
        var texture=sprite.texture;
        float extra;
        if(texture==null||texture.getMask()==null||texture.getWidthOrig()<=0||texture.getHeightOrig()<=0)extra=Shapes.sample(sprite,north);
        else extra=Shapes.HEIGHTS.computeIfAbsent(sprite,s->new float[]{Shapes.sample(s,true),Shapes.sample(s,false)})[north?0:1];
        return extra;
    }
}
