package org.momu.pathfinder.navigation.pathfinding;

/**
 * The player's collision box and movement limits (vanilla values). The search always places the player at the
 * centre of a block, so the box is a 0.6 x 0.6 footprint centred on {@code (px, pz)} and 1.8 blocks tall.
 */
final class PlayerBody {
    static final double EPS = 1.0E-4;
    static final double HALF_WIDTH = 0.3;
    static final double HEIGHT = 1.8;
    /** Ledges up to this height are walked onto without jumping (slabs, stairs, carpets, snow...). */
    static final double STEP_HEIGHT = 0.6;
    /**
     * Highest ledge reachable with a jump (the apex is ~1.2522 blocks). The same reach applies when leaving the
     * top of a ladder, vine or scaffolding, or when climbing out of water.
     */
    static final double JUMP_HEIGHT = 1.25;

    private PlayerBody() {
    }

    /** Whether box {@code k} of a cell's boxes overlaps the footprint horizontally. */
    static boolean overlapsFootprint(double[] boxes, int k, int cellX, int cellZ, double px, double pz) {
        return cellX + boxes[k] < px + HALF_WIDTH - EPS && cellX + boxes[k + 3] > px - HALF_WIDTH + EPS
                && cellZ + boxes[k + 2] < pz + HALF_WIDTH - EPS && cellZ + boxes[k + 5] > pz - HALF_WIDTH + EPS;
    }

    static boolean cellOverlapsFootprint(int cellX, int cellZ, double px, double pz) {
        return cellX < px + HALF_WIDTH - EPS && cellX + 1 > px - HALF_WIDTH + EPS
                && cellZ < pz + HALF_WIDTH - EPS && cellZ + 1 > pz - HALF_WIDTH + EPS;
    }
}
