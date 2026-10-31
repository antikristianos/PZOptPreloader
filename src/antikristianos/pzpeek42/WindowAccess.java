package antikristianos.pzpeek42;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoGridSquare;

/** The same raw fields used by PeekAView's stair scopes, with prepared typed access. */
public final class WindowAccess {
    private static final VarHandle X,Y,Z,CURRENT;
    static{
        try{
            var lookup=MethodHandles.privateLookupIn(IsoMovingObject.class,MethodHandles.lookup());
            X=lookup.findVarHandle(IsoMovingObject.class,"x",float.class);
            Y=lookup.findVarHandle(IsoMovingObject.class,"y",float.class);
            Z=lookup.findVarHandle(IsoMovingObject.class,"z",float.class);
            CURRENT=lookup.findVarHandle(IsoMovingObject.class,"current",IsoGridSquare.class);
        }catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    public static void initialize(){ }
    public static boolean write(IsoGameCharacter character,float x,float y,float z){X.set(character,x);Y.set(character,y);Z.set(character,z);return true;}
    public static IsoGridSquare current(IsoMovingObject object){return (IsoGridSquare)CURRENT.get(object);}
}
