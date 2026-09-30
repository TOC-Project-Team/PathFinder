package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Location;

/**
 * Packs block coordinates into a single {@code long} for the search's hash tables.
 */
final class BlockKey {
    private BlockKey() {
    }

    static long of(Location location) {
        return of(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    static long of(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | (long) y & 0xFFFL;
    }

    /** Spreads the key's bits so that neighboring blocks land in different hash buckets. */
    static int hash(long key) {
        key ^= key >>> 33;
        key *= 0xff51afd7ed558ccdL;
        key ^= key >>> 33;
        key *= 0xc4ceb9fe1a85ec53L;
        key ^= key >>> 33;
        return (int) key;
    }

    /** Smallest power-of-two table size that holds {@code expectedSize} entries at the given load factor. */
    static int tableSize(int expectedSize, float loadFactor) {
        int minimum = Math.max(2, (int) Math.ceil(expectedSize / loadFactor));
        int capacity = 1;
        while (capacity < minimum) {
            capacity <<= 1;
        }
        return capacity;
    }
}
