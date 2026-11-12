import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.List;

/**
 * Custom singly-linked LIFO (Last-In, First-Out) Stack implementation.
 *
 * <p>Built from scratch using an internal linked node architecture without
 * relying on {@link java.util.Stack} or {@link java.util.ArrayDeque}, specifically
 * designed for academic demonstration and zero-allocation overhead.
 *
 * <p><b>Academic Complexity Summary:</b>
 * <ul>
 *   <li>{@link #push(Object)}: Time O(1), Space O(1)</li>
 *   <li>{@link #pop()}: Time O(1), Space O(1)</li>
 *   <li>{@link #peek()}: Time O(1), Space O(1)</li>
 *   <li>{@link #isEmpty()}: Time O(1), Space O(1)</li>
 *   <li>{@link #size()}: Time O(1), Space O(1)</li>
 * </ul>
 *
 * @param <T> element type stored in the stack
 */
public class LocationStack<T> {

    /**
     * Singly-linked internal node structure.
     */
    private static class Node<E> {
        final E data;
        Node<E> next;

        Node(E data, Node<E> next) {
            this.data = data;
            this.next = next;
        }
    }

    /** Top of the stack. Null if stack is empty. */
    private Node<T> top;

    /** Total number of elements currently stored in the stack. */
    private int size;

    /**
     * Constructs an empty LocationStack.
     */
    public LocationStack() {
        this.top = null;
        this.size = 0;
    }

    /**
     * Pushes an item onto the top of this stack.
     *
     * <p><b>Time Complexity:</b> O(1)
     * <br><b>Space Complexity:</b> O(1)
     *
     * @param item element to push (must not be null)
     * @throws IllegalArgumentException if item is null
     */
    public void push(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot push null item onto stack.");
        }
        this.top = new Node<>(item, this.top);
        this.size++;
    }

    /**
     * Removes and returns the item at the top of this stack.
     *
     * <p><b>Time Complexity:</b> O(1)
     * <br><b>Space Complexity:</b> O(1)
     *
     * @return element formerly at the top of this stack
     * @throws EmptyStackException if this stack is empty
     */
    public T pop() {
        if (isEmpty()) {
            throw new EmptyStackException();
        }
        T data = top.data;
        top = top.next;
        size--;
        return data;
    }

    /**
     * Looks at the item at the top of this stack without removing it.
     *
     * <p><b>Time Complexity:</b> O(1)
     * <br><b>Space Complexity:</b> O(1)
     *
     * @return element currently at the top of this stack
     * @throws EmptyStackException if this stack is empty
     */
    public T peek() {
        if (isEmpty()) {
            throw new EmptyStackException();
        }
        return top.data;
    }

    /**
     * Tests if this stack is empty.
     *
     * <p><b>Time Complexity:</b> O(1)
     *
     * @return {@code true} if size is 0, {@code false} otherwise
     */
    public boolean isEmpty() {
        return top == null;
    }

    /**
     * Returns the number of elements in this stack.
     *
     * <p><b>Time Complexity:</b> O(1)
     *
     * @return current size of the stack
     */
    public int size() {
        return size;
    }

    /**
     * Clears all elements from the stack.
     *
     * <p><b>Time Complexity:</b> O(1) pointer reset
     */
    public void clear() {
        this.top = null;
        this.size = 0;
    }

    /**
     * Returns a list of elements from top to bottom (LIFO order).
     * Useful for rendering navigation history or breadcrumb trails.
     *
     * <p><b>Time Complexity:</b> O(N)
     * <br><b>Space Complexity:</b> O(N)
     *
     * @return list snapshot from top to bottom
     */
    public List<T> toList() {
        List<T> list = new ArrayList<>(size);
        Node<T> curr = top;
        while (curr != null) {
            list.add(curr.data);
            curr = curr.next;
        }
        return list;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LocationStack[");
        Node<T> curr = top;
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

