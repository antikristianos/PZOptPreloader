package antikristianos.pzoptpreloader;

import zombie.GameWindow;
import zombie.gameStates.IngameState;
import zombie.iso.IsoCell;
import zombie.iso.IsoWorld;
import zombie.core.physics.WorldSimulation;

/** A slack producer may consume only a recent request from the same playable world. */
public final class ChunkHandoffLifetime {
    private static IsoCell requestedCell;
    private static int requestedFrame;
    private ChunkHandoffLifetime() { }
    private static IsoCell readyCell() {
        if (GameWindow.states == null || !(GameWindow.states.current instanceof IngameState)
                || IngameState.loading || IsoWorld.instance == null
                || WorldSimulation.instance == null || !WorldSimulation.instance.created) return null;
        IsoCell cell = IsoWorld.instance.currentCell;
        return cell != null && cell.chunkMap != null && cell.chunkMap.length > 0
                && cell.chunkMap[0] != null ? cell : null;
    }
    public static boolean eligible(boolean enabled) {
        requestedCell = null;
        return enabled && readyCell() != null;
    }
    public static void request(int frame) {
        requestedCell = readyCell();
        requestedFrame = frame;
    }
    public static boolean live(int frame) {
        int age = frame - requestedFrame;
        return requestedCell != null && age >= 0 && age <= 1 && readyCell() == requestedCell;
    }
    public static void reset() { requestedCell = null; }
}
