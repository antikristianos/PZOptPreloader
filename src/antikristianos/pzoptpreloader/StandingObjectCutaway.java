package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.lang.invoke.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;
import zombie.core.properties.IsoPropertyType;
import zombie.iso.*;
import zombie.iso.fboRenderChunk.*;
import zombie.iso.sprite.*;
import zombie.IndieGL;

/** Camera-only admission of verified standing props to the native obstruction/fade lifecycle. */
public final class StandingObjectCutaway {
    private static final String CELL="zombie/iso/fboRenderChunk/FBORenderCell",SELF="antikristianos/pzoptpreloader/StandingObjectCutaway";
    private static final String OBJECT="Lzombie/iso/IsoObject;";
    private static final Map<String,String> PINS=Map.of(
        "isPotentiallyObscuringObject("+OBJECT+")Z","45f7012fe660dcae50122c5773c9910fdcdb569e8ae1791029ab7d94b77b0b68",
        "calculateObjectTargetAlpha("+OBJECT+")F","17fbe88fd3fcd65969361d421bd9af24b870520c225384ad0abe057591782711",
        "calculateObjectTargetAlpha_NotDoorOrWall("+OBJECT+")F","75d1abad5731f9f07f7526a69421e82a0c50b1603585f4284334b6b91c1dca41",
        "calculateObjectRenderLayer("+OBJECT+")Lzombie/iso/fboRenderChunk/ObjectRenderLayer;","c6aa7863660c9e7d4eabeb729de6d05499f71c3940504a6046f9f49058365543",
        "renderMinusFloor_NotDoorOrWall("+OBJECT+")V","31bb3e38e2db496ccc68488ed95b4c71405540a8e5b567a70220573774b208a1",
        "calculateObjectsObscuringPlayer(ILjava/util/ArrayList;)V","371087afcabdde8f22380c58ba9894ff8a0d756baf7423901642f7c9ebd3c335");
    private static final int STANDING=1,PILLAR=2,TANK=6;
    private static final ThreadLocal<Cache> CACHE=ThreadLocal.withInitial(Cache::new);
    private StandingObjectCutaway() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!name.equals(CELL))return bytes;
        ClassReader reader=new ClassReader(bytes);if(!reader.getClassName().equals(CELL))throw new IOException("Standing-object class identity");
        Map<String,String> actual=Compatibility.methods(bytes);for(var pin:PINS.entrySet())if(!RepairProfiles.matches(pin.getValue(),actual.get(pin.getKey())))throw new IOException("Unsupported standing-object boundary: "+pin.getKey());
        int[] hits={0,0,0,0,0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,n,d,s,e)){
                int squareCalls;boolean afterLoop;
                @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){
                    super.visitMethodInsn(op,owner,method,desc,itf);
                    if(n.equals("calculateObjectsObscuringPlayer")&&owner.equals(CELL)&&method.equals("testSquareObscuringPlayer"))squareCalls++;
                }
                @Override public void visitJumpInsn(int op,Label label){super.visitJumpInsn(op,label);if(n.equals("calculateObjectsObscuringPlayer")&&squareCalls==6&&op==Opcodes.GOTO&&hits[4]==0)afterLoop=true;}
                @Override public void visitFrame(int type,int count,Object[] locals,int stackCount,Object[] stack){
                    super.visitFrame(type,count,locals,stackCount,stack);if(afterLoop){afterLoop=false;super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ILOAD,1);super.visitVarInsn(Opcodes.ALOAD,2);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/PeekIntegration","extendPropCutaway","(Lzombie/iso/fboRenderChunk/FBORenderCell;ILjava/util/ArrayList;)V",false);hits[4]++;}
                }
                @Override public void visitFieldInsn(int op,String owner,String field,String desc){
                    super.visitFieldInsn(op,owner,field,desc);
                    if(n.equals("isPotentiallyObscuringObject")&&op==Opcodes.GETFIELD&&owner.equals("zombie/iso/sprite/IsoSprite")&&field.equals("solidTrans")&&desc.equals("Z")){
                        super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,SELF,"potential","(Z"+OBJECT+")Z",false);hits[0]++;
                    }
                }
                @Override public void visitInsn(int op){
                    if(n.equals("calculateObjectRenderLayer")&&op==Opcodes.ARETURN){
                        super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,SELF,"layer","(Lzombie/iso/fboRenderChunk/ObjectRenderLayer;"+OBJECT+")Lzombie/iso/fboRenderChunk/ObjectRenderLayer;",false);hits[3]++;
                    }
                    if((n.equals("calculateObjectTargetAlpha")||n.equals("calculateObjectTargetAlpha_NotDoorOrWall"))&&op==Opcodes.FRETURN){
                        super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ALOAD,0);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,SELF,n.equals("calculateObjectTargetAlpha")?"alpha":"lateAlpha","(F"+OBJECT+"Ljava/lang/Object;)F",false);hits[n.equals("calculateObjectTargetAlpha")?1:2]++;
                    }
                    super.visitInsn(op);
                }
            };}
        },0);
        if(hits[0]!=1||hits[1]!=3||hits[2]!=11||hits[3]!=9||hits[4]!=1)throw new IOException("Standing-object call shape: "+Arrays.toString(hits));return writer.toByteArray();
    }
    /** Applied after the existing physical-source adapter has verified its original boundary. */
    public static byte[] depthScope(String name,byte[] bytes)throws IOException{
        if(!name.equals(CELL))return bytes;
        String signature="renderMinusFloor_NotDoorOrWall("+OBJECT+")V";
        if(!RepairProfiles.matches(PINS.get(signature),Compatibility.methods(bytes).get(signature)))throw new IOException("Standing-object depth boundary changed");
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                if(!(n+d).equals(signature))return super.visitMethod(a,n,d,s,e);
                plainScope(super.visitMethod(a,n,d,s,e));
                return super.visitMethod((a&~(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PRIVATE,"pzoptPreloaderStandingPlain",d,s,e);
            }
        },0);return writer.toByteArray();
    }
    private static void plainScope(MethodVisitor m){
        m.visitCode();m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(Opcodes.INVOKESTATIC,SELF,"beginDepth","("+OBJECT+")V",false);
        Label start=new Label(),end=new Label(),handler=new Label();m.visitTryCatchBlock(start,end,handler,"java/lang/Throwable");m.visitLabel(start);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(Opcodes.INVOKESPECIAL,CELL,"pzoptPreloaderStandingPlain","("+OBJECT+")V",false);m.visitLabel(end);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,SELF,"endDepth","()V",false);m.visitInsn(Opcodes.RETURN);m.visitLabel(handler);m.visitFrame(Opcodes.F_SAME1,0,null,1,new Object[]{"java/lang/Throwable"});m.visitVarInsn(Opcodes.ASTORE,2);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,SELF,"endDepth","()V",false);m.visitVarInsn(Opcodes.ALOAD,2);m.visitInsn(Opcodes.ATHROW);m.visitMaxs(2,3);m.visitEnd();
    }
    private static boolean range(String name,String prefix,int low,int high){
        if(name==null||!name.startsWith(prefix))return false;int value=0;
        for(int i=prefix.length();i<name.length();i++){char c=name.charAt(i);if(c<'0'||c>'9')return false;value=value*10+c-'0';if(value>high)return false;}
        return name.length()>prefix.length()&&value>=low;
    }
    private static int kind(IsoSprite sprite){
        if(sprite==null)return 0;Cache cache=CACHE.get();Integer known=cache.sprites.get(sprite);if(known!=null)return known;
        var properties=sprite.getProperties();String custom=properties.get(IsoPropertyType.CUSTOM_NAME),painting=properties.get(IsoPropertyType.PAINTING_TYPE),name=sprite.getName();int result=0;
        if("pillar".equals(painting)||"Pillar".equals(custom)||range(name,"location_community_church_small_01_",32,33)||range(name,"walls_detailing_02_",4,7))result=PILLAR;
        else if(range(name,"industry_02_",128,131)||"industry_02_151".equals(name))result=TANK;
        else if(range(name,"appliances_com_01_",28,30)||range(name,"appliances_com_01_",36,38)||range(name,"industry_02_",72,75))result=STANDING;
        cache.sprites.put(sprite,result);return result;
    }
    public static boolean candidate(IsoObject object){
        if(object==null||object instanceof zombie.iso.objects.IsoWorldInventoryObject||object.getSquare()==null)return false;
        IsoSprite sprite=object.getSprite();if(sprite==null||sprite.getProperties().has(zombie.iso.SpriteDetails.IsoFlagType.water))return false;
        IsoGridSquare camera=IsoCamera.frameState.camCharacterSquare;
        if(kind(sprite)==TANK&&(camera==null||object.square.z!=camera.z))return false; // Added fading belongs only to the player's current floor.
        var character=IsoCamera.frameState.camCharacter;if(character!=null&&(character.isSittingOnFurniture()&&character.isSitOnFurnitureObject(object)||character.isOnBed()&&character.getBed()==object))return false;
        return kind(sprite)!=0;
    }
    public static boolean potential(boolean nativeValue,IsoObject object){
        if(object==null)return nativeValue;
        return nativeValue||candidate(object);
    }
    public static boolean peekCandidate(IsoObject object){
        if(!candidate(object))return false;int family=kind(object.getSprite());
        return family==PILLAR||family==TANK||range(object.getSprite().getName(),"appliances_com_01_",28,30)||range(object.getSprite().getName(),"appliances_com_01_",36,38);
    }
    public static ObjectRenderLayer layer(ObjectRenderLayer original,IsoObject object){
        if(WallReflectionSource.sourcePass())return original;
        IsoGridSquare camera=IsoCamera.frameState.camCharacterSquare;
        boolean upperTank=object!=null&&object.square!=null&&camera!=null&&object.square.z>camera.z&&tankRole(object.getSprite())>=0;
        if(!depthReceiver(object)&&!upperTank)return original;
        // Keep these vertically joined shafts on the working native per-frame path even at alpha 1.
        // None/floor/other native exclusions remain authoritative; physical sources remain cached.
        if(original==ObjectRenderLayer.MinusFloor)return ObjectRenderLayer.Translucent;
        if(original==ObjectRenderLayer.MinusFloorSE)return ObjectRenderLayer.TranslucentSE;
        return original;
    }
    private static int tankRole(IsoSprite sprite){
        if(sprite==null)return -1;String name=sprite.getName();
        if("industry_02_128".equals(name)||"industry_02_151".equals(name))return 0;
        if("industry_02_131".equals(name))return 1;
        if("industry_02_129".equals(name))return 2;
        return "industry_02_130".equals(name)?3:-1;
    }
    private static boolean hiddenTankExtension(IsoObject object,int player,int floor){
        int role=tankRole(object.getSprite());IsoCell world=IsoWorld.instance.currentCell;if(role<0||world==null)return false;
        int x=object.square.x-(role&1),y=object.square.y-(role>>1);
        // Only native exclusion of a connected intermediate tank footprint propagates upward.
        // No distance/ground-obstruction rule and no persistent hidden state are introduced.
        for(int z=object.square.z-1;z>floor;z--){
            boolean connected=false;
            for(int part=0;part<4;part++){
                IsoGridSquare square=world.getGridSquare(x+(part&1),y+(part>>1),z);if(square==null||square.chunk==null)continue;
                boolean matching=false;var objects=square.getObjects();for(int i=0;i<objects.size();i++)if(tankRole(objects.get(i).getSprite())==part){matching=true;break;}
                if(!matching)continue;connected=true;
                if(!square.chunk.getCutawayDataForLevel(z).shouldRenderSquare(player,square)||!FBORenderCutaways.getInstance().shouldRenderBuildingSquare(player,square))return true;
            }
            if(!connected)break;
        }return false;
    }
    private static boolean depthReceiver(IsoObject object){return pillar(object);}
    public static boolean pillar(IsoObject object){return object!=null&&object.getSprite()!=null&&(range(object.getSprite().getName(),"location_community_church_small_01_",32,33)||range(object.getSprite().getName(),"walls_detailing_02_",4,7));}
    private static boolean pillarSquare(IsoGridSquare square){if(square==null)return false;var objects=square.getObjects();for(int i=0;i<objects.size();i++)if(pillar(objects.get(i)))return true;return false;}
    /** Native cached translucent-square lists only; no world scan, reflection or allocation per object. */
    public static int shadowShapes(List<IsoChunk> chunks,int player,float[] shapes){
        if(chunks==null)return 0;int count=0;float ox=(float)Math.floor(IsoCamera.frameState.camCharacterX),oy=(float)Math.floor(IsoCamera.frameState.camCharacterY);
        for(int c=0;c<chunks.size();c++){
            IsoChunk chunk=chunks.get(c);if(chunk==null)continue;var levels=chunk.getRenderLevels(player);
            for(int z=chunk.minLevel;z<=chunk.maxLevel;z++){
                var squares=levels.getCachedSquares_TranslucentNonFloor(z);
                for(int s=0;s<squares.size();s++){
                    IsoGridSquare square=squares.get(s);var objects=square.getObjects();
                    for(int o=0;o<objects.size();o++)if(pillar(objects.get(o))){
                        float x=square.x+.5f-ox,y=square.y+.5f-oy,low=square.z*2.4494897f,high=low+2.4494897f;int index=-1;
                        int lx=square.x-chunk.wx*8,ly=square.y-chunk.wy*8;
                        for(int level=square.z+1;level<=chunk.maxLevel&&level<=square.z+8;level++){if(!pillarSquare(chunk.getGridSquare(lx,ly,level)))break;high=(level+1)*2.4494897f;}
                        for(int i=0;i<count;i++)if(shapes[i*4]==x&&shapes[i*4+1]==y&&low<=shapes[i*4+3]+.01f&&high>=shapes[i*4+2]-.01f){index=i;low=Math.min(low,shapes[i*4+2]);high=Math.max(high,shapes[i*4+3]);break;}
                        if(index<0){
                            if(count<shapes.length/4)index=count++;
                            else{float far=-1;for(int i=0;i<count;i++){float d=shapes[i*4]*shapes[i*4]+shapes[i*4+1]*shapes[i*4+1];if(d>far){far=d;index=i;}}if(x*x+y*y>=far)continue;}
                        }
                        shapes[index*4]=x;shapes[index*4+1]=y;shapes[index*4+2]=low;shapes[index*4+3]=high;
                    }
                }
            }
        }return count;
    }
    public static void beginDepth(IsoObject object){
        Cache cache=CACHE.get();if(cache.depth==cache.depthObjects.length){cache.depthObjects=Arrays.copyOf(cache.depthObjects,cache.depth*2);cache.depthWrites=Arrays.copyOf(cache.depthWrites,cache.depth*2);}
        cache.depthObjects[cache.depth]=object;cache.depthWrites[cache.depth++]=false;
        ShoreReceiverState.beginStanding(object);
    }
    public static void writeDepth(IsoObject object,IsoSprite sprite,boolean perFrame){
        Cache cache=CACHE.get();if(cache.depth==0||cache.depthObjects[cache.depth-1]!=object||WallReflectionSource.sourcePass()||FBORenderChunkManager.instance.isCaching()||!perFrame||!depthReceiver(object)||object.getSprite()!=sprite)return;
        int player=IsoCamera.frameState.playerIndex;if(object.getAlpha(player)<.99999f||object.getRenderInfo(player).targetAlpha<.99999f)return;
        cache.depthWrites[cache.depth-1]=true;IndieGL.glDepthMask(true);
    }
    public static void endDepth(){
        Cache cache=CACHE.get();if(cache.depth==0)throw new IllegalStateException("Unbalanced standing-object depth scope");int index=--cache.depth;boolean wrote=cache.depthWrites[index];cache.depthObjects[index]=null;cache.depthWrites[index]=false;
        ShoreReceiverState.endStanding();if(wrote)IndieGL.glDepthMask(index>0&&cache.depthWrites[index-1]);
    }
    public static float alpha(float nativeValue,IsoObject object,Object cell){
        if(WallReflectionSource.sourcePass()||CACHE.get().nativeAlpha||object==null||object.getSquare()==null)return nativeValue;
        int player=IsoCamera.frameState.playerIndex;ObjectRenderLayer layer=object.getRenderInfo(player).layer;
        if(layer!=ObjectRenderLayer.MinusFloor&&layer!=ObjectRenderLayer.MinusFloorSE&&layer!=ObjectRenderLayer.Translucent&&layer!=ObjectRenderLayer.TranslucentSE)return nativeValue;
        int family=kind(object.getSprite());
        IsoGridSquare camera=IsoCamera.frameState.camCharacterSquare;
        if(family==TANK&&camera!=null&&object.square.z>camera.z)return hiddenTankExtension(object,player,camera.z)?0:nativeValue;
        if(!candidate(object))return nativeValue;
        IsoGridSquare square=object.getSquare();
        if(family==PILLAR&&IsoCamera.frameState.camCharacterSquare!=null&&square.z>IsoCamera.frameState.camCharacterSquare.z){
            IsoGridSquare base=IsoWorld.instance.currentCell.getGridSquare(square.x,square.y,IsoCamera.frameState.camCharacterSquare.z);
            if(base==null)return nativeValue;IsoObject owner=null;for(int i=0;i<base.getObjects().size();i++)if(kind(base.getObjects().get(i).getSprite())==family){owner=base.getObjects().get(i);break;}if(owner==null)return nativeValue;
            nativeValue=Access.nativeAlpha(cell,owner);square=base;
        }
        if(family!=PILLAR&&nativeValue<1)return nativeValue<=0?nativeValue:Math.min(nativeValue,.4f);
        if(!Access.obscures(cell,player,square))return nativeValue;
        return square.isCanSee(player)?.4f:0;
    }
    public static float lateAlpha(float nativeValue,IsoObject object,Object cell){
        int family=object==null?0:kind(object.getSprite());
        // Keep the user-accepted dish/boiler draw path and unrelated native objects unchanged.
        return family==PILLAR||family==TANK?alpha(nativeValue,object,cell):nativeValue;
    }
    public static void clear(){CACHE.remove();}
    private static final class Cache {
        final WeakHashMap<IsoSprite,Integer> sprites=new WeakHashMap<>();boolean nativeAlpha;int depth;IsoObject[] depthObjects=new IsoObject[8];boolean[] depthWrites=new boolean[8];
    }
    private static final class Access {
        static final MethodHandle PLAYERS,OBSCURES,ALPHA;
        static {try{var lookup=MethodHandles.lookup();Class<?> type=Class.forName(CELL.replace('/','.')+"$PerPlayerData");var cellLookup=MethodHandles.privateLookupIn(FBORenderCell.class,lookup);PLAYERS=cellLookup.findGetter(FBORenderCell.class,"perPlayerData",java.lang.reflect.Array.newInstance(type,0).getClass()).asType(MethodType.methodType(Object[].class,Object.class));OBSCURES=MethodHandles.privateLookupIn(type,lookup).findVirtual(type,"isSquareObscuringPlayer",MethodType.methodType(boolean.class,IsoGridSquare.class)).asType(MethodType.methodType(boolean.class,Object.class,IsoGridSquare.class));ALPHA=cellLookup.findVirtual(FBORenderCell.class,"calculateObjectTargetAlpha_NotDoorOrWall",MethodType.methodType(float.class,IsoObject.class)).asType(MethodType.methodType(float.class,Object.class,IsoObject.class));}catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}}
        static float nativeAlpha(Object cell,IsoObject object){Cache cache=CACHE.get();cache.nativeAlpha=true;try{return (float)ALPHA.invokeExact(cell,object);}catch(Throwable error){throw new IllegalStateException("Native pillar base alpha unavailable",error);}finally{cache.nativeAlpha=false;}}
        static boolean obscures(Object cell,int player,IsoGridSquare square){try{Object[] players=(Object[])PLAYERS.invokeExact(cell);return (boolean)OBSCURES.invokeExact(players[player],square);}catch(Throwable error){throw new IllegalStateException("Native standing-object occlusion unavailable",error);}}
    }
}
