import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Custom Trie (Prefix Tree) data structure for campus location and room search.
 *
 * <p>Enables ultra-fast, prefix-based autocomplete for students and faculty searching
 * for rooms, labs, and buildings across campus.
 *
 * <p><b>Example Query:</b>
 * Searching {@code "B10"} returns:
 * <ul>
 *   <li>{@code B101 - Lecture Hall}</li>
 *   <li>{@code B107 - Operating Systems Lab}</li>
 *   <li>{@code B108 - Network Lab}</li>
 * </ul>
 *
 * <p><b>Academic Complexity Analysis:</b>
 * <ul>
 *   <li><b>Insert:</b> Time O(L), Space O(L) where L is the length of the key.</li>
 *   <li><b>Search Exact:</b> Time O(L), Space O(1).</li>
 *   <li><b>Starts With:</b> Time O(P), Space O(1) where P is the length of the prefix.</li>
 *   <li><b>Search Prefix:</b> Time O(P + M), Space O(M) where M is the total characters
 *       in all matching sub-trie words.</li>
 * </ul>
 */
public class LocationTrie {

    private final TrieNode root;
    private int wordCount;

    /**
     * Constructs an empty LocationTrie.
     */
    public LocationTrie() {
        this.root = new TrieNode();
        this.wordCount = 0;
    }

    // ── Insertion ─────────────────────────────────────────────────────────────

    /**
     * Inserts a key string linked to a {@link LocationNode} into the Trie.
     *
     * <p><b>Time Complexity:</b> O(L) where L = key length
     * <br><b>Space Complexity:</b> O(L) new nodes created in worst case
     *
     * @param key      search key (e.g. room number "b107" or name "Operating Systems")
     * @param location reference to the campus hierarchy node
     */
    public void insert(String key, LocationNode location) {
        if (key == null || key.trim().isEmpty() || location == null) {
            return;
        }

        String normalized = normalize(key);
        TrieNode current = root;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            current = current.getOrCreateChild(c);
        }

        if (!current.isEndOfWord()) {
            current.setEndOfWord(true);
            wordCount++;
        }
        current.setLocationNode(location);
        current.setWord(normalized);
    }

    // ── Search Operations ─────────────────────────────────────────────────────

    /**
     * Searches for an exact key match in the Trie.
     *
     * <p><b>Time Complexity:</b> O(L) where L = key length
     * <br><b>Space Complexity:</b> O(1)
     *
     * @param key exact search string
     * @return matching {@link LocationNode}, or null if not found
     */
    public LocationNode searchExact(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }

        TrieNode node = findPrefixNode(normalize(key));
        if (node != null && node.isEndOfWord()) {
            return node.getLocationNode();
        }
        return null;
    }

    /**
     * Checks if any indexed location key begins with the specified prefix.
     *
     * <p><b>Time Complexity:</b> O(P) where P = prefix length
     * <br><b>Space Complexity:</b> O(1)
     *
     * @param prefix search prefix
     * @return true if prefix exists in the trie
     */
    public boolean startsWith(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return false;
        }
        return findPrefixNode(normalize(prefix)) != null;
    }

    /**
     * Finds and returns all distinct {@link LocationNode}s whose search keys start
     * with the specified prefix.
     *
     * <p>Example: {@code searchPrefix("b10")} returns nodes for {@code B101},
     * {@code B107}, and {@code B108}.
     *
     * <p><b>Time Complexity:</b> O(P + M) where P = prefix length, M = subtree nodes
     * <br><b>Space Complexity:</b> O(M)
     *
     * @param prefix search prefix (case-insensitive)
     * @return list of matching location nodes, ordered by natural trie traversal
     */
    public List<LocationNode> searchPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        TrieNode prefixNode = findPrefixNode(normalize(prefix));
        if (prefixNode == null) {
            return Collections.emptyList();
        }

        // Use LinkedHashSet to preserve DFS traversal order and eliminate duplicates
        Set<LocationNode> results = new LinkedHashSet<>();
        collectLocations(prefixNode, results);
        return new ArrayList<>(results);
    }

    /**
     * Returns all indexed word keys that begin with the specified prefix.
     *
     * @param prefix search prefix
     * @return list of matching word keys
     */
    public List<String> getWordsWithPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        TrieNode prefixNode = findPrefixNode(normalize(prefix));
        if (prefixNode == null) {
            return Collections.emptyList();
        }

        List<String> words = new ArrayList<>();
        collectWords(prefixNode, words);
        return words;
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    private TrieNode findPrefixNode(String prefix) {
        TrieNode current = root;
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            current = current.getChild(c);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private void collectLocations(TrieNode node, Set<LocationNode> results) {
        if (node.isEndOfWord() && node.getLocationNode() != null) {
            results.add(node.getLocationNode());
        }
        for (Map.Entry<Character, TrieNode> entry : node.getChildren().entrySet()) {
            collectLocations(entry.getValue(), results);
        }
    }

    private void collectWords(TrieNode node, List<String> results) {
        if (node.isEndOfWord() && node.getWord() != null) {
            results.add(node.getWord());
        }
        for (Map.Entry<Character, TrieNode> entry : node.getChildren().entrySet()) {
            collectWords(entry.getValue(), results);
        }
    }

    private String normalize(String s) {
        return s.trim().toLowerCase();
    }

    // ── CampusTree Integration ────────────────────────────────────────────────

    /**
     * Crawls the provided {@link CampusTree} and indexes every location by its
     * unique ID and its individual descriptive name tokens.
     *
     * <p>Allows searching by room code (e.g. "b107") as well as keyword (e.g. "network").
     *
     * <p><b>Time Complexity:</b> O(N * L) where N = total tree nodes, L = average key length
     *
     * @param tree the campus tree to index
     */
    public void indexCampusTree(CampusTree tree) {
        if (tree == null) return;
        List<LocationNode> allNodes = tree.dfsTraversal();
        for (LocationNode node : allNodes) {
            // 1. Index primary unique ID
            insert(node.getId(), node);

            // 2. Index room code prefix if applicable (e.g., "b107" from "B107 - OS Lab")
            String[] tokens = node.getName().split("[\\s\\-]+");
            for (String token : tokens) {
                if (token.length() >= 2) {
                    insert(token, node);
                }
            }
        }
    }

    // ── Metrics ───────────────────────────────────────────────────────────────

    public int size() {
        return wordCount;
    }

    public boolean isEmpty() {
        return wordCount == 0;
    }
}

