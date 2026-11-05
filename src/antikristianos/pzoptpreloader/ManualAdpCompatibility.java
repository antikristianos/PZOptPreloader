package antikristianos.pzoptpreloader;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Recognizes the installed ADP simulation; its original physics remains its owner. */
public final class ManualAdpCompatibility {
    public static final String SIMULATION="zombie/core/physics/WorldSimulation";
    private static final String CONTROLLER="zombie/core/physics/CarController";
    enum Kind { ABSENT, STANDARD, OPTCOMPAT }
    private static final Map<String,Kind> WORLD_HASHES=Map.of(
        "cd874e23b69c7c72067b7914f35f6029a07aa1e404631a9bb2f19d69a6591563",Kind.STANDARD,
        "17ee8f7c23f5820bf31dd94f88d7f349412e049364f526542e515ec02f5fffd3",Kind.OPTCOMPAT);
    private static final String CONTROLLER_HASH="c1e38b3dc3151b8dea3f96edd53f768cf2f4832991782a01b2a9f4213813ff57";
    private static final Map<String,String> NESTED_HASHES=Map.of(
        "BulletVariables","3c55833dcc32ca6fe4f898c1ff64e32faa66a2f175830bd594b46d8630750167",
        "ClientControls","5f882f940b2cec08ef34104a95df87f06c44189130b3f8229f60d105605fa16d",
        "ControlState","b85ef7b5ddc9902d5cffc5c0d4cc7c393a5b3c12b010a556f4e2db662813d1d1",
        "GearInfo","5776d1adb5376089361952531e8825216524e7d86ef51b04bcd1a37911a3467c",
        "TorqueConverterType","cfe49bdb3eb6f9cc0c4bd7967d66a97ca63bf3cf3f61dca9140fcfb17bfb02ee",
        "TransmissionType","b1c8f098c036e5e4921a8af04b4a172781929fc94d0ec69ee13b956fde97de17",
        "VehicleInfoType","17c004177433d6d1a8f08058f015a76b1a0303a7e45f6b433366e31afe1d7fb4");
    private static final String INVENTORY="zombie/inventory/";
    private static final Map<String,String> CONTAINER_HASHES=Map.ofEntries(
        Map.entry("ItemContainer$CategoryPredicate","b382c048632da9e5a6694b1660aa75c2652a9b88e88b84446acc86aa2b2af106"),
        Map.entry("ItemContainer$Comparators","b19f0710493bdb9e66554f3cff29af6dfa471a874e1d1c9ce4a597a69fa954c5"),
        Map.entry("ItemContainer$ConditionComparator","271152cbf049ab345db44a6643548350fa1b846eb722ea4b361a5d021dcaf4bb"),
        Map.entry("ItemContainer$EvalArgComparator","e3310a78be79d186982cb8aefe426d56cd55f27b4f4f1f576f7976f3ce91e52a"),
        Map.entry("ItemContainer$EvalArgPredicate","42856d780ad46bbcedcf3764dbd70e029dd0e110177cd0ec2de5904d093bef1b"),
        Map.entry("ItemContainer$EvalComparator","af000c221250cd3ba442a6a30014694be68cf9ea192394423fb63ba56b38806d"),
        Map.entry("ItemContainer$EvalPredicate","bbe2e66b32953634e80f0ea874201f1ecdef7cb3d7dba5c7bd0fd8c2805b727d"),
        Map.entry("ItemContainer$InventoryItemList","a443144620365c710805aadc2ca258ee57d5ef9cbc99162579502a24416de30e"),
        Map.entry("ItemContainer$InventoryItemListPool","e3388089c036bbc3d69dbfa005d0dd8eda767c3084974af750ed5d59bedfcad4"),
        Map.entry("ItemContainer$Predicates","8796c1ab777adcc37342e95179e1c1cb4d18e884b090f93a43f01da6d1f6bba7"),
        Map.entry("ItemContainer$TagEvalArgPredicate","76c9206c28fc10a71a2d7967f22b2dcc3d0bde86a623d1cacb82d871c3ed3107"),
        Map.entry("ItemContainer$TagEvalPredicate","1d2836509f9112f7390401be98b3a576e58ac301fc642520757605ed1b5c6707"),
        Map.entry("ItemContainer$TagPredicate","792a28fe30ba46641e7898c77bddae8e9f3d1a31455a9f2e612e72419387195b"),
        Map.entry("ItemContainer$TypeEvalArgPredicate","32e5ea4eaf8effd7a642035b6ea2e1eed926544c727f3689d2c06fcc322c936a"),
        Map.entry("ItemContainer$TypeEvalPredicate","362ea6280e811d7fd69b12bf17487217d38cc5a63a6ffd93ad74358aeefea58e"),
        Map.entry("ItemContainer$TypePredicate","eef2052dc196fe1702f3cc96561b9a0a46089239f6b6e7a9bfc0ceb240c257c4"),
        Map.entry("ItemContainer","3ef6988e5ce1eb101f301c2cd973e062fe93bdb7ccc5257f7dc8a4c332834554"));
    private static final String VEHICLES="zombie/vehicles/";
    private static final String PERFORMANCE="zombie/core/physics/WorldSimulation$s_performance";
    private static final String PERFORMANCE_HASH="e5194d3c2dcd839dc6f0d89d81cd20fccc5ef6e3161c58b342c620eca166fc42";
    private static final Map<String,String> VEHICLE_HASHES=Map.ofEntries(
        Map.entry("BaseVehicle$1","6b1dd6ca42190ad28c124296a551293c88f7ce0790afe2d4c9c8d92c16177648"),
        Map.entry("BaseVehicle$Authorization","f4f866c063f6662dee335efd14a396253558c4e53e561965d20caf128040d4c2"),
        Map.entry("BaseVehicle$engineStateTypes","aa04b04385b80e02f61da8766b3bf877a58cfe928c97870bbd45623087ce39fe"),
        Map.entry("BaseVehicle$HitVars","e4ae87bf2a862b9eae88b0719682e9856da1e74fcf4ffd08fa694c419d0a7d55"),
        Map.entry("BaseVehicle$L_testCollisionWithVehicle","7fc018b65b441c5bc97b7843d87e7416095b8f478c0ca324792111d72588c7a4"),
        Map.entry("BaseVehicle$Matrix4fObjectPool","db0be83ee76b2482857dabb17b11266633d2c3925df78cd94b7844189d96a15f"),
        Map.entry("BaseVehicle$MinMaxPosition","7779e2b5735fc9e8fd918f4ee6750a742b7fc9fadb0661b1f06f452f46274099"),
        Map.entry("BaseVehicle$ModelInfo","37c4eb917e3bd2279efa6db009d9aa2476f5decbe6544cc682923776afd07dc4"),
        Map.entry("BaseVehicle$Passenger","a1ebef98ddda990d3e1cdd845d41ec068f814ecbe74d38d4319f209cc962a349"),
        Map.entry("BaseVehicle$PositionHistoryEntry","8dc840cf29af888dc6e20a395a94879047a8c46a630e0e62129eb49fa9497304"),
        Map.entry("BaseVehicle$QuaternionfObjectPool","283aeb672411c97aed6079abe80828e9c2d3e4701525118677b81b6a06240aea"),
        Map.entry("BaseVehicle$ServerVehicleState","9c450e297121ebad2cd4d32b4cb404d87ee3096aa093f515d6fd0a1ccf03ffa3"),
        Map.entry("BaseVehicle$TransformPool","78450263b4d28d946c80da78275981cdb6fad9bb990ad9b2f54dcefc5ff7093c"),
        Map.entry("BaseVehicle$UpdateFlags","3280fe0d5daf677e7bf252e74a3c11d137ba4c5a640b2f9de9da68a76204c780"),
        Map.entry("BaseVehicle$Vector2fObjectPool","32d36f43713dadf00cfd6b9836c0246543fead231a0148cde900c3d34840a948"),
        Map.entry("BaseVehicle$Vector3fObjectPool","d5e781170bf0f540b56da579f3c6513ab6aa4ea0c433e2a9e8477d459ee7d9d0"),
        Map.entry("BaseVehicle$Vector3ObjectPool","a5e652e3b19443c0a242e721da39e434b9d102a4aa4c5a825c2877d4ca3ffca4"),
        Map.entry("BaseVehicle$Vector4fObjectPool","e415173416c5e0435528d04201f8c6e9d61e486015d95f55535cdca52ea3e05a"),
        Map.entry("BaseVehicle$VehicleImpulse","a1d81381876f367fe246d346b1f96876a578f0d095e8ece629e50b287c3e1eaa"),
        Map.entry("BaseVehicle$WeightedVehiclePart","e2d897d7e85d78bf3604602f9dd6ae78a5046c7c8400659c796d7a7201ca1cf7"),
        Map.entry("BaseVehicle$WheelInfo","b70ba7eb5625ba5b452ba6d6028f14def023e39bc95daa56f40b9c3d1b6cc05f"),
        Map.entry("BaseVehicle","585e53fadf024d1a56d1cfac389074dfcbb2051a598f4b8338ae74abcb9154e7"));
    private ManualAdpCompatibility() { }

    /** Verified manual physics keeps its schema while joining the native signed package domain. */
    public static void defineSignedPhysics(Payload payload,java.lang.instrument.Instrumentation instrumentation)throws Exception{
        if(!payload.manualAdpPhysics())return;
        ClassLoader loader=ManualAdpCompatibility.class.getClassLoader();
        Class<?> anchor=Class.forName("zombie.core.physics.Bullet",false,loader);
        var origin=anchor.getProtectionDomain().getCodeSource();
        if(origin==null||origin.getCertificates()==null||origin.getCertificates().length==0)return;
        if(!java.nio.file.Path.of(origin.getLocation().toURI()).toRealPath().equals(Compatibility.gameJar()))throw new IOException("Manual ADP native anchor changed");
        Kind kind=detect();
        if(kind==Kind.ABSENT||(kind==Kind.OPTCOMPAT)!=payload.manualAdpOptimizedPhysics())throw new IOException("Verified manual ADP inputs changed");
        Set<String> loaded=new HashSet<>();for(Class<?> type:instrumentation.getAllLoadedClasses())loaded.add(type.getName().replace('.','/'));
        List<String> names=new ArrayList<>(List.of(SIMULATION,CONTROLLER));
        NESTED_HASHES.keySet().stream().sorted().forEach(name->names.add(CONTROLLER+"$"+name));
        Class<?> vehicleAnchor=null;
        if(kind==Kind.OPTCOMPAT){
            names.add(PERFORMANCE);
            vehicleAnchor=Class.forName("zombie.vehicles.IVehicleAlarmListener",false,loader);
            if(vehicleAnchor.getProtectionDomain()!=anchor.getProtectionDomain())throw new IOException("Manual ADP vehicle anchor differs");
            VEHICLE_HASHES.keySet().stream().sorted().forEach(name->names.add(VEHICLES+name));
        }
        Class<?> inventoryAnchor=null;
        if(!nativeContainer(loader)){
            inventoryAnchor=Class.forName("zombie.inventory.InventoryItem",false,loader);
            if(inventoryAnchor.getProtectionDomain()!=anchor.getProtectionDomain())throw new IOException("Manual ADP inventory anchor differs");
            CONTAINER_HASHES.keySet().stream().sorted().forEach(name->names.add(INVENTORY+name));
        }
        Map<String,byte[]> definitions=new LinkedHashMap<>();
        for(String name:names){
            if(loaded.contains(name))throw new IOException("Manual ADP class loaded before signed-package preparation: "+name);
            byte[] bytes=resource(loader,name);
            String expected=name.startsWith(VEHICLES)?VEHICLE_HASHES.get(name.substring(VEHICLES.length()))
                :name.equals(PERFORMANCE)?PERFORMANCE_HASH
                :name.startsWith(INVENTORY)?CONTAINER_HASHES.get(name.substring(INVENTORY.length()))
                :name.equals(SIMULATION)?WORLD_HASHES.entrySet().stream().filter(e->e.getValue()==kind).findFirst().orElseThrow().getKey()
                :name.equals(CONTROLLER)?CONTROLLER_HASH:NESTED_HASHES.get(name.substring(CONTROLLER.length()+1));
            if(bytes==null||!expected.equals(hash(bytes)))throw new IOException("Verified manual ADP member changed: "+name);
            definitions.put(name,bytes);
        }
        // As in the normal source-family loader, define nested types before their enclosing class:
        // verification of its initializers may otherwise resolve an unsigned classpath copy early.
        for(var entry:definitions.entrySet().stream().sorted(Comparator
            .comparingInt((Map.Entry<String,byte[]> e)->definitionOrder(e.getKey()))
            .thenComparing(e->e.getKey().startsWith(VEHICLES)?0:e.getKey().startsWith(INVENTORY)?1:2)
            .thenComparing(Map.Entry::getKey)).toList()){
            Class<?> target=entry.getKey().startsWith(VEHICLES)?vehicleAnchor:entry.getKey().startsWith(INVENTORY)?inventoryAnchor:anchor;
            Class<?> defined;
            try{defined=java.lang.invoke.MethodHandles.privateLookupIn(target,java.lang.invoke.MethodHandles.lookup()).defineClass(entry.getValue());}
            catch(LinkageError|SecurityException error){throw new IOException("Verified manual ADP member could not be defined: "+entry.getKey(),error);}
            if(defined.getProtectionDomain()!=target.getProtectionDomain())throw new IOException("Manual ADP package domain differs: "+defined.getName());
        }
    }
    private static int definitionOrder(String name){
        if(name.startsWith(VEHICLES)&&name.contains("$")&&(name.endsWith("ObjectPool")||name.endsWith("TransformPool")
            ||name.endsWith("$1")||name.endsWith("$Authorization")||name.endsWith("$engineStateTypes")))return 0;
        if(name.equals(VEHICLES+"BaseVehicle"))return 1;
        if(name.contains("$"))return 2;
        if(name.startsWith(INVENTORY))return 3;
        if(name.equals(CONTROLLER))return 4;
        return 5;
    }

    static Kind detect()throws IOException {
        ClassLoader loader=ManualAdpCompatibility.class.getClassLoader();
        byte[] world=resource(loader,SIMULATION),controller=resource(loader,CONTROLLER);
        if(world==null||controller==null)return Kind.ABSENT;
        boolean[] needsField={false};
        new ClassReader(controller).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM9){
                    @Override public void visitFieldInsn(int opcode,String owner,String field,String descriptor){
                        if(owner.equals(SIMULATION)&&field.equals("massScaler")&&descriptor.equals("F"))needsField[0]=true;
                    }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        if(!needsField[0])return Kind.ABSENT;
        Kind kind=WORLD_HASHES.get(hash(world));
        if(kind==null||!CONTROLLER_HASH.equals(hash(controller)))
            throw new IOException("Unverified manual ADP physics classes; refusing to overwrite their required massScaler schema");
        for(var entry:NESTED_HASHES.entrySet()){
            byte[] member=resource(loader,CONTROLLER+"$"+entry.getKey());
            if(member==null||!entry.getValue().equals(hash(member)))throw new IOException("Unverified manual ADP controller member: "+entry.getKey());
        }
        if(kind==Kind.OPTCOMPAT){
            verify(loader,PERFORMANCE,PERFORMANCE_HASH);
            for(var entry:VEHICLE_HASHES.entrySet())verify(loader,VEHICLES+entry.getKey(),entry.getValue());
        }
        if(!nativeContainer(loader))for(var entry:CONTAINER_HASHES.entrySet())verify(loader,INVENTORY+entry.getKey(),entry.getValue());
        return kind;
    }
    private static void verify(ClassLoader loader,String name,String expected)throws IOException{
        byte[] bytes=resource(loader,name);
        if(bytes==null||!expected.equals(hash(bytes)))throw new IOException("Unverified installed ADP member: "+name);
    }
    private static boolean nativeContainer(ClassLoader loader)throws IOException{
        var container=loader.getResource(INVENTORY+"ItemContainer.class");
        try{return container!=null&&container.openConnection() instanceof java.net.JarURLConnection connection
            &&java.nio.file.Path.of(connection.getJarFileURL().toURI()).toRealPath().equals(Compatibility.gameJar());}
        catch(java.net.URISyntaxException error){throw new IOException("Cannot identify installed container source",error);}
    }
    private static byte[] resource(ClassLoader loader,String name)throws IOException {
        try(InputStream input=loader.getResourceAsStream(name+".class")){return input==null?null:input.readAllBytes();}
    }
    private static String hash(byte[] bytes)throws IOException {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}
    }
    static byte[] keepManualPhysics(String name,byte[] bytes)throws IOException {
        if(!name.equals("pzopt/VehicleSmooth"))return bytes;
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        Set<String> gated=new HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions){
                if(!Set.of("enabled","stepsChanged").contains(method)||!desc.equals("()Z"))return super.visitMethod(access,method,desc,signature,exceptions);
                if((access&Opcodes.ACC_STATIC)==0)throw new IllegalStateException("Unexpected vehicle physics gate");
                MethodVisitor target=super.visitMethod(access,method,desc,signature,exceptions);
                target.visitCode();target.visitInsn(Opcodes.ICONST_0);target.visitInsn(Opcodes.IRETURN);target.visitMaxs(1,0);target.visitEnd();
                gated.add(method);return null;
            }
        },0);
        if(gated.size()!=2)throw new IOException("Unverified PZOpt vehicle smoothing gates; manual ADP ownership cannot be preserved");
        return writer.toByteArray();
    }
}
