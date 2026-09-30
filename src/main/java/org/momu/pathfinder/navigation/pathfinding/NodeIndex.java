package org.momu.pathfinder.navigation.pathfinding;

/**
 * Open-addressing hash map from {@link BlockKey} to the node at that block.
 */
final class NodeIndex {
    private static final float LOAD_FACTOR = 0.65f;

    private long[] keys;
    private PathNode[] values;
    private int mask;
    private int resizeAt;
    private int size;

    NodeIndex(int expectedSize) {
        allocate(BlockKey.tableSize(expectedSize, LOAD_FACTOR));
    }

    PathNode get(long key) {
        int index = BlockKey.hash(key) & mask;
        while (values[index] != null) {
            if (keys[index] == key) {
                return values[index];
            }
            index = (index + 1) & mask;
        }
        return null;
    }

    void put(long key, PathNode value) {
        if (size >= resizeAt) {
            long[] oldKeys = keys;
            PathNode[] oldValues = values;
            allocate(oldValues.length << 1);
            for (int i = 0; i < oldValues.length; i++) {
                if (oldValues[i] != null) {
                    insert(oldKeys[i], oldValues[i]);
                }
            }
        }
        insert(key, value);
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        values = new PathNode[capacity];
        mask = capacity - 1;
        resizeAt = (int) (capacity * LOAD_FACTOR);
        size = 0;
    }

    private void insert(long key, PathNode value) {
        int index = BlockKey.hash(key) & mask;
        while (values[index] != null) {
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
