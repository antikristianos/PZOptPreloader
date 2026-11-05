package antikristianos.pzoptpreloader;

/** Orders existing grants without changing their tiers, capacity or native draw decisions. */
public final class SchedulerProgressPolicy {
    private static final int CUTAWAY=1;
    private SchedulerProgressPolicy() { }

    /** In-place primitive heapsort: no per-frame arrays, boxed indices or world lookups. */
    public static void sort(long[] keys,int from,int to,int[] classes,int[] distances,int[] ages,int[] levels,int arrivalWait) {
        if(from<0||to<from||to>keys.length)throw new IndexOutOfBoundsException("Scheduler key range");
        int size=to-from;
        for(int root=from+size/2-1;root>=from;root--)sift(keys,root,from,to,classes,distances,ages,levels,arrivalWait);
        for(int end=to-1;end>from;end--){
            long saved=keys[from];keys[from]=keys[end];keys[end]=saved;
            sift(keys,from,from,end,classes,distances,ages,levels,arrivalWait);
        }
    }
    private static void sift(long[] keys,int root,int from,int to,int[] classes,int[] distances,int[] ages,int[] levels,int arrivalWait) {
        while(true){
            int child=from+(root-from)*2+1;if(child>=to)return;
            if(child+1<to&&compare(keys[child],keys[child+1],classes,distances,ages,levels,arrivalWait)<0)child++;
            if(compare(keys[root],keys[child],classes,distances,ages,levels,arrivalWait)>=0)return;
            long saved=keys[root];keys[root]=keys[child];keys[child]=saved;root=child;
        }
    }
    private static int compare(long left,long right,int[] classes,int[] distances,int[] ages,int[] levels,int arrivalWait) {
        int lt=(int)(left>>>48),rt=(int)(right>>>48);
        if(lt!=rt)return Integer.compare(lt,rt);
        // MUST and native level-change bursts retain their exact native ordering.
        if(lt==0||lt>3)return Long.compare(left,right);
        int a=(int)(left&0xffff),b=(int)(right&0xffff),result;
        int ageA=Math.max(0,ages[a]),ageB=Math.max(0,ages[b]);
        if(lt==1){
            // Keep lower creation levels ahead of upper floors; prevent a stream of fresh
            // nearby creations from indefinitely replacing already waiting arrivals.
            result=Integer.compare(levels[a],levels[b]);if(result!=0)return result;
            boolean oldA=ageA>=Math.max(0,arrivalWait),oldB=ageB>=Math.max(0,arrivalWait);
            if(oldA!=oldB)return oldA?-1:1;
            if(oldA){result=Integer.compare(ageB,ageA);if(result!=0)return result;}
            result=Integer.compare(distances[a],distances[b]);if(result!=0)return result;
            result=Integer.compare(ageB,ageA);if(result!=0)return result;
        }else{
            // Cutaway safety is retained even when other classes have accumulated age.
            boolean cutA=classes[a]==CUTAWAY,cutB=classes[b]==CUTAWAY;
            if(cutA!=cutB)return cutA?-1:1;
            if(lt==2){
                result=Integer.compare(ageB,ageA);if(result!=0)return result;
                result=Integer.compare(distances[a],distances[b]);if(result!=0)return result;
            }else{
                result=Integer.compare(distances[a],distances[b]);if(result!=0)return result;
                result=Integer.compare(ageB,ageA);if(result!=0)return result;
            }
        }
        result=Integer.compare(classes[a],classes[b]);
        return result!=0?result:Integer.compare(a,b);
    }
}
