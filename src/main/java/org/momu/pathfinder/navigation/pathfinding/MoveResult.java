package org.momu.pathfinder.navigation.pathfinding;

/**
 * Scratch result of one move attempt; one instance is reused for every move of a search. It also carries the
 * blocks the move breaks, which the terrain reads as air while the move is being checked.
 */
final class MoveResult {
    static final int MAX_BREAKS = 3;

    enum Status { OK, BLOCKED, FAILED }

    Status status;
    /** The block in the way when {@link Status#BLOCKED}. */
    int blockX, blockY, blockZ;
    /** Where the move ends. */
    int x, y, z;
    double feet;
    int kind;
    int floorFlags;
    MoveType moveType;
    double drop;
    boolean passedDoor;
    boolean passedTrapdoor;
    final long[] breaks = new long[MAX_BREAKS];
    int breakCount;

    void reset() {
        breakCount = 0;
        passedDoor = false;
        passedTrapdoor = false;
        drop = 0.0;
    }

    boolean blocked(int x, int y, int z) {
        status = Status.BLOCKED;
        blockX = x;
        blockY = y;
        blockZ = z;
        return false;
    }

    boolean failed() {
        status = Status.FAILED;
        return false;
    }

    boolean land(int x, int y, int z, TerrainView.Stand stand, MoveType moveType, double drop) {
        status = Status.OK;
        this.x = x;
        this.y = y;
        this.z = z;
        this.feet = stand.feet;
        this.kind = stand.kind;
        this.floorFlags = stand.floorFlags;
        this.moveType = moveType;
        this.drop = drop;
        return true;
    }

    boolean isBroken(long key) {
        for (int i = 0; i < breakCount; i++) {
            if (breaks[i] == key) {
                return true;
            }
        }
        return false;
    }
}
