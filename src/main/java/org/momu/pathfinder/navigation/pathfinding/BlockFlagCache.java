package org.momu.pathfinder.navigation.pathfinding;

/**
 * Open-addressing hash map from {@link BlockKey} to a small bit set. Zero means "not cached", so stored values
 * must always have at least one bit set.
 */
final class BlockFlagCache {
    static final byte MISSING = 0;
    private static final float LOAD_FACTOR = 0.65f;

    private long[] keys;
    private byte[] values;
    private int mask;
    private int resizeAt;
    private int size;

    BlockFlagCache(int expectedSize) {
        allocate(BlockKey.tableSize(expectedSize, LOAD_FACTOR));
    }

    byte get(long key) {
        int index = BlockKey.hash(key) & mask;
        while (values[index] != MISSING) {
            if (keys[index] == key) {
                return values[index];
            }
            index = (index + 1) & mask;
        }
        return MISSING;
    }

    void put(long key, byte value) {
        if (size >= resizeAt) {
            long[] oldKeys = keys;
            byte[] oldValues = values;
            allocate(oldValues.length << 1);
            for (int i = 0; i < oldValues.length; i++) {
                if (oldValues[i] != MISSING) {
                    insert(oldKeys[i], oldValues[i]);
                }
            }
        }
        insert(key, value);
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        values = new byte[capacity];
        mask = capacity - 1;
        resizeAt = (int) (capacity * LOAD_FACTOR);
        size = 0;
    }

    private void insert(long key, byte value) {
        int index = BlockKey.hash(key) & mask;
        while (values[index] != MISSING) {
            if (keys[index] == key) {
                values[index] = value;
                return;
            }
            index = (index + 1) & mask;
        }
        keys[index] = key;
        values[index] = value;
        size++;
    }
}
