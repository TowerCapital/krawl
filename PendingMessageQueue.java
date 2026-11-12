import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Custom singly-linked FIFO (First-In, First-Out) Queue implementation.
 *
 * <p>Built from scratch using an internal linked node structure with explicit
 * {@code head} and {@code tail} pointers, without relying on {@link java.util.Queue}
 * or {@link java.util.LinkedList}.
 *
 * <p>Designed specifically for offline peer message buffering: when a recipient
 * is unreachable on the LAN, outgoing messages are enqueued here and flushed
 * sequentially in exact creation order upon reconnection.
 *
 * <p><b>Academic Complexity Summary:</b>
 * <ul>
 *   <li>{@link #enqueue(Object)}: Time O(1), Space O(1)</li>
 *   <li>{@link #dequeue()}: Time O(1), Space O(1)</li>
 *   <li>{@link #peek()}: Time O(1), Space O(1)</li>
 *   <li>{@link #isEmpty()}: Time O(1), Space O(1)</li>
 *   <li>{@link #size()}: Time O(1), Space O(1)</li>
 * </ul>
 *
 * @param <T> element type stored in the queue
 */
public class PendingMessageQueue<T> {

    /**
     * Singly-linked internal node structure.
     */
    private static class Node<E> {
        final E data;
        Node<E> next;

        Node(E data) {
            this.data = data;
            this.next = null;
        }
    }

    /** Points to the front of the queue (where elements are dequeued). */
    private Node<T> head;

    /** Points to the end of the queue (where elements are enqueued). */
    private Node<T> tail;

    /** Number of elements currently in the queue. */
    private int size;

    /**
     * Constructs an empty PendingMessageQueue.
     */
    public PendingMessageQueue() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    /**
     * Inserts an element at the tail of the queue.
     *
     * <p><b>Time Complexity:</b> O(1)
     * <br><b>Space Complexity:</b> O(1)
     *
     * @param item element to insert (must not be null)
     * @throws IllegalArgumentException if item is null
     */
    public void enqueue(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot enqueue null item.");
        }
        Node<T> newNode = new Node<>(item);
        if (tail == null) {
            head = newNode;
            tail = newNode;
        } else {
            tail.next = newNode;
            tail = newNode;
        }
        size++;
    }

    /**
     * Retrieves and removes the element at the head of the queue.
     *
     * <p><b>Time Complexity:</b> O(1)
     * <br><b>Space Complexity:</b> O(1)
     *
     * @return element formerly at the front of the queue
     * @throws NoSuchElementException if the queue is empty
     */
    public T dequeue() {
        if (isEmpty()) {
            throw new NoSuchElementException("Cannot dequeue from an empty queue.");
        }
        T data = head.data;
        head = head.next;
        if (head == null) {
            tail = null;
        }
        size--;
        return data;
    }

    /**
     * Retrieves, but does not remove, the element at the head of the queue.
     *
     * <p><b>Time Complexity:</b> O(1)
     * <br><b>Space Complexity:</b> O(1)
     *
     * @return element currently at the front of the queue
     * @throws NoSuchElementException if the queue is empty
     */
    public T peek() {
        if (isEmpty()) {
            throw new NoSuchElementException("Cannot peek into an empty queue.");
        }
        return head.data;
    }

    /**
     * Checks whether the queue contains zero elements.
     *
     * <p><b>Time Complexity:</b> O(1)
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return head == null;
    }

    /**
     * Returns the current number of elements waiting in the queue.
     *
     * <p><b>Time Complexity:</b> O(1)
     *
     * @return element count
     */
    public int size() {
        return size;
    }

    /**
     * Removes all elements from the queue.
     *
     * <p><b>Time Complexity:</b> O(1) pointer reset
     */
    public void clear() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    /**
     * Returns a snapshot list of elements in FIFO order (head to tail).
     *
     * <p><b>Time Complexity:</b> O(N)
     * <br><b>Space Complexity:</b> O(N)
     *
     * @return ordered list of items
     */
    public List<T> toList() {
        List<T> list = new ArrayList<>(size);
        Node<T> curr = head;
        while (curr != null) {
            list.add(curr.data);
            curr = curr.next;
        }
        return list;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PendingMessageQueue[");
        Node<T> curr = head;
        while (curr != null) {
            sb.append(curr.data);
            if (curr.next != null) {
                sb.append(" -> ");
            }
            curr = curr.next;
        }
        sb.append("]");
        return sb.toString();
    }
}

