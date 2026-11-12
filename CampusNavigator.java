import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages user navigation history and location breadcrumbs across the campus hierarchy
 * using dual {@link LocationStack} instances (back-stack and forward-stack).
 *
 * <p>Designed for integration with desktop UI navigation (e.g. Ronak's JavaFX GUI)
 * and interactive CLI navigation commands.
 *
 * <p><b>Academic Complexity Summary:</b>
 * <ul>
 *   <li>{@link #visit(LocationNode)}: Time O(1), Space O(1)</li>
 *   <li>{@link #goBack()}: Time O(1), Space O(1)</li>
 *   <li>{@link #goForward()}: Time O(1), Space O(1)</li>
 *   <li>{@link #getBreadcrumbPath(String)}: Time O(H), Space O(H)</li>
 * </ul>
 */
public class CampusNavigator {

    private final LocationStack<LocationNode> backStack;
    private final LocationStack<LocationNode> forwardStack;
    private LocationNode currentLocation;

    /**
     * Constructs a navigator starting at a designated initial location.
     *
     * @param initialLocation starting location node (e.g. Campus root)
     */
    public CampusNavigator(LocationNode initialLocation) {
        this.backStack = new LocationStack<>();
        this.forwardStack = new LocationStack<>();
        this.currentLocation = initialLocation;
    }

    /**
     * Default constructor starting without an initial location.
     */
    public CampusNavigator() {
        this(null);
    }

    // ── Navigation Operations ─────────────────────────────────────────────────

    /**
     * Visits a new location, recording the previous location onto the back-stack.
     * Clears forward-stack history (standard browser/file-manager navigation model).
     *
     * <p><b>Time Complexity:</b> O(1)
     * <br><b>Space Complexity:</b> O(1)
     *
     * @param nextLocation location being visited
     */
    public void visit(LocationNode nextLocation) {
        if (nextLocation == null) {
            throw new IllegalArgumentException("Cannot visit null location.");
        }
        if (currentLocation != null) {
            backStack.push(currentLocation);
        }
        currentLocation = nextLocation;
        forwardStack.clear();
    }

    /**
     * Navigates back to the previous location in history.
     *
     * <p><b>Time Complexity:</b> O(1)
     * <br><b>Space Complexity:</b> O(1)
     *
     * @return the previous {@link LocationNode}, or null if history is empty
     */
    public LocationNode goBack() {
        if (backStack.isEmpty()) {
            return null;
        }
        if (currentLocation != null) {
            forwardStack.push(currentLocation);
        }
        currentLocation = backStack.pop();
        return currentLocation;
    }

    /**
     * Navigates forward to a location previously backed out of.
     *
     * <p><b>Time Complexity:</b> O(1)
     * <br><b>Space Complexity:</b> O(1)
     *
     * @return the restored {@link LocationNode}, or null if forward history is empty
     */
    public LocationNode goForward() {
        if (forwardStack.isEmpty()) {
            return null;
        }
        if (currentLocation != null) {
            backStack.push(currentLocation);
        }
        currentLocation = forwardStack.pop();
        return currentLocation;
    }

    public boolean canGoBack() {
        return !backStack.isEmpty();
    }

    public boolean canGoForward() {
        return !forwardStack.isEmpty();
    }

    public LocationNode getCurrentLocation() {
        return currentLocation;
    }

    public int getHistoryDepth() {
        return backStack.size();
    }

    // ── Breadcrumb & Visualization Helpers ────────────────────────────────────

    /**
     * Builds a chronological trail of previously visited locations leading up to
     * the current location.
     *
     * <p><b>Time Complexity:</b> O(H) where H is history depth
     * <br><b>Space Complexity:</b> O(H)
     *
     * @param delimiter separator between location names (e.g. " -> ")
     * @return formatted breadcrumb string
     */
    public String getBreadcrumbPath(String delimiter) {
        if (currentLocation == null) {
            return "";
        }
        List<LocationNode> history = backStack.toList();
        Collections.reverse(history);

        String sep = (delimiter != null) ? delimiter : " -> ";
        StringBuilder sb = new StringBuilder();
        for (LocationNode node : history) {
            sb.append(node.getName()).append(sep);
        }
        sb.append(currentLocation.getName());
        return sb.toString();
    }
}

