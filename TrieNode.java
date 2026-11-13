import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Node structure for the {@link LocationTrie} prefix search tree.
 *
 * <p>Each node stores character branches leading to child nodes, an end-of-word
 * flag, and a direct pointer to the target {@link LocationNode} in the campus hierarchy.
 *
 * <p><b>Academic Complexity Summary:</b>
 * <ul>
 *   <li>Child lookup / insertion: O(1) average time</li>
 *   <li>Memory: Dynamic allocation per active character branch</li>
 * </ul>
 */
public class TrieNode {

    private final Map<Character, TrieNode> children;
    private boolean isEndOfWord;
    private LocationNode locationNode;
    private String word;

    public TrieNode() {
        this.children = new HashMap<>();
        this.isEndOfWord = false;
        this.locationNode = null;
        this.word = null;
    }

    public boolean hasChild(char c) {
        return children.containsKey(c);
    }

    public TrieNode getChild(char c) {
        return children.get(c);
    }

    public TrieNode getOrCreateChild(char c) {
        return children.computeIfAbsent(c, k -> new TrieNode());
    }

    public Map<Character, TrieNode> getChildren() {
        return Collections.unmodifiableMap(children);
    }

    public boolean isEndOfWord() {
        return isEndOfWord;
    }

    public void setEndOfWord(boolean endOfWord) {
        isEndOfWord = endOfWord;
    }

    public LocationNode getLocationNode() {
        return locationNode;
    }

    public void setLocationNode(LocationNode locationNode) {
        this.locationNode = locationNode;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public int getChildCount() {
        return children.size();
    }
}

