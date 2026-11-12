import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single node in the N-ary {@link CampusTree} location hierarchy.
 *
 * <p>Each node holds an upward reference to its {@code parent} (allowing
 * bottom-up path resolution in O(H) time, where H is tree height) and a list
 * of {@code children} (allowing top-down BFS/DFS exploration in O(N) time).
 *
 * <p><b>Academic Complexity Summary:</b>
 * <ul>
 *   <li>{@link #addChild(LocationNode)}: Time O(1), Space O(1)</li>
 *   <li>{@link #removeChild(String)}: Time O(C) where C = number of children, Space O(1)</li>
 *   <li>{@link #getLevel()}: Time O(H), Space O(1)</li>
 * </ul>
 */
public class LocationNode {

    private final String id;
    private String name;
    private final LocationType type;

    /** Pointer to immediate parent node. Null if this is the root node. */
    private LocationNode parent;

    /** Ordered list of sub-locations directly under this node. */
    private final List<LocationNode> children;

    /**
     * Constructs a new LocationNode.
     *
     * @param id   unique identifier (e.g. "b107", "b-building")
     * @param name human-readable descriptive name (e.g. "B107 - OS Lab")
     * @param type level in the campus hierarchy
     */
    public LocationNode(String id, String name, LocationType type) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Location ID must not be null or empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Location name must not be null or empty.");
        }
        this.id = id.trim().toLowerCase();
        this.name = name.trim();
        this.type = Objects.requireNonNull(type, "LocationType must not be null.");
        this.children = new ArrayList<>();
        this.parent = null;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Location name must not be null or empty.");
        }
        this.name = name.trim();
    }

    public LocationType getType() {
        return type;
    }

    public LocationNode getParent() {
        return parent;
    }

    /**
     * Internal method to set the parent pointer.
     * Called automatically by {@link #addChild(LocationNode)}.
     */
    protected void setParent(LocationNode parent) {
        this.parent = parent;
    }

    /**
     * Returns an unmodifiable view of this node's direct children.
     *
     * @return list of children
     */
    public List<LocationNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    // ── Tree Operations ───────────────────────────────────────────────────────

    /**
     * Adds a child location under this node and sets its parent pointer.
     *
     * <p><b>Time Complexity:</b> O(1) amortized
     * <br><b>Space Complexity:</b> O(1)
     *
     * @param child non-null child node to attach
     */
    public void addChild(LocationNode child) {
        if (child == null) {
            throw new IllegalArgumentException("Child node cannot be null.");
        }
        if (child == this) {
            throw new IllegalArgumentException("A node cannot be its own child.");
        }
        child.setParent(this);
        children.add(child);
    }

    /**
     * Removes an immediate child by its unique ID and clears its parent pointer.
     *
     * <p><b>Time Complexity:</b> O(C) where C is the number of direct children
     * <br><b>Space Complexity:</b> O(1)
     *
     * @param childId unique ID of child to remove
     * @return true if child was found and removed, false otherwise
     */
    public boolean removeChild(String childId) {
        if (childId == null) return false;
        String normalized = childId.trim().toLowerCase();
        for (int i = 0; i < children.size(); i++) {
            LocationNode child = children.get(i);
            if (child.getId().equals(normalized)) {
                child.setParent(null);
                children.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if this node is a leaf (has no sub-locations).
     *
     * @return true if no children exist
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Checks if this node is the root of a tree (has no parent).
     *
     * @return true if parent is null
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Calculates the depth level of this node from the root.
     * Root is level 0, immediate children are level 1, etc.
     *
     * <p><b>Time Complexity:</b> O(H) where H is tree height
     * <br><b>Space Complexity:</b> O(1)
     *
     * @return zero-based depth level
     */
    public int getLevel() {
        int level = 0;
        LocationNode curr = this.parent;
        while (curr != null) {
            level++;
            curr = curr.getParent();
        }
        return level;
    }

    /**
     * Returns the count of immediate sub-locations.
     *
     * @return number of direct children
     */
    public int getChildCount() {
        return children.size();
    }

    // ── Equality & String Representation ──────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocationNode)) return false;
        LocationNode that = (LocationNode) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s, children=%d)",
                type, name, id, children.size());
    }
}

