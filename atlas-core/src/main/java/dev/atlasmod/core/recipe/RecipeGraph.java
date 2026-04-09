package dev.atlasmod.core.recipe;

import dev.atlasmod.core.entry.EntryKey;

import java.util.*;

/**
 * Directed graph of recipe nodes. Supports multi-path resolution, cycle detection,
 * and cost estimation for the Planning pillar.
 */
public final class RecipeGraph {

    private final Map<String, RecipeNode> nodesById = new LinkedHashMap<>();
    private final Map<EntryKey, List<RecipeNode>> byOutput = new HashMap<>();

    public void addNode(RecipeNode node) {
        nodesById.put(node.id(), node);
        for (EntryKey output : node.outputs()) {
            byOutput.computeIfAbsent(output, k -> new ArrayList<>()).add(node);
        }
    }

    public Optional<RecipeNode> getNode(String id) {
        return Optional.ofNullable(nodesById.get(id));
    }

    /**
     * Returns all recipe nodes that produce the given entry.
     */
    public List<RecipeNode> recipesFor(EntryKey output) {
        return byOutput.getOrDefault(output, List.of());
    }

    /**
     * Returns all registered nodes.
     */
    public Collection<RecipeNode> allNodes() {
        return Collections.unmodifiableCollection(nodesById.values());
    }

    /**
     * Detects cycles reachable from the given entry.
     * Returns true if a cycle is found.
     */
    public boolean hasCycle(EntryKey start) {
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        for (RecipeNode node : recipesFor(start)) {
            if (detectCycleDfs(node, visited, inStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean detectCycleDfs(RecipeNode node, Set<String> visited, Set<String> inStack) {
        if (inStack.contains(node.id())) return true;
        if (visited.contains(node.id())) return false;

        visited.add(node.id());
        inStack.add(node.id());

        for (var input : node.inputs()) {
            EntryKey inputEntry = new EntryKey(input.type(), input.id());
            for (RecipeNode producer : recipesFor(inputEntry)) {
                if (detectCycleDfs(producer, visited, inStack)) {
                    return true;
                }
            }
        }

        inStack.remove(node.id());
        return false;
    }

    public int size() {
        return nodesById.size();
    }

    public void clear() {
        nodesById.clear();
        byOutput.clear();
    }
}
