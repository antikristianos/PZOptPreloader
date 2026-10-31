package antikristianos.pzpeek42;

import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Startup/retransform-only contract for the tree paths we compose with. No release or class hash pins. */
public final class RenderPaths {
    private RenderPaths() { }
    public static final String CELL = "zombie/iso/fboRenderChunk/FBORenderCell";
    public static final String HELPER = "antikristianos/pzpeek42/TreeCache";
    public static final String OBJECT = "Lzombie/iso/IsoObject;", TREE = "Lzombie/iso/objects/IsoTree;";
    private static final String LEVELS = "Lzombie/iso/fboRenderChunk/FBORenderLevels;";
    private static final String PUBLIC = "isTranslucentTree("+OBJECT+")Z";
    private static final String PRIVATE = "pzoptIsTranslucentTree("+OBJECT+"Z)Z";
    private static final String NEED = "pzoptTreeNeedsPerFrame("+TREE+"Z)Z";
    private static final String BAKES = "pzoptTreeBakes("+TREE+"Z)Z";
    private static final String AT_BAKE = "pzoptTreeNeedsPerFrameAtBake("+TREE+")Z";
    private static final String EACH = "isTreeRenderedEveryFrame("+OBJECT+")Z";
    private static final String MINUS = "isObjectRenderLayer_MinusFloor("+OBJECT+")Z";
    private static final String TRANS = "isObjectRenderLayer_Translucent("+OBJECT+")Z";
    private static final String DIRTY = "pzoptReachDirtyTick(I"+LEVELS+")V";
    private static final String CHECK = "checkTreeTranslucency(I"+LEVELS+")Z";
    private static final String REACH = "pzoptReachOn()Z";
    private static final String COPIES = "pzoptInvalidateTreeCopies("+TREE+"I)V";
    private static final String LINGER = "pzoptTreeLinger("+LEVELS+TREE+"I)V";
    private static final String DRAW = "renderMinusFloor_NotDoorOrWall("+OBJECT+")V";
    public static final Map<String,String> WRAPPERS = Map.of(NEED,"needsPerFrame",BAKES,"bakes",MINUS,"minusFloor",TRANS,"translucentLayer");
    // Exact roles, not just a total call count: moving a predicate into the wrong path must fail.
    private static final Map<String,String> DECISIONS = Map.of(DIRTY,PRIVATE,CHECK,PRIVATE,BAKES,PRIVATE,MINUS,PUBLIC,TRANS,PUBLIC,DRAW,PUBLIC);

    public record Shape(int decisions, int wrappers, int cacheGates, Map<String,Integer> configReads) { }
    private record Call(int opcode,String owner,String method,boolean itf) { }
    private static final class MethodPath {
        final int access;
        final List<Call> calls = new ArrayList<>();
        final Map<String,Integer> fields = new HashMap<>();
        int returns;
        MethodPath(int access) { this.access=access; }
    }
    private static IllegalStateException mismatch(String detail) {
        return new IllegalStateException("tree render path mismatch: "+detail);
    }
    private static MethodPath instance(Map<String,MethodPath> methods,String key) {
        MethodPath path=methods.get(key);
        if(path==null)throw mismatch("missing method "+key);
        if((path.access & (Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)
            throw mismatch("method is not a concrete instance method: "+key);
        if(key.endsWith(")Z") && path.returns==0)throw mismatch("missing boolean return: "+key);
        return path;
    }
    private static void calls(Map<String,MethodPath> methods,String from,String... targets) {
        MethodPath path=instance(methods,from);
        for(String to:targets) {
            instance(methods,to);
            if(path.calls.stream().noneMatch(call -> call.owner.equals(CELL) && call.method.equals(to)
                    && !call.itf && (call.opcode==Opcodes.INVOKEVIRTUAL || call.opcode==Opcodes.INVOKESPECIAL)))
                throw mismatch(from+" no longer calls "+to);
        }
    }
    private static void reads(Map<String,MethodPath> methods,String from,String owner,String field,String descriptor,int opcode) {
        if(!instance(methods,from).fields.containsKey(opcode+":"+owner+"."+field+descriptor))
            throw mismatch(from+" no longer reads "+owner+"."+field+descriptor);
    }
    private static String decision(String original) {
        return "decision(L"+CELL+";"+OBJECT+(original.equals(PRIVATE)?"Z":"")+")Z";
    }
    public static Shape inspect(byte[] bytes,boolean bridged) {
        if(bytes==null)throw mismatch("effective renderer bytes unavailable");
        ClassReader reader=new ClassReader(bytes);
        if(!reader.getClassName().equals(CELL))throw mismatch("unexpected class "+reader.getClassName());
        Map<String,MethodPath> methods=new LinkedHashMap<>();
        Map<String,Integer> configReads=new TreeMap<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions) {
                String key=name+desc;
                MethodPath path=new MethodPath(access);
                if(methods.put(key,path)!=null)throw mismatch("duplicate method "+key);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitInsn(int opcode) { if(opcode==Opcodes.IRETURN)path.returns++; }
                    @Override public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean itf) {
                        path.calls.add(new Call(opcode,owner,name+desc,itf));
                    }
                    @Override public void visitFieldInsn(int opcode,String owner,String name,String desc) {
                        String field=opcode+":"+owner+"."+name+desc;
                        path.fields.merge(field,1,Integer::sum);
                        if(opcode==Opcodes.GETSTATIC && owner.equals("pzopt/Config"))configReads.merge(key+":"+name+desc,1,Integer::sum);
                    }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        for(String key:WRAPPERS.keySet())instance(methods,key);
        // The entry shortcut is safe only while the superseded classifier itself is read-only.
        for(String dependency:instance(methods,NEED).fields.keySet()){
            if(!Set.of(Opcodes.GETFIELD+":zombie/iso/objects/IsoTree.fadeAlphaF",
                    Opcodes.GETSTATIC+":pzopt/Config.TREE_CUTAWAY_REACH_PXI",
                    Opcodes.GETSTATIC+":zombie/core/Core.tileScaleI").contains(dependency))throw mismatch("per-frame classifier input "+dependency);
        }
        for(Call call:instance(methods,NEED).calls){
            boolean nativeCall=call.owner.equals(CELL) && Set.of("pzoptXxlCutaway("+OBJECT+")Z","pzoptTreeRule("+OBJECT+"Z)Z",
                "pzoptReachBoxes()[I","pzoptTreeTouchesMask("+TREE+"[II)Z").contains(call.method);
            boolean ownCall=call.owner.equals(HELPER) && Set.of("earlyNeeds(L"+CELL+";"+TREE+")I","needsPerFrame(ZL"+CELL+";"+TREE+")Z").contains(call.method);
            if(!nativeCall && !ownCall)throw mismatch("per-frame classifier call "+call.owner+"."+call.method);
        }
        if((instance(methods,PUBLIC).access & Opcodes.ACC_PUBLIC)==0)throw mismatch("public PeekAView hook is no longer public");
        calls(methods,PUBLIC,PRIVATE); // Do not redirect this edge back through PeekAView.
        calls(methods,DIRTY,EACH,"pzoptTickBakedTree("+TREE+"I)V");
        calls(methods,CHECK,REACH,DIRTY,EACH,NEED,LINGER,COPIES);
        calls(methods,AT_BAKE,NEED);
        calls(methods,BAKES,REACH,NEED,EACH);
        calls(methods,MINUS,AT_BAKE,EACH);
        calls(methods,TRANS,AT_BAKE,EACH);
        calls(methods,"pzoptBakeTrees(Lzombie/iso/IsoChunk;IF)V",BAKES);
        calls(methods,"pzoptQueueTreeAppend(Lzombie/iso/IsoChunk;Lzombie/iso/IsoChunk;IIIFZIII)Z",BAKES);
        calls(methods,COPIES,"pzoptTreePassActive()Z");
        for(String key:List.of(REACH,CHECK,EACH,"pzoptTreePassActive()Z"))
            reads(methods,key,"pzopt/Config","TREES_IN_CHUNK_TEXTURE","Z",Opcodes.GETSTATIC);
        reads(methods,CHECK,"pzopt/Config","TREE_REBAKE_LAZY","Z",Opcodes.GETSTATIC);
        reads(methods,REACH,"pzopt/Config","TREE_CUTAWAY_REACH","Z",Opcodes.GETSTATIC);
        for(String key:List.of(CHECK,AT_BAKE)) {
            reads(methods,key,CELL,"pzoptTreesOut","Ljava/util/Set;",Opcodes.GETFIELD);
            for(String operation:List.of("add(Ljava/lang/Object;)Z","remove(Ljava/lang/Object;)Z"))
                if(instance(methods,key).calls.stream().noneMatch(call -> call.owner.equals("java/util/Set") && call.method.equals(operation)))
                    throw mismatch(key+" lost removed-tree membership operation "+operation);
        }
        reads(methods,LINGER,CELL,"pzoptTreeLingerUntil","Ljava/util/IdentityHashMap;",Opcodes.GETFIELD);
        int decisions=0,publicOriginal=0;
        Map<String,Integer> seen=new HashMap<>();
        for(var entry:methods.entrySet()) {
            String key=entry.getKey();MethodPath path=entry.getValue();
            for(Call call:path.calls) {
                boolean original=call.owner.equals(CELL) && (call.method.startsWith("pzoptIsTranslucentTree(") || call.method.startsWith("isTranslucentTree("));
                boolean helper=call.owner.equals(HELPER) && call.method.startsWith("decision(");
                if(!original && !helper)continue;
                if(key.equals(PUBLIC) && original && call.method.equals(PRIVATE) && !call.itf
                        && (call.opcode==Opcodes.INVOKEVIRTUAL || call.opcode==Opcodes.INVOKESPECIAL)) { publicOriginal++;continue; }
                String expected=DECISIONS.get(key);
                boolean right=expected!=null && !call.itf && (bridged
                    ? helper && call.opcode==Opcodes.INVOKESTATIC && call.method.equals(decision(expected))
                    : original && (call.opcode==Opcodes.INVOKEVIRTUAL || call.opcode==Opcodes.INVOKESPECIAL) && call.method.equals(expected));
                if(!right)throw mismatch("unexpected predicate edge "+key+" -> "+call.owner+"."+call.method);
                seen.merge(key,1,Integer::sum);decisions++;
            }
            String wrapper=WRAPPERS.get(key);
            long resultCalls=path.calls.stream().filter(call -> call.owner.equals(HELPER) && !call.method.startsWith("decision(")
                && !(key.equals(NEED) && call.opcode==Opcodes.INVOKESTATIC && !call.itf && call.method.equals("earlyNeeds(L"+CELL+";"+TREE+")I"))
                && !(key.equals("renderInternal()V") && call.opcode==Opcodes.INVOKESTATIC && !call.itf && call.method.equals("finishFrame()V"))).count();
            if(bridged && wrapper!=null) {
                String desc=wrapper+"(ZL"+CELL+";"+(key.startsWith("pzopt")?TREE:OBJECT)+")Z";
                long correct=path.calls.stream().filter(call -> call.owner.equals(HELPER) && call.opcode==Opcodes.INVOKESTATIC && !call.itf && call.method.equals(desc)).count();
                if(correct!=path.returns || resultCalls!=correct)throw mismatch("unwrapped boolean return in "+key);
            } else if(resultCalls!=0)throw mismatch("unexpected result hook in "+key);
        }
        if(publicOriginal!=1)throw mismatch("public predicate recursion boundary changed");
        for(String key:DECISIONS.keySet())if(seen.getOrDefault(key,0)!=1)throw mismatch("expected one decision edge in "+key);
        int gates=configReads.entrySet().stream().filter(entry -> entry.getKey().endsWith(":TREES_IN_CHUNK_TEXTUREZ")).mapToInt(Map.Entry::getValue).sum();
        return new Shape(decisions,WRAPPERS.size(),gates,Map.copyOf(configReads));
    }
    public static void verifyPreserved(Shape original,Shape patched) {
        if(original.decisions!=patched.decisions || original.wrappers!=patched.wrappers || !original.configReads.equals(patched.configReads))
            throw mismatch("transformation changed cache/configuration gates");
    }
}
