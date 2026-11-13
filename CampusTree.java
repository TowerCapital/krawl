import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

/**
 * General N-ary Tree implementation representing the Campus Location Hierarchy.
 *
 * <p>
 * Models real-world administrative and structural campus paths:
 * 
 * <pre>
 *   VIT Pune (Root - CAMPUS)
 *     ├── B Building (BUILDING)
 *     │     ├── Ground Floor (FLOOR)
 *     │     └── First Floor (FLOOR)
 *     │           ├── B101 - Lecture Hall (ROOM)
 *     │           └── B107 - Operating Systems Lab (LAB)
 *     └── A Building (BUILDING)
 *           └── First Floor (FLOOR)
 *                 └── A101 - Seminar Hall (ROOM)
 * </pre>
 *
 * <p>
 * <b>Academic Complexity Analysis:</b>
 * <ul>
 * <li><b>addLocation:</b> Time O(N) to find parent + O(1) insertion, Space O(H)
 * recursion stack</li>
 * <li><b>findLocation:</b> Time O(N) worst-case search, Space O(H) recursion
 * stack</li>
 * <li><b>getFullPath:</b> Time O(H) climbing parent pointers, Space O(H) output
 * path</li>
 * <li><b>bfsTraversal:</b> Time O(N) visiting all nodes, Space O(W) queue where
 * W = max level width</li>
 * <li><b>dfsTraversal:</b> Time O(N) visiting all nodes, Space O(H) recursion
 * stack</li>
 * </ul>
 * where N = total locations in tree, H = tree height (levels), W = maximum tree
 * width.
 */
public class CampusTree {

    private LocationNode root;

    /**
     * Constructs a CampusTree with a specified root node.
     *
     * @param root the top-level campus root node
     */
    public CampusTree(LocationNode root) {
        this.root = root;
    }

    /**
     * Default constructor. Initializes an empty tree without a root.
     */
    public CampusTree() {
        this.root = null;
    }

    // ── Root Accessors ────────────────────────────────────────────────────────

    public LocationNode getRoot() {
        return root;
    }

    public void setRoot(LocationNode root) {
        this.root = root;
    }

    // ── Core Operations ───────────────────────────────────────────────────────

    /**
     * Attaches a new location node as a child under the specified parent ID.
     *
     * <p>
     * <b>Time Complexity:</b> O(N) to locate parent + O(1) to append child
     * <br>
     * <b>Space Complexity:</b> O(H) recursion stack
     *
     * @param parentId unique ID of the target parent node
     * @param child    the new node to attach
     * @return true if parent was found and child attached, false otherwise
     */
    public boolean addLocation(String parentId, LocationNode child) {
        if (child == null) {
            throw new IllegalArgumentException("Child location cannot be null.");
        }
        if (root == null) {
            // If tree has no root, child becomes the root
            root = child;
            return true;
        }

        LocationNode parent = findLocation(parentId);
        if (parent == null) {
            return false;
        }

        parent.addChild(child);
        return true;
    }

    /**
     * Finds a location node anywhere in the tree by its unique ID using DFS search.
     *
     * <p>
     * <b>Time Complexity:</b> O(N) in worst case (traverses until found)
     * <br>
     * <b>Space Complexity:</b> O(H) call stack depth
     *
     * @param id unique ID to search for (case-insensitive)
     * @return matching {@link LocationNode}, or null if not found
     */
    public LocationNode findLocation(String id) {
        if (root == null || id == null) {
            return null;
        }
        return findLocationRecursive(root, id.trim().toLowerCase());
    }

    private LocationNode findLocationRecursive(LocationNode current, String targetId) {
        if (current.getId().equals(targetId)) {
            return current;
        }
        for (LocationNode child : current.getChildren()) {
            LocationNode found = findLocationRecursive(child, targetId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Retrieves the direct child locations under a given node.
     *
     * <p>
     * <b>Time Complexity:</b> O(N) to find node + O(1) to get children
     * <br>
     * <b>Space Complexity:</b> O(1)
     *
     * @param id unique ID of the location
     * @return list of immediate children, or empty list if not found or leaf
     */
    public List<LocationNode> getChildren(String id) {
        LocationNode node = findLocation(id);
        if (node == null) {
            return Collections.emptyList();
        }
        return node.getChildren();
    }

    /**
     * Builds the complete hierarchical path string from the campus root down
     * to the target location by ascending parent pointers.
     *
     * <p>
     * Example: {@code "VIT Pune > B Building > First Floor > B107"}
     *
     * <p>
     * <b>Time Complexity:</b> O(H) where H is tree depth
     * <br>
     * <b>Space Complexity:</b> O(H) to store the reversed path components
     *
     * @param id        unique ID of the location
     * @param delimiter separator between path segments (e.g. " > " or " / ")
     * @return formatted full path string, or null if node not found
     */
    public String getFullPath(String id, String delimiter) {
        List<LocationNode> path = getPathNodes(id);
        if (path.isEmpty()) {
            return null;
        }
        String sep = (delimiter != null) ? delimiter : " > ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            sb.append(path.get(i).getName());
            if (i < path.size() - 1) {
                sb.append(sep);
            }
        }
        return sb.toString();
    }

    /**
     * Resolves the ordered list of nodes from the root down to the target location.
     *
     * <p>
     * <b>Time Complexity:</b> O(H) where H is the node's depth
     * <br>
     * <b>Space Complexity:</b> O(H) to store nodes
     *
     * @param id unique ID of target location
     * @return list of nodes starting from root down to target, or empty list if not
     *         found
     */
    public List<LocationNode> getPathNodes(String id) {
        LocationNode target = findLocation(id);
        if (target == null) {
            return Collections.emptyList();
        }

        List<LocationNode> path = new ArrayList<>();
        LocationNode curr = target;
        while (curr != null) {
            path.add(curr);
            curr = curr.getParent();
        }
        Collections.reverse(path);
        return path;
    }

    // ── Traversals ────────────────────────────────────────────────────────────

    /**
     * Breadth-First Search (Level-Order) Traversal.
     *
     * <p>
     * Visits nodes level-by-level (CAMPUS → BUILDINGS → FLOORS → ROOMS).
     *
     * <p>
     * <b>Time Complexity:</b> O(N) — every node is enqueued and dequeued once.
     * <br>
     * <b>Space Complexity:</b> O(W) auxiliary space for the queue, where W
     * is the maximum number of nodes at any level.
     *
     * @return ordered list of nodes visited in BFS order
     */
    public List<LocationNode> bfsTraversal() {
        List<LocationNode> result = new ArrayList<>();
        if (root == null) {
            return result;
        }

        Queue<LocationNode> queue = new ArrayDeque<>();
        queue.offer(root);

        while (!queue.isEmpty()) {
            LocationNode current = queue.poll();
            result.add(current);
            for (LocationNode child : current.getChildren()) {
                queue.offer(child);
            }
        }
        return result;
    }

    /**
     * Depth-First Search (Pre-Order) Traversal.
     *
     * <p>
     * Explores each branch to its maximum depth before backtracking
     * (Root → first child branch completely → next child branch).
     *
     * <p>
     * <b>Time Complexity:</b> O(N) — visits each node exactly once.
     * <br>
     * <b>Space Complexity:</b> O(H) auxiliary space on the call stack,
     * where H is the height of the tree.
     *
     * @return ordered list of nodes visited in DFS pre-order
     */
    public List<LocationNode> dfsTraversal() {
        List<LocationNode> result = new ArrayList<>();
        if (root != null) {
            dfsRecursive(root, result);
        }
        return result;
    }

    private void dfsRecursive(LocationNode current, List<LocationNode> result) {
        result.add(current);
        for (LocationNode child : current.getChildren()) {
            dfsRecursive(child, result);
        }
    }

    /**
     * Depth-First Search (Pre-Order) Traversal implemented iteratively using
     * our custom {@link LocationStack}.
     *
     * <p>
     * Pushing children in reverse order ensures that the left-most child is popped
     * and explored first, matching the exact pre-order sequence of recursive DFS.
     *
     * <p>
     * <b>Time Complexity:</b> O(N) — each node pushed and popped once
     * <br>
     * <b>Space Complexity:</b> O(H) — auxiliary heap stack space bounded by tree
     * height
     *
     * @return ordered list of nodes visited in DFS pre-order
     */
    public List<LocationNode> dfsIterative() {
        List<LocationNode> result = new ArrayList<>();
        if (root == null) {
            return result;
        }

        LocationStack<LocationNode> stack = new LocationStack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            LocationNode current = stack.pop();
            result.add(current);

            // Push children in reverse order so first child is on top of stack
            List<LocationNode> children = current.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
        return result;
    }

    // ── Metrics & Helpers ─────────────────────────────────────────────────────

    /**
     * Returns the total count of locations registered in the tree.
     *
     * <p>
     * <b>Time Complexity:</b> O(N)
     *
     * @return number of nodes
     */
    public int size() {
        return dfsTraversal().size();
    }

    /**
     * Calculates the maximum depth (height) of the tree.
     * A tree with only a root has height 1.
     *
     * <p>
     * <b>Time Complexity:</b> O(N)
     * <br>
     * <b>Space Complexity:</b> O(H)
     *
     * @return maximum tree depth
     */
    public int getHeight() {
        return getHeightRecursive(root);
    }

    private int getHeightRecursive(LocationNode node) {
        if (node == null)
            return 0;
        int maxChildHeight = 0;
        for (LocationNode child : node.getChildren()) {
            maxChildHeight = Math.max(maxChildHeight, getHeightRecursive(child));
        }
        return 1 + maxChildHeight;
    }

    // ── Default Seed Data ─────────────────────────────────────────────────────

    /**
     * Pre-populates the tree with the VIT Pune campus structure for demonstration.
     *
     * <pre>
     * VIT Pune (CAMPUS)
     *   ├── B Building (BUILDING)
     *   │     ├── Ground Floor (FLOOR)
     *   │     │     └── B001 - Central Library (ROOM)
     *   │     ├── First Floor (FLOOR)
     *   │     │     ├── B101 - Lecture Hall (ROOM)
     *   │     │     ├── B107 - Operating Systems Lab (LAB)
     *   │     │     └── B108 - Network Lab (LAB)
     *   │     └── Second Floor (FLOOR)
     *   │           └── B205 - AI Research Lab (LAB)
     *   └── A Building (BUILDING)
     *         ├── First Floor (FLOOR)
     *         │     └── A101 - Seminar Hall (ROOM)
     *         └── Second Floor (FLOOR)
     *               └── A202 - IoT Systems Lab (LAB)
     * </pre>
     */
    public void seedDefaultCampus() {
        // Root: Campus
        this.root = new LocationNode("vit-pune", "VIT Pune", LocationType.CAMPUS);

        // Buildings
        addLocation("vit-pune", new LocationNode("b-building", "B Building", LocationType.BUILDING));
        addLocation("vit-pune", new LocationNode("a-building", "A Building", LocationType.BUILDING));

        // B Building - Floors
        addLocation("b-building", new LocationNode("b-ground", "Ground Floor", LocationType.FLOOR));
        addLocation("b-building", new LocationNode("b-floor-1", "First Floor", LocationType.FLOOR));
        addLocation("b-building", new LocationNode("b-floor-2", "Second Floor", LocationType.FLOOR));

        // B Building - Rooms & Labs
        addLocation("b-ground", new LocationNode("b001", "B001 - Central Library", LocationType.ROOM));
        addLocation("b-floor-1", new LocationNode("b101", "B101 - Lecture Hall", LocationType.ROOM));
        addLocation("b-floor-1", new LocationNode("b107", "B107 - Operating Systems Lab", LocationType.LAB));
        addLocation("b-floor-1", new LocationNode("b108", "B108 - Network Lab", LocationType.LAB));
        addLocation("b-floor-2", new LocationNode("b205", "B205 - AI Research Lab", LocationType.LAB));

        // A Building - Floors & Rooms
        addLocation("a-building", new LocationNode("a-floor-1", "First Floor", LocationType.FLOOR));
        addLocation("a-building", new LocationNode("a-floor-2", "Second Floor", LocationType.FLOOR));
        addLocation("a-floor-1", new LocationNode("a101", "A101 - Seminar Hall", LocationType.ROOM));
        addLocation("a-floor-2", new LocationNode("a202", "A202 - IoT Systems Lab", LocationType.LAB));
    }
}
