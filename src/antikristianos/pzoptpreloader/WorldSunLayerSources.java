package antikristianos.pzoptpreloader;

import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import java.util.regex.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import zombie.core.SpriteRenderer;
import zombie.core.sprite.SpriteRenderState;

/** Bounded own sun-coloured sources. Native RGB multiply/ratio math precedes the native texture fetch. */
public final class WorldSunLayerSources {
    private static final Map<Integer,Source> SOURCES=new HashMap<>();
    private static final Map<Integer,Source> BY_COLOUR=new HashMap<>();
    private static final Map<Integer,Set<Source>> BY_PARENT=new HashMap<>();
    private static final Map<Integer,Program> PROGRAMS=new HashMap<>();
    private static final ArrayDeque<Work> QUEUE=new ArrayDeque<>();
    private static final ArrayList<Work> OPEN=new ArrayList<>();
    private static SpriteRenderState frameState;private static long frameTime=Long.MIN_VALUE,frame;
    private static int used,sharpVao;private static boolean replay;
    private static volatile boolean busy,resetRequested;private static volatile boolean wasEnabled=true;
    private static volatile boolean shownAcknowledged;
    private static volatile int cohortPending;
    private static volatile long admissions,appliedAdmissions,releasedAdmissions,resetSequence;
    public static long sourceUpdates;
    private WorldSunLayerSources(){ }
    private static final class Image {
        int id,fbo,w,h,format,levels;
        void free(){if(id!=0)GL11.glDeleteTextures(id);if(fbo!=0)GL30.glDeleteFramebuffers(fbo);id=fbo=0;}
    }
    private static final class Source {
        int depth,parent,original,w,h;long key,generation,lastRequest=Long.MIN_VALUE;
        Image current;Work tail;final ArrayList<Work> work=new ArrayList<>();
        void free(){for(Work w:work)w.free();work.clear();if(current!=null)current.free();current=null;tail=null;}
    }
    private record Uniform(int nativeLocation,int ownLocation,String type,String name){ }
    private record Program(int id,List<Uniform> uniforms){ }
    private record Value(Uniform uniform,int integer,float[] floats){ }
    private record Draw(Program program,List<Value> values,int level,int[] viewport,boolean blend,int[] factors,byte[] mask){ }
    private static final class Work {
        Source owner;long generation;Work previous;Image base,result;
        final Map<Integer,Image> inputs=new HashMap<>();final ArrayList<Draw> draws=new ArrayList<>();
        boolean sealed,queued,built,mips,sharp,counted;
        void free(){if(counted&&!built){counted=false;cohortPending--;}for(Image i:inputs.values())i.free();inputs.clear();if(base!=null)base.free();base=null;if(result!=null)result.free();result=null;}
    }
    private static final class Access {
        static final Access INSTANCE=new Access();
        final Field colour,depth,w,h,key,index,entries,multiply,ratio,entryOwner,stageColour,stageDepth,stageW,stageH,entryKey,sunOn,budget;
        Access(){try{Class<?> j=Class.forName("pzopt.ChunkAo$Job"),g=Class.forName("pzopt.ChunkAo$Gl"),e=Class.forName("pzopt.ChunkAo$Entry"),c=Class.forName("pzopt.Config");colour=f(j,"colorTex");depth=f(j,"srcTex");w=f(j,"w");h=f(j,"h");key=f(j,"key");index=f(j,"index");entries=f(g,"entries");multiply=f(g,"mulProgram");ratio=f(g,"ratioProgram");entryOwner=f(e,"colorTex");stageColour=f(e,"sColorTex");stageDepth=f(e,"sDepthTex");stageW=f(e,"sW");stageH=f(e,"sH");entryKey=f(e,"key");sunOn=f(c,"SUN_SHADOWS");budget=f(c,"SUN_COMPUTE_BUDGET");}catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
        static Field f(Class<?> c,String n)throws ReflectiveOperationException{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    }
    private static long clock(){SpriteRenderState s=SpriteRenderer.instance.states.getRendering();if(s!=frameState||s.time!=frameTime){frameState=s;frameTime=s.time;frame++;used=0;}return frame;}
    /** Producer-side successful native sun-step admission; no wind clock or wind data is involved. */
    public static boolean admitted(boolean changed){if(changed&&WorldSunLayerShaders.enabled())try{if(Access.INSTANCE.sunOn.getBoolean(null)){admissions++;shownAcknowledged=false;busy=true;}else busy=false;}catch(IllegalAccessException error){throw new IllegalStateException(error);}return changed;}
    private static boolean sunEnabled(){try{return Access.INSTANCE.sunOn.getBoolean(null);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    public static boolean allowUpdate(){if(!WorldSunLayerShaders.enabled())return true;try{if(!Access.INSTANCE.sunOn.getBoolean(null)){if(wasEnabled){resetSequence=admissions;resetRequested=true;wasEnabled=false;}busy=false;return true;}wasEnabled=true;}catch(IllegalAccessException e){throw new IllegalStateException(e);}return admissions<=releasedAdmissions;}
    public static void nativeApplied(){appliedAdmissions=admissions;for(Source s:SOURCES.values())for(Work w:s.work)if(!w.built&&w.generation==s.generation){if(!w.counted){w.counted=true;cohortPending++;}enqueue(w);}}
    public static void shown(){shownAcknowledged=true;releaseAdmission();}
    private static void releaseAdmission(){if(shownAcknowledged&&cohortPending==0&&appliedAdmissions>=admissions){releasedAdmissions=appliedAdmissions;busy=false;}}
    public static void noQueuedStep(){appliedAdmissions=admissions;shownAcknowledged=true;if(cohortPending==0){releasedAdmissions=appliedAdmissions;busy=false;}}
    /** At the already configured native draw. Only immutable inputs are captured; no own RGB update occurs here. */
    public static void drawArrays(int mode,int first,int count,Object gl,Object job){
        capture(mode,first,count,gl,job);
        WallReflectionLighting.drawArrays(mode,first,count,gl,job);
    }
    private static void capture(int mode,int first,int count,Object gl,Object job){
        if(resetRequested)cancelPrevious();if(replay||!WorldSunLayerShaders.enabled()||!sunEnabled())return;
        try{Access a=Access.INSTANCE;int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);boolean fresh=p==a.multiply.getInt(gl);if(!fresh&&p!=a.ratio.getInt(gl))return;
            int depth=((int[])a.depth.get(job))[0],original=a.colour.getInt(job);if(original<=0&&fresh)original=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            Object entry=((Map<?,?>)a.entries.get(gl)).get(a.index.getInt(job));int parent=entry==null?depth:a.entryOwner.getInt(entry);if(parent<=0)parent=depth;
            captureDraw(depth,parent,original,a.w.getInt(job),a.h.getInt(job),a.key.getLong(job),fresh,p,fresh?WorldSunLayerShaders.colourMultiply():WorldSunLayerShaders.colourRatio());
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Native sun-colour capture",e);}
    }
    public static void staged(int mode,int first,int count,Object gl,Object entry){
        if(resetRequested)cancelPrevious();if(replay||entry==null||!WorldSunLayerShaders.enabled()||!sunEnabled())return;
        try{Access a=Access.INSTANCE;if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=a.ratio.getInt(gl))return;captureDraw(a.stageDepth.getInt(entry),a.entryOwner.getInt(entry),a.stageColour.getInt(entry),a.stageW.getInt(entry),a.stageH.getInt(entry),a.entryKey.getLong(entry),false,a.ratio.getInt(gl),WorldSunLayerShaders.colourRatio());}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Native staged sun-colour capture",e);}
    }
    public static void mirrorPhysical(int mode,int first,int count,int depth,int original){
        Source s=SOURCES.get(depth);if(s==null||s.original!=original)return;int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        captureDraw(depth,s.parent,original,s.w,s.h,s.key,false,p,WorldSunLayerShaders.colourRatio());
    }
    private static void captureDraw(int depth,int parent,int original,int w,int h,long key,boolean fresh,int nativeProgram,String fragment){
        if(depth<=0||original<=0||w<=0||h<=0)return;
        int level=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
        Source s=SOURCES.get(depth);if(s==null||s.original!=original||s.key!=key||s.w!=w||s.h!=h){if(s!=null){BY_COLOUR.remove(s.original,s);Set<Source> family=BY_PARENT.get(s.parent);if(family!=null){family.remove(s);if(family.isEmpty())BY_PARENT.remove(s.parent);}Source stale=s;QUEUE.removeIf(x->x.owner==stale);OPEN.removeIf(x->x.owner==stale);s.free();}s=new Source();s.depth=depth;s.parent=parent;s.original=original;s.key=key;s.w=w;s.h=h;SOURCES.put(depth,s);BY_COLOUR.put(original,s);BY_PARENT.computeIfAbsent(parent,k->new HashSet<>()).add(s);fresh=true;}
        if(fresh){Source freshSource=s;QUEUE.removeIf(x->x.owner==freshSource);OPEN.removeIf(x->x.owner==freshSource);s.generation++;s.free();}
        Work work=s.tail;
        if(level==0){Work next=new Work();next.owner=s;next.generation=s.generation;next.previous=work;if(work==null)next.base=copy(original,w,h);s.work.add(next);s.tail=next;work=next;if(fresh)work.sealed=true;else OPEN.add(work);}
        if(work==null)throw new IllegalStateException("Own sun source begins at a nonzero mip");
        Program program=PROGRAMS.computeIfAbsent(nativeProgram,p->program(p,fragment));List<Value> values=new ArrayList<>();int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try{for(Uniform u:program.uniforms){
            if(u.type.equals("sampler2D")||u.type.equals("int")){int value=GL20.glGetUniformi(nativeProgram,u.nativeLocation);values.add(new Value(u,value,null));if(u.type.equals("sampler2D")&&!u.name.equals("Depth")&&!work.inputs.containsKey(value)){GL13.glActiveTexture(GL13.GL_TEXTURE0+value);int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if(texture>0)work.inputs.put(value,copy(texture,-1,-1));}}
            else{float[] v=new float[u.type.equals("float")?1:4];GL20.glGetUniformfv(nativeProgram,u.nativeLocation,v);values.add(new Value(u,0,v));}
        }}finally{GL13.glActiveTexture(active);}
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);int[] factors=new int[6],names={GL14.GL_BLEND_SRC_RGB,GL14.GL_BLEND_DST_RGB,GL14.GL_BLEND_SRC_ALPHA,GL14.GL_BLEND_DST_ALPHA,GL20.GL_BLEND_EQUATION_RGB,GL20.GL_BLEND_EQUATION_ALPHA};for(int i=0;i<names.length;i++)factors[i]=GL30.glGetIntegeri(names[i],0);ByteBuffer mask=BufferUtils.createByteBuffer(4);GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,0,mask);byte[] masks=new byte[4];for(int i=0;i<4;i++)masks[i]=mask.get(i);
        work.draws.add(new Draw(program,List.copyOf(values),level,viewport,GL30.glIsEnabledi(GL11.GL_BLEND,0),factors,masks));
    }
    /** A native job return seals its complete ordered level list. */
    public static void seal(){for(Work w:OPEN)if(w.generation==w.owner.generation){w.sealed=true;if(w.owner.lastRequest>=clock()-1)enqueue(w);}OPEN.clear();}
    public static void mips(Object object){if(!WorldSunLayerShaders.enabled())return;zombie.iso.fboRenderChunk.FBORenderChunk chunk=(zombie.iso.fboRenderChunk.FBORenderChunk)object;if(chunk.tex==null||chunk.highRes||!zombie.debug.DebugOptions.instance.fboRenderChunk.mipMaps.getValue())return;Source s=BY_COLOUR.get(chunk.tex.getID());if(s!=null&&s.tail!=null){s.tail.mips=true;s.tail.sealed=true;}}
    public static void sharp(int original){Source s=BY_COLOUR.get(original);if(s!=null&&s.tail!=null)s.tail.sharp=true;}
    private static void enqueue(Work w){if(w==null||w.built||w.queued||!w.sealed)return;if(w.previous!=null&&!w.previous.built)enqueue(w.previous);w.queued=true;QUEUE.addLast(w);}
    private static void drain(){
        long now=clock();int limit;try{limit=Math.max(1,Math.min(4,Access.INSTANCE.budget.getInt(null)));}catch(IllegalAccessException e){throw new IllegalStateException(e);}
        while(used<limit&&!QUEUE.isEmpty()){Work w=QUEUE.removeFirst();w.queued=false;if(w.generation!=w.owner.generation||!w.sealed||w.built)continue;if(!w.counted&&w.owner.lastRequest<now-1)continue;
            if(w.previous!=null&&!w.previous.built){enqueue(w.previous);enqueue(w);continue;}
            try{build(w);used++;sourceUpdates++;}catch(Throwable e){releasedAdmissions=admissions;busy=false;throw e;}
        }
    }
    private static void build(Work w){
        Image base=w.previous==null?w.base:w.previous.result;if(base==null||base.id==0)throw new IllegalStateException("Own source predecessor was released");w.result=copy(base.id,base.w,base.h);
        try(WorldSunLayerState.RenderScope scope=new WorldSunLayerState.RenderScope(w.result.id,w.result.w,w.result.h)){
            for(Draw d:w.draws){GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,w.result.id,d.level);GL11.glViewport(d.viewport[0],d.viewport[1],d.viewport[2],d.viewport[3]);GL20.glUseProgram(d.program.id);
                for(Value v:d.values){Uniform u=v.uniform;if(u.type.equals("sampler2D")||u.type.equals("int"))GL20.glUniform1i(u.ownLocation,v.integer);else if(u.type.equals("float"))GL20.glUniform1f(u.ownLocation,v.floats[0]);else GL20.glUniform4f(u.ownLocation,v.floats[0],v.floats[1],v.floats[2],v.floats[3]);}
                for(var input:w.inputs.entrySet()){GL13.glActiveTexture(GL13.GL_TEXTURE0+input.getKey());GL11.glBindTexture(GL11.GL_TEXTURE_2D,input.getValue().id);}
                GL13.glActiveTexture(GL13.GL_TEXTURE2);GL11.glBindTexture(GL11.GL_TEXTURE_2D,w.owner.depth);if(d.blend)GL30.glEnablei(GL11.GL_BLEND,0);else GL30.glDisablei(GL11.GL_BLEND,0);GL40.glBlendFuncSeparatei(0,d.factors[0],d.factors[1],d.factors[2],d.factors[3]);GL40.glBlendEquationSeparatei(0,d.factors[4],d.factors[5]);GL30.glColorMaski(0,d.mask[0]!=0,d.mask[1]!=0,d.mask[2]!=0,d.mask[3]!=0);WorldSunLayerState.quadFor();
            }
            if(w.mips){GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,w.result.id);GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);}
            if(w.sharp)sharpPass(w.result);
        }
        w.built=true;if(w.counted){w.counted=false;cohortPending--;}releaseAdmission();for(Image i:w.inputs.values())i.free();w.inputs.clear();if(w.base!=null){w.base.free();w.base=null;}
    }
    private static final class Mips {
        static final Mips INSTANCE=new Mips();final Method pass;final Field program,sampler,quad;
        Mips(){try{Class<?> c=Class.forName("pzopt.SpriteMips");pass=c.getDeclaredMethod("pass",int.class,int.class,int.class);pass.setAccessible(true);program=Access.f(c,"program");sampler=Access.f(c,"sampler");quad=Access.f(c,"quadVbo");}catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
    }
    private static void sharpPass(Image image){try{
        Mips m=Mips.INSTANCE;int p=m.program.getInt(null);if(p==0)throw new IllegalStateException("Original sharp-mip program disappeared before its receiver replay");
        GL20.glUseProgram(p);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL33.glBindSampler(0,m.sampler.getInt(null));
        GL30.glDisablei(GL11.GL_BLEND,0);GL30.glColorMaski(0,true,true,true,true);if(sharpVao==0)sharpVao=GL30.glGenVertexArrays();GL30.glBindVertexArray(sharpVao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,m.quad.getInt(null));GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,8,0L);GL20.glEnableVertexAttribArray(0);m.pass.invoke(null,image.id,image.w,image.h);
    }catch(ReflectiveOperationException e){throw new IllegalStateException("Native sharp mip receiver",e);}}
    public static boolean requested(int parent,long ignored){long now=clock();for(Source s:BY_PARENT.getOrDefault(parent,Set.of()))if(s.lastRequest>=now-1)return true;return false;}
    public static boolean ready(int parent){long now=clock();for(Source s:BY_PARENT.getOrDefault(parent,Set.of()))if(s.lastRequest>=now-1&&s.tail!=null&&!s.tail.built)return false;return true;}
    public static void publish(int parent){for(Source s:BY_PARENT.getOrDefault(parent,Set.of()))if(s.tail!=null&&s.tail.built){Image next=s.tail.result;if(s.current!=null&&s.current!=next)s.current.free();s.current=next;for(Work w:s.work)if(w!=s.tail&&w.result!=s.current){w.free();}s.tail.previous=null;s.work.removeIf(w->w!=s.tail);}}
    public static void requestFamily(int parent){if(resetRequested)cancelPrevious();long now=clock();for(Source s:BY_PARENT.getOrDefault(parent,Set.of())){s.lastRequest=now;enqueue(s.tail);}drain();WorldSunLayerState.advanceSources();}
    public static boolean currentLatest(int depth){Source s=SOURCES.get(depth);return s!=null&&s.tail!=null&&s.tail.built&&s.current==s.tail.result;}
    public static int texture(int depth,int original){if(resetRequested)cancelPrevious();Source s=SOURCES.get(depth);if(s!=null&&s.original==original){s.lastRequest=clock();enqueue(s.tail);}drain();WorldSunLayerState.advanceSources();if(s==null||s.original!=original)return 0;try{if(!Access.INSTANCE.sunOn.getBoolean(null))return 0;}catch(IllegalAccessException e){throw new IllegalStateException(e);}if(!WorldSunLayerState.grouped(s.parent)&&s.tail!=null&&s.tail.built&&s.current!=s.tail.result)publish(s.parent);return s.current==null?0:s.current.id;}
    private static Program program(int nativeProgram,String fragment){int p=WorldSunLayerState.programFor(fragment);List<Uniform> uniforms=new ArrayList<>();Matcher m=Pattern.compile("uniform\\s+(sampler2D|vec4|float|int)\\s+([^;]+);").matcher(fragment);while(m.find())for(String name:m.group(2).split(",")){name=name.trim();int n=GL20.glGetUniformLocation(nativeProgram,name),o=GL20.glGetUniformLocation(p,name);if(n>=0&&o>=0)uniforms.add(new Uniform(n,o,m.group(1),name));}return new Program(p,List.copyOf(uniforms));}
    private static Image copy(int original,int wantedW,int wantedH){
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),temporary=0;boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);Image out=new Image();
        try{GL11.glBindTexture(GL11.GL_TEXTURE_2D,original);out.w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);out.h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);out.format=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);out.levels=32-Integer.numberOfLeadingZeros(Math.max(out.w,out.h));out.id=GL11.glGenTextures();out.fbo=GL30.glGenFramebuffers();GL11.glBindTexture(GL11.GL_TEXTURE_2D,out.id);for(int i=0;i<out.levels;i++)GL11.glTexImage2D(GL11.GL_TEXTURE_2D,i,out.format,Math.max(1,out.w>>i),Math.max(1,out.h>>i),0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            temporary=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,temporary);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,out.fbo);GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);GL11.glDisable(GL11.GL_SCISSOR_TEST);for(int i=0;i<out.levels;i++){GL11.glBindTexture(GL11.GL_TEXTURE_2D,original);if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,i,GL11.GL_TEXTURE_WIDTH)<=0)break;GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,original,i);GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,out.id,i);int w=Math.max(1,out.w>>i),h=Math.max(1,out.h>>i);GL30.glBlitFramebuffer(0,0,w,h,0,0,w,h,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);}return out;
        }catch(Throwable error){out.free();throw error;}finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,bound);GL13.glActiveTexture(active);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);if(temporary!=0)GL30.glDeleteFramebuffers(temporary);if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);}
    }
    public static void matchSampling(int original,int own){if(own==0||own==original)return;int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);int[] names={GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_TEXTURE_WRAP_S,GL11.GL_TEXTURE_WRAP_T,GL12.GL_TEXTURE_BASE_LEVEL,GL12.GL_TEXTURE_MAX_LEVEL};GL11.glBindTexture(GL11.GL_TEXTURE_2D,original);int[] values=new int[names.length];for(int i=0;i<names.length;i++)values[i]=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,names[i]);float min=GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MIN_LOD),max=GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LOD),bias=GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_LOD_BIAS);try{GL11.glBindTexture(GL11.GL_TEXTURE_2D,own);for(int i=0;i<names.length;i++)GL11.glTexParameteri(GL11.GL_TEXTURE_2D,names[i],values[i]);GL11.glTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MIN_LOD,min);GL11.glTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LOD,max);GL11.glTexParameterf(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_LOD_BIAS,bias);}finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,bound);}}
    public static void free(int parent){for(var it=SOURCES.values().iterator();it.hasNext();){Source s=it.next();if(s.parent==parent||s.depth==parent){QUEUE.removeIf(w->w.owner==s);OPEN.removeIf(w->w.owner==s);BY_COLOUR.remove(s.original,s);Set<Source> family=BY_PARENT.get(s.parent);if(family!=null){family.remove(s);if(family.isEmpty())BY_PARENT.remove(s.parent);}s.free();it.remove();}}if(SOURCES.isEmpty()){releasedAdmissions=admissions;busy=false;}}
    private static void clearImages(){for(Source s:SOURCES.values())s.free();SOURCES.clear();BY_COLOUR.clear();BY_PARENT.clear();QUEUE.clear();OPEN.clear();for(Program p:PROGRAMS.values())GL20.glDeleteProgram(p.id);PROGRAMS.clear();if(sharpVao!=0)GL30.glDeleteVertexArrays(sharpVao);sharpVao=0;frameState=null;frameTime=Long.MIN_VALUE;frame=0;used=0;cohortPending=0;}
    private static void cancelPrevious(){long sequence=resetSequence;clearImages();appliedAdmissions=Math.max(appliedAdmissions,sequence);releasedAdmissions=Math.max(releasedAdmissions,sequence);resetRequested=false;busy=admissions>releasedAdmissions;}
    public static void clear(){clearImages();busy=false;resetRequested=false;shownAcknowledged=false;admissions=appliedAdmissions=releasedAdmissions=resetSequence=0;}
}
