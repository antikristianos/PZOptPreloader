package antikristianos.pzoptpreloader;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.lwjgl.opengl.*;
import zombie.core.SpriteRenderer;
import zombie.core.sprite.SpriteRenderState;
import zombie.core.textures.TextureDraw;

/** Render-thread sun receivers. Geometry/AO colours are never attached to these framebuffers. */
public final class SunCompositeState {
    private static final int CURRENT_UNIT=30,OLD_UNIT=31;
    private static final Map<Integer,Term> TERMS=new HashMap<>(),INDICES=new HashMap<>();
    private static final Map<Integer,int[]> PROGRAMS=new HashMap<>();
    private record Request(Term term,Layer layer){ }
    private static final ArrayDeque<Request> PENDING=new ArrayDeque<>();
    private static long budgetFrame=Long.MIN_VALUE;private static int budgetUsed;
    private static SpriteRenderState renderState;
    private static long renderStamp=Long.MIN_VALUE,renderSerial;
    private static long shaderGeneration=Long.MIN_VALUE;
    private static int factorProgram,mixProgram,vao,vbo;
    private static Term ratioTerm;
    private record Shown(float[] world,float strength,long sequence,long time){ }
    private static final Map<Object,Shown> QUEUED_SHOWN=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final AtomicReference<Shown> READY_SHOWN=new AtomicReference<>();
    private static final ThreadLocal<Boolean> CONSUMING=ThreadLocal.withInitial(()->false);
    private static volatile boolean holdShown;private static long shownSequence,publishedShownSequence;
    private SunCompositeState(){ }
    private static final class Group {long frame,t0;Shown shown;}
    private static final class Term {
        Object entry;int index,depth,w,h;long key,revision;float ppu,scale;boolean pending;
        Group group;
        Object[] aoSignature,pendingSignature;boolean pendingSameAo;int colour;
        final Map<Integer,Layer> layers=new HashMap<>();
    }
    private static final class Layer {int receiver,w,h,current,old,prepared;long revision,preparedRevision,t0,lastRequest=Long.MIN_VALUE;boolean queued;}
    private static final class Access {
        static final Access INSTANCE=new Access();
        final Field entries,valid,owner,tex,stage,key,aw,ah,index,w,h,n,src,kind,ppu,applyN,applyIdx,fadeMs;
        final Method scale,edgeLeaves,stepApplied;final Field edgeAware,edgeShade,flip,unitsPerDepth,sunBudget,devBits,syncMax,stagedSun,shownT0,shownTo;
        final Field[] aoFields,environmentFields;final Field sunDir,ratioProgram,colour;
        Access(){try{
            Class<?> a=Class.forName("pzopt.ChunkAo"),g=Class.forName("pzopt.ChunkAo$Gl"),e=Class.forName("pzopt.ChunkAo$Entry"),j=Class.forName("pzopt.ChunkAo$Job"),c=Class.forName("pzopt.Config");
            entries=f(g,"entries");valid=f(e,"valid");owner=f(e,"colorTex");tex=f(e,"tex");stage=f(e,"staged");key=f(e,"key");aw=f(e,"aw");ah=f(e,"ah");
            index=f(j,"index");w=f(j,"w");h=f(j,"h");n=f(j,"n");src=f(j,"srcTex");kind=f(j,"kind");ppu=f(j,"ppu");applyN=f(j,"applyN");applyIdx=f(j,"applyIdx");
            fadeMs=f(c,"SUN_STEP_FADE_MS");edgeAware=f(c,"AO_EDGE_AWARE");edgeShade=f(c,"AO_EDGE_SHADE");flip=f(c,"AO_CHUNK_FLIP");
            boolean newer=RuntimeState.payload!=null&&Compatibility.extendedSource(RuntimeState.payload);
            unitsPerDepth=f(Class.forName("pzopt.AmbientOcclusion"),"UNITS_PER_DEPTH");scale=m(g,"scale");edgeLeaves=newer?m(a,"edgeShadeLeaves"):null;sunBudget=f(c,"SUN_COMPUTE_BUDGET");devBits=newer?f(a,"devBits"):null;syncMax=f(c,"SUN_STEP_SYNC_MAX_FRAMES");
            Class<?> sun=Class.forName("pzopt.SunShadow");stagedSun=f(a,"STAGED_SUN");shownT0=f(sun,"shownT0");shownTo=f(sun,"SHOWN_TO");stepApplied=sun.getDeclaredMethod("stepApplied",float[].class,float.class);stepApplied.setAccessible(true);
            String[] names={"w","h","ppu","n","srcTex","srcX","srcY","srcW","srcH","srcDepth","veg","vegetation","trees","nTrees","treeA","treeB","treeC","treeD","cards","roofLv","isoHalfW","isoInvSA","isoS","isoTop","sun"};aoFields=new Field[names.length];for(int i=0;i<names.length;i++)aoFields[i]=f(j,names[i]);sunDir=f(j,"sunDir");ratioProgram=f(g,"ratioProgram");colour=f(j,"colorTex");
            environmentFields=Arrays.stream(c.getDeclaredFields()).filter(field->Modifier.isStatic(field.getModifiers())&&(field.getName().equals("AO")||field.getName().startsWith("AO_")||field.getName().startsWith("DEV_AO_")||Set.of("SUN_SHADOW_TREES","SUN_SHADOW_TREE_CARDS").contains(field.getName()))).sorted(Comparator.comparing(Field::getName)).toArray(Field[]::new);for(Field field:environmentFields)field.setAccessible(true);
        }catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
        static Field f(Class<?> t,String n)throws ReflectiveOperationException{Field f=t.getDeclaredField(n);f.setAccessible(true);return f;}
        static Method m(Class<?> t,String n)throws ReflectiveOperationException{Method m=t.getDeclaredMethod(n);m.setAccessible(true);return m;}
    }
    /** Called only after the installed GL job commits (staged computations merely remember metadata). */
    public static void changed(Object gl,Object job){
        if(!SunCompositeShaders.enabled())return;
        try{
            Access a=Access.INSTANCE;Map<?,?> entries=(Map<?,?>)a.entries.get(gl);
            if(a.kind.getInt(job)==4){Group group=new Group();group.frame=renderFrame();group.shown=QUEUED_SHOWN.remove(job);boolean changed=false;int[] ids=(int[])a.applyIdx.get(job);for(int i=0;i<a.applyN.getInt(job);i++){
                Object e=entries.get(ids[i]);if(e==null||!a.valid.getBoolean(e)||a.stage.getBoolean(e))continue;
                Term t=TERMS.get(a.owner.getInt(e));if(t!=null&&t.entry==e&&t.pending){t.pending=false;t.revision++;t.group=group;t.aoSignature=t.pendingSignature;t.pendingSignature=null;changed=true;}
            }ratioTerm=null;if(!changed)publishShown(group,System.nanoTime());return;}
            int idx=a.index.getInt(job);Object e=entries.get(idx);if(e==null||!a.valid.getBoolean(e)||a.n.getInt(job)<1)return;
            int depth=((int[])a.src.get(job))[0];if(depth<=0||a.owner.getInt(e)!=depth)return;
            long key=a.key.getLong(e);Term t=INDICES.get(idx);
            if(t!=null&&(t.depth!=depth||t.key!=key||t.entry!=e)){free(t.depth);t=null;}
            if(t==null){t=new Term();t.index=idx;t.depth=depth;t.key=key;t.entry=e;TERMS.put(depth,t);INDICES.put(idx,t);}
            t.w=a.w.getInt(job);t.h=a.h.getInt(job);t.ppu=a.ppu.getFloat(job);t.scale=(Float)a.scale.invoke(null);
            Object[] signature=aoSignature(a,job);t.colour=a.colour.getInt(job);
            t.pending=a.stage.getBoolean(e);if(t.pending){t.pendingSignature=signature;t.pendingSameAo=t.aoSignature!=null&&Arrays.deepEquals(t.aoSignature,signature);}else{t.aoSignature=signature;t.pendingSignature=null;t.pendingSameAo=false;t.revision++;t.group=null;}
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Sun layer commit metadata",e);}
    }
    private static boolean owned(Term t){try{Access a=Access.INSTANCE;return a.valid.getBoolean(t.entry)&&a.owner.getInt(t.entry)==t.depth&&a.key.getLong(t.entry)==t.key;}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    /** The producer's ChunkAo.frames may advance while this same queued image is still drawing. */
    private static long renderFrame(){SpriteRenderState state=SpriteRenderer.instance.states.getRendering();long time=state.time;if(state!=renderState||time!=renderStamp){renderState=state;renderStamp=time;renderSerial++;}return renderSerial;}
    private static Object[] aoSignature(Access a,Object job)throws IllegalAccessException{Object[] out=new Object[a.aoFields.length+a.environmentFields.length+2];for(int i=0;i<a.aoFields.length;i++){Object v=a.aoFields[i].get(job);out[i]=v instanceof int[] x?x.clone():v instanceof float[] x?x.clone():v;}for(int i=0;i<a.environmentFields.length;i++)out[a.aoFields.length+i]=a.environmentFields[i].get(null);out[out.length-2]=Float.floatToRawIntBits(((float[])a.sunDir.get(job))[3]);out[out.length-1]=a.devBits==null?0:a.devBits.getInt(null);return out;}
    public static void ratioEntry(Object entry){if(!SunCompositeShaders.enabled()){ratioTerm=null;return;}try{ratioTerm=TERMS.get(Access.INSTANCE.owner.getInt(entry));if(ratioTerm!=null&&ratioTerm.entry!=entry)ratioTerm=null;}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    /** Only known unchanged-AO staged sun refreshes omit actual colour and colour-mip draws. */
    public static void ratioDraw(int mode,int first,int count,Object gl){try{
        Term t=ratioTerm;boolean ratio=SunCompositeShaders.enabled()&&GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==Access.INSTANCE.ratioProgram.getInt(gl);
        if(ratio&&t!=null&&owned(t)&&t.pending&&t.pendingSameAo)return;
        GL11.glDrawArrays(mode,first,count);if(ratio&&t!=null&&owned(t))WallReflectionSource.mirrorLighting(mode,first,count,t.colour);
    }catch(IllegalAccessException e){throw new IllegalStateException("Sun cache ratio boundary",e);}}
    /** Game-thread snapshot before queuing the native apply job. */
    public static void queued(Object job){if(!SunCompositeShaders.enabled())return;try{float[] sun=((float[])Access.INSTANCE.stagedSun.get(null)).clone();long sequence=++shownSequence;QUEUED_SHOWN.put(job,new Shown(sun,sun[3],sequence,0));holdShown=true;}catch(IllegalAccessException e){throw new IllegalStateException("Queued displayed sun",e);}}
    public static boolean allowShown(){return !SunCompositeShaders.enabled()||!holdShown||CONSUMING.get();}
    public static boolean fadeSupported(boolean original){return SunCompositeShaders.enabled()||original;}
    private static void publishShown(Group group,long time){if(group.shown!=null){Shown s=group.shown;if(s.sequence<publishedShownSequence)return;publishedShownSequence=s.sequence;READY_SHOWN.set(new Shown(s.world,s.strength,s.sequence,time));}}
    /** Native SunShadow.updateShown invokes this on the game thread, never from a GL worker. */
    public static void consumeShown(){Shown s=READY_SHOWN.getAndSet(null);if(s==null)return;CONSUMING.set(true);try{Access a=Access.INSTANCE;float[] target=(float[])a.shownTo.get(null);boolean same=target[0]==s.world[0]&&target[1]==s.world[1]&&target[2]==s.world[2]&&target[3]==s.strength;a.stepApplied.invoke(null,s.world,s.strength);if(!same&&a.shownT0.getLong(null)!=0)a.shownT0.setLong(null,s.time);if(s.sequence>=shownSequence)holdShown=false;}catch(ReflectiveOperationException e){throw new IllegalStateException("Displayed sun publication",e);}finally{CONSUMING.remove();}}
    public static boolean repairLegacyUniform(boolean successful,Object gl){if(!successful)return false;try{int p=Access.f(gl.getClass(),"aoProgram").getInt(gl);int[] u=(int[])Access.f(gl.getClass(),"uAo").get(gl);u[24]=GL20.glGetUniformLocation(p,"farTree");return true;}catch(ReflectiveOperationException e){throw new IllegalStateException("Legacy native farTree uniform",e);}}
    public static int layer(int originalDepth,int receiverDepth){
        if(!SunCompositeShaders.enabled())return 0;
        Term t=TERMS.get(originalDepth);if(t==null||!owned(t)||t.revision==0)return 0;
        if(receiverDepth<=0)receiverDepth=originalDepth;
        Layer l=t.layers.get(receiverDepth);
        if(l==null){
            // A chunk has at most the visible receiver and its currently owned physical receiver.
            if(receiverDepth!=originalDepth)for(var it=t.layers.entrySet().iterator();it.hasNext();){var e=it.next();if(e.getKey()!=originalDepth){release(e.getValue());it.remove();}}
            l=new Layer();l.receiver=receiverDepth;l.w=t.w;l.h=t.h;t.layers.put(receiverDepth,l);
        }
        expire(l);
        l.lastRequest=renderFrame();
        if(l.revision!=t.revision&&l.preparedRevision!=t.revision&&!l.queued){l.queued=true;PENDING.addLast(new Request(t,l));}
        drain();return l.current;
    }
    private static void drain(){
        try{Access a=Access.INSTANCE;long frame=renderFrame();if(frame!=budgetFrame){publishGroups(frame);budgetFrame=frame;budgetUsed=0;}
            int limit=Math.max(1,Math.min(4,a.sunBudget.getInt(null)));
            while(budgetUsed<limit&&!PENDING.isEmpty()){
                Request request=PENDING.removeFirst();Term t=request.term;Layer l=request.layer;l.queued=false;
                if(TERMS.get(t.depth)!=t||t.layers.get(l.receiver)!=l||!owned(t)||l.revision==t.revision||l.preparedRevision==t.revision||l.lastRequest<frame-1)continue;
                rebuild(t,l);budgetUsed++;
            }
        }catch(IllegalAccessException e){throw new IllegalStateException("Sun layer frame budget",e);}
    }
    private static void rebuild(Term t,Layer l){
            if(l.w!=t.w||l.h!=t.h){release(l);l.w=t.w;l.h=t.h;}
            int next=texture(l.w,l.h);try{factor(t,l,next);}catch(Throwable e){GL11.glDeleteTextures(next);throw e;}
            if(l.prepared!=0)GL11.glDeleteTextures(l.prepared);l.prepared=0;l.preparedRevision=0;
            if(t.group!=null&&t.group.t0==0){l.prepared=next;l.preparedRevision=t.revision;return;}
            commit(t,l,next,t.group==null?System.nanoTime():t.group.t0);
    }
    private static void commit(Term t,Layer l,int next,long time){
            int old=l.current;float rest=remaining(l);
            if(l.old!=0&&old!=0&&rest>0){int mixed=texture(l.w,l.h);try{mix(l,mixed,rest);}catch(Throwable e){GL11.glDeleteTextures(mixed);GL11.glDeleteTextures(next);throw e;}GL11.glDeleteTextures(old);old=mixed;}
            if(l.old!=0)GL11.glDeleteTextures(l.old);l.old=old;l.current=next;l.revision=t.revision;l.t0=time;l.prepared=0;l.preparedRevision=0;expire(l);
    }
    private static void publishGroups(long frame)throws IllegalAccessException{
        Set<Group> groups=Collections.newSetFromMap(new IdentityHashMap<>());for(Term t:TERMS.values())if(t.group!=null&&t.group.t0==0)groups.add(t.group);
        for(Group group:groups){boolean requested=false,ready=true;for(Term t:TERMS.values())if(t.group==group&&owned(t))for(Layer l:t.layers.values())if(l.lastRequest>=frame-1){requested=true;ready&=l.revision==t.revision||l.preparedRevision==t.revision;}
            boolean timeout=frame-group.frame>=Math.max(1,Access.INSTANCE.syncMax.getInt(null));
            if(!requested){if(timeout){group.t0=System.nanoTime();publishShown(group,group.t0);}continue;}
            if(!ready&&!timeout)continue;group.t0=System.nanoTime();publishShown(group,group.t0);
            for(Term t:TERMS.values())if(t.group==group&&owned(t))for(Layer l:t.layers.values())if(l.preparedRevision==t.revision&&l.prepared!=0)commit(t,l,l.prepared,group.t0);
        }
    }
    public static int oldLayer(int originalDepth,int receiverDepth){Term t=TERMS.get(originalDepth);Layer l=t==null?null:t.layers.get(receiverDepth<=0?originalDepth:receiverDepth);if(l==null)return 0;expire(l);return l.old!=0?l.old:l.current;}
    public static float remaining(int originalDepth,int receiverDepth){Term t=TERMS.get(originalDepth);Layer l=t==null?null:t.layers.get(receiverDepth<=0?originalDepth:receiverDepth);return l==null?0:remaining(l);}
    private static float remaining(Layer l){if(l.old==0)return 0;try{int ms=Access.INSTANCE.fadeMs.getInt(null);if(ms<=0)return 0;float f=Math.max(0,Math.min(1,(System.nanoTime()-l.t0)/(ms*1e6f)));return 1-f*f*(3-2*f);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    private static void expire(Layer l){if(l.old!=0&&remaining(l)<=0){GL11.glDeleteTextures(l.old);l.old=0;}}
    private static int texture(int w,int h){int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R16F,w,h,0,GL11.GL_RED,GL11.GL_FLOAT,0L);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);GL11.glBindTexture(GL11.GL_TEXTURE_2D,bound);GL13.glActiveTexture(active);return id;}
    private static void factor(Term t,Layer l,int target){
        if(factorProgram==0)factorProgram=program(SunCompositeShaders.layerFragment());
        try(RenderScope state=new RenderScope(target,l.w,l.h)){
            GL20.glUseProgram(factorProgram);Access a=Access.INSTANCE;
            try{
                bindTexture(0,a.tex.getInt(t.entry));bindTexture(1,l.receiver);
                GL20.glUniform1i(loc(factorProgram,"Ao"),0);GL20.glUniform1i(loc(factorProgram,"Depth"),1);
                GL20.glUniform4f(loc(factorProgram,"m"),t.scale/a.aw.getInt(t.entry),t.scale/a.ah.getInt(t.entry),1,0);
                int bits=a.devBits==null?0:a.devBits.getInt(null);float shade=a.edgeShade.getBoolean(null)&&(bits&256)==0?((bits&512)!=0?2:1):0;
                GL20.glUniform4f(loc(factorProgram,"e"),a.edgeAware.getBoolean(null)?1:0,t.scale,a.unitsPerDepth.getFloat(null)/.12f,shade);
                GL20.glUniform4f(loc(factorProgram,"pn"),t.ppu,a.flip.getBoolean(null)?-1:1,a.edgeLeaves!=null&&(Boolean)a.edgeLeaves.invoke(null)?1:0,0);
                quad();
            }catch(ReflectiveOperationException e){throw new IllegalStateException("Sun receiver parameters",e);}
        }
    }
    private static void mix(Layer l,int target,float rest){
        if(mixProgram==0)mixProgram=program("#version 140\nuniform sampler2D Current,Old;uniform float rest;uniform vec2 size;out vec4 c;void main(){vec2 uv=gl_FragCoord.xy/size;c=vec4(mix(texture(Current,uv).r,texture(Old,uv).r,rest),0,0,1);}");
        try(RenderScope state=new RenderScope(target,l.w,l.h)){GL20.glUseProgram(mixProgram);bindTexture(0,l.current);bindTexture(1,l.old);GL20.glUniform1i(loc(mixProgram,"Current"),0);GL20.glUniform1i(loc(mixProgram,"Old"),1);GL20.glUniform1f(loc(mixProgram,"rest"),rest);GL20.glUniform2f(loc(mixProgram,"size"),l.w,l.h);quad();}
    }
    private static int loc(int program,String name){return GL20.glGetUniformLocation(program,name);}
    private static int program(String fragment){int v=0,f=0,p=0;try{v=shader(GL20.GL_VERTEX_SHADER,"#version 140\nin vec2 position;void main(){gl_Position=vec4(position,0,1);}");f=shader(GL20.GL_FRAGMENT_SHADER,fragment);p=GL20.glCreateProgram();GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL20.glBindAttribLocation(p,0,"position");GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException("Sun layer link: "+GL20.glGetProgramInfoLog(p));return p;}catch(Throwable error){if(p!=0)GL20.glDeleteProgram(p);throw error;}finally{if(v!=0)GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);}}
    private static int shader(int type,String source){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0){String log=GL20.glGetShaderInfoLog(s);GL20.glDeleteShader(s);throw new IllegalStateException("Sun layer shader: "+log);}return s;}
    private static void quad(){if(vao==0){vao=GL30.glGenVertexArrays();vbo=GL15.glGenBuffers();GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,new float[]{-1,-1,1,-1,-1,1,1,1},GL15.GL_STATIC_DRAW);GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,0,0L);GL20.glEnableVertexAttribArray(0);}else GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP,0,4);}
    private static void bindTexture(int unit,int texture){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);}
    private static final class RenderScope implements AutoCloseable {
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),array=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),buffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),fbo;
        final int[] textures=new int[2],samplers=new int[2],viewport=new int[4];
        final ByteBuffer mask=org.lwjgl.BufferUtils.createByteBuffer(4);
        final boolean blend=GL30.glIsEnabledi(GL11.GL_BLEND,0),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),stencil=GL11.glIsEnabled(GL11.GL_STENCIL_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),discard=GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD),depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        RenderScope(int target,int w,int h){GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,0,mask);for(int i=0;i<2;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);samplers[i]=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,i);}fbo=GL30.glGenFramebuffers();try{for(int i=0;i<2;i++)GL33.glBindSampler(i,0);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,target,0);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Sun receiver framebuffer incomplete");GL11.glViewport(0,0,w,h);GL30.glDisablei(GL11.GL_BLEND,0);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);GL30.glColorMaski(0,true,true,true,true);GL11.glDepthMask(false);}catch(Throwable error){close();throw error;}}
        private static void restore(int option,boolean on){if(on)GL11.glEnable(option);else GL11.glDisable(option);}
        public void close(){GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glDeleteFramebuffers(fbo);GL20.glUseProgram(program);GL30.glBindVertexArray(array);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);if(blend)GL30.glEnablei(GL11.GL_BLEND,0);else GL30.glDisablei(GL11.GL_BLEND,0);GL30.glColorMaski(0,mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);restore(GL11.GL_DEPTH_TEST,depth);restore(GL11.GL_SCISSOR_TEST,scissor);restore(GL11.GL_STENCIL_TEST,stencil);restore(GL11.GL_CULL_FACE,cull);restore(GL30.GL_RASTERIZER_DISCARD,discard);GL11.glDepthMask(depthMask);for(int i=0;i<2;i++){bindTexture(i,textures[i]);GL33.glBindSampler(i,samplers[i]);}GL13.glActiveTexture(active);}
    }
    public static Scope bind(int originalDepth,int receiverDepth){
        if(!SunCompositeShaders.enabled())return new Scope();
        long generation=SunCompositeShaders.generation;if(generation!=shaderGeneration){PROGRAMS.clear();shaderGeneration=generation;}
        int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p==0)return new Scope();int[] u=PROGRAMS.computeIfAbsent(p,id->new int[]{loc(id,"pzSunLayerParams"),loc(id,"pzSunLayer"),loc(id,"pzSunLayerOld")});
        if(u[0]<0)return new Scope();
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);if(receiverDepth<=0){GL13.glActiveTexture(GL13.GL_TEXTURE1);receiverDepth=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}if(originalDepth<=0)originalDepth=receiverDepth;
        int current=layer(originalDepth,receiverDepth);Scope state=new Scope(p,u[0],active);
        bindTexture(CURRENT_UNIT,current);bindTexture(OLD_UNIT,oldLayer(originalDepth,receiverDepth));GL20.glUniform1i(u[1],CURRENT_UNIT);GL20.glUniform1i(u[2],OLD_UNIT);GL20.glUniform2f(u[0],current!=0?1:0,remaining(originalDepth,receiverDepth));GL13.glActiveTexture(active);return state;
    }
    public static final class Scope implements AutoCloseable {
        final int program,uniform,active,current,old,currentSampler,oldSampler;
        Scope(){program=0;uniform=-1;active=current=old=currentSampler=oldSampler=0;}
        Scope(int p,int u,int a){program=p;uniform=u;active=a;GL13.glActiveTexture(GL13.GL_TEXTURE0+CURRENT_UNIT);current=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL13.glActiveTexture(GL13.GL_TEXTURE0+OLD_UNIT);old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);currentSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,CURRENT_UNIT);oldSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,OLD_UNIT);GL33.glBindSampler(CURRENT_UNIT,0);GL33.glBindSampler(OLD_UNIT,0);}
        public void close(){if(uniform<0)return;GL20.glUniform2f(uniform,0,0);bindTexture(CURRENT_UNIT,current);bindTexture(OLD_UNIT,old);GL33.glBindSampler(CURRENT_UNIT,currentSampler);GL33.glBindSampler(OLD_UNIT,oldSampler);GL13.glActiveTexture(active);}
    }
    private static void release(Layer l){if(l.current!=0)GL11.glDeleteTextures(l.current);if(l.old!=0)GL11.glDeleteTextures(l.old);if(l.prepared!=0)GL11.glDeleteTextures(l.prepared);l.current=l.old=l.prepared=0;l.revision=l.preparedRevision=0;}
    public static void free(int originalDepth){Term t=TERMS.remove(originalDepth);if(t==null)return;INDICES.remove(t.index,t);PENDING.removeIf(request->request.term==t);for(Layer l:t.layers.values())release(l);t.layers.clear();if(t.group!=null&&t.group.t0==0&&TERMS.values().stream().noneMatch(other->other.group==t.group))publishShown(t.group,System.nanoTime());}
    public static void clear(){for(Term t:TERMS.values())for(Layer l:t.layers.values())release(l);TERMS.clear();INDICES.clear();PENDING.clear();QUEUED_SHOWN.clear();READY_SHOWN.set(null);holdShown=false;PROGRAMS.clear();budgetFrame=Long.MIN_VALUE;budgetUsed=0;renderState=null;renderStamp=Long.MIN_VALUE;renderSerial=0;shaderGeneration=Long.MIN_VALUE;ratioTerm=null;if(factorProgram!=0)GL20.glDeleteProgram(factorProgram);if(mixProgram!=0)GL20.glDeleteProgram(mixProgram);if(vao!=0)GL30.glDeleteVertexArrays(vao);if(vbo!=0)GL15.glDeleteBuffers(vbo);factorProgram=mixProgram=vao=vbo=0;}
}
