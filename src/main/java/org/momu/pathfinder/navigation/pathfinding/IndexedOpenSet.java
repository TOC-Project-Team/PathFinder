package org.momu.pathfinder.navigation.pathfinding;

import java.util.Arrays;

/**
 * Binary min-heap of open nodes that tracks each node's position, so a node whose cost dropped can be moved up
 * without a linear search.
 */
final class IndexedOpenSet {
    private PathNode[] heap;
    private int size;

    IndexedOpenSet(int initialCapacity) {
        heap = new PathNode[Math.max(2, initialCapacity + 1)];
    }

    boolean isEmpty() {
        return size == 0;
    }

    void add(PathNode node) {
        if (size + 1 >= heap.length) {
            heap = Arrays.copyOf(heap, heap.length << 1);
        }
        heap[++size] = node;
        node.heapIndex = size;
        siftUp(size);
    }

    PathNode poll() {
        PathNode result = heap[1];
        PathNode tail = heap[size];
        heap[size--] = null;
        result.heapIndex = -1;
        if (size > 0) {
            heap[1] = tail;
            tail.heapIndex = 1;
            siftDown(1);
        }
        return result;
    }

    /** Restores heap order after {@code node}'s cost decreased. */
    void decreaseKey(PathNode node) {
        if (node.heapIndex <= 0) {
            throw new IllegalStateException("Cannot update a node outside the open set");
        }
        siftUp(node.heapIndex);
    }

    private void siftUp(int index) {
        PathNode value = heap[index];
        while (index > 1) {
            int parentIndex = index >>> 1;
            PathNode parent = heap[parentIndex];
            if (PathNode.compare(value, parent) >= 0) {
                break;
            }
            heap[index] = parent;
            parent.heapIndex = index;
            index = parentIndex;
        }
        heap[index] = value;
        value.heapIndex = index;
    }

    private void siftDown(int index) {
        PathNode value = heap[index];
        int half = size >>> 1;
        while (index <= half) {
            int child = index << 1;
            int right = child + 1;
            if (right <= size && PathNode.compare(heap[right], heap[child]) < 0) {
                child = right;
            }
            PathNode childValue = heap[child];
            if (PathNode.compare(value, childValue) <= 0) {
                break;
            }
            heap[index] = childValue;
            childValue.heapIndex = index;
            index = child;
        }
        heap[index] = value;
        value.heapIndex = index;
    }
}
