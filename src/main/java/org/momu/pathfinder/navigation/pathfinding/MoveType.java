package org.momu.pathfinder.navigation.pathfinding;

/**
 * How the player gets from the previous path node to a node.
 */
public enum MoveType {
    /** Walking on the same level. */
    HORIZONTAL,
    /** Climbing straight up a ladder, scaffolding or water column. Rewritten to {@link #JUMP} in finished paths. */
    UP,
    /** Stepping down. The search always refines this to {@link #FALL}. */
    DOWN,
    /** Stepping up one block while moving sideways. */
    JUMP,
    /** Dropping down one or more blocks. */
    FALL,
    /** Jumping across a gap of two or more blocks. */
    BLOCK_JUMP,
    /** Swimming in a straight line along the water surface. */
    WATER_SURFACE
}
