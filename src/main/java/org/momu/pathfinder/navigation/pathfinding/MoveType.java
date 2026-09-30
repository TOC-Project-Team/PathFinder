package org.momu.pathfinder.navigation.pathfinding;

/**
 * How the player gets from the previous path node to a node.
 */
public enum MoveType {
    /** Walking, including stepping onto slabs, carpets and stairs and down small drops. */
    HORIZONTAL,
    /** Climbing up a ladder, vine or scaffolding, swimming up, or stepping out on top of scaffolding. */
    UP,
    /** Climbing or swimming down, or sneaking down scaffolding. */
    DOWN,
    /** Jumping (or climbing off a ladder or vine) onto a ledge while moving sideways. */
    JUMP,
    /** Dropping down more than a step. */
    FALL,
    /** Jumping across a gap to a block two or more blocks away, possibly at another height. */
    BLOCK_JUMP,
    /** Swimming in a straight line along the water surface. */
    WATER_SURFACE
}
