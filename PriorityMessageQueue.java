import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Custom Min-Heap based Priority Queue for {@link PriorityMessage} delivery ordering.
 *
 * <p>Ensures that URGENT messages are always dequeued and delivered before NORMAL
 * messages, and NORMAL before LOW — regardless of the order they were inserted.
 * Within the same priority level, messages are dequeued in FIFO (insertion) order.
 *
 * <p><b>Heap Property:</b> parent.priority ≤ children.priority (Min-Heap)
 *
 * <p><b>Academic Complexity Summary:</b>
 * <ul>
 *   <li>{@link #insert}: Time O(log N), Space O(1)</li>
 *   <li>{@link #extractMin}: Time O(log N), Space O(1)</li>
 *   <li>{@link #peekMin}: Time O(1), Space O(1)</li>
 *   <li>{@link #isEmpty}: Time O(1), Space O(1)</li>
 * </ul>
 * where N = current number of elements in the heap.
 */
public class PriorityMessageQueue {

    /** Internal array-backed heap. Index 0 is unused; root is at index 1. */
    private final List<PriorityMessage> heap;

    /** Monotonic counter guaranteeing FIFO ordering within same priority level. */
    private long insertionCounter;

    public PriorityMessageQueue() {
        this.heap = new ArrayList<>();
        this.heap.add(null); // placeholder so root is at index 1
        this.insertionCounter = 0;
    }

    // ── Core Heap Operations ──────────────────────────────────────────────────

    /**
     * Inserts a message with its priority into the heap and sifts it up.
     *
     * <p><b>Time Complexity:</b> O(log N) — sifts up at most H = log₂(N) levels
     * <br><b>Space Complexity:</b> O(1)
     *
     * @param message  message to enqueue
     * @param priority urgency level
     */
    public void insert(Message message, MessagePriority priority) {
        if (message == null || priority == null) {
            throw new IllegalArgumentException("Message and priority must not be null.");
        }
        PriorityMessage pm = new PriorityMessage(message, priority, insertionCounter++);
        heap.add(pm);
        siftUp(heap.size() - 1);
    }

    /**
     * Removes and returns the highest-priority (minimum weight) message.
     *
     * <p><b>Time Complexity:</b> O(log N)
     * <br><b>Space Complexity:</b> O(1)
     *
     * @return the most urgent {@link PriorityMessage}
     * @throws NoSuchElementException if the queue is empty
     */
    public PriorityMessage extractMin() {
        if (isEmpty()) {
            throw new NoSuchElementException("Priority queue is empty.");
        }
        PriorityMessage min = heap.get(1);
        int last = heap.size() - 1;
        heap.set(1, heap.get(last));
        heap.remove(last);
        if (!isEmpty()) {
            siftDown(1);
        }
        return min;
    }

    /**
     * Returns the highest-priority message without removing it.
     *
     * <p><b>Time Complexity:</b> O(1)
     *
     * @return root of the heap
     * @throws NoSuchElementException if the queue is empty
     */
    public PriorityMessage peekMin() {
        if (isEmpty()) {
            throw new NoSuchElementException("Priority queue is empty.");
        }
        return heap.get(1);
    }

    public boolean isEmpty() { return heap.size() <= 1; }
    public int size()        { return heap.size() - 1; }

    // ── Heap Maintenance ──────────────────────────────────────────────────────

    /**
     * Sift-Up: After inserting at the last position, bubble the new element
     * up until the heap property is restored.
     *
     * <p>Compares child with parent; swaps if child has higher priority (lower weight).
     */
    private void siftUp(int index) {
        while (index > 1) {
            int parent = index / 2;
            if (heap.get(index).compareTo(heap.get(parent)) < 0) {
                Collections.swap(heap, index, parent);
                index = parent;
            } else {
                break;
            }
        }
    }

    /**
     * Sift-Down: After extracting the root and placing the last element there,
     * push it down until the heap property is restored.
     *
     * <p>Finds the minimum-priority child and swaps if smaller than current node.
     */
    private void siftDown(int index) {
        int size = heap.size() - 1;
        while (true) {
            int left  = 2 * index;
            int right = 2 * index + 1;
            int smallest = index;

            if (left <= size && heap.get(left).compareTo(heap.get(smallest)) < 0) {
                smallest = left;
            }
            if (right <= size && heap.get(right).compareTo(heap.get(smallest)) < 0) {
                smallest = right;
            }
            if (smallest != index) {
                Collections.swap(heap, index, smallest);
                index = smallest;
            } else {
                break;
            }
        }
    }

    /**
     * Returns all messages sorted by priority (non-destructive snapshot).
     *
     * @return sorted list from highest to lowest priority
     */
    public List<PriorityMessage> peekAll() {
        List<PriorityMessage> snapshot = new ArrayList<>(heap.subList(1, heap.size()));
        snapshot.sort(null);
        return Collections.unmodifiableList(snapshot);
    }
}

