package dev.atlasmod.core.invalidation;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central invalidation engine. Subsystems register listeners for specific
 * invalidation events and get notified when state changes.
 */
public final class InvalidationEngine {

    private final Map<InvalidationEvent, List<Consumer<InvalidationEvent>>> listeners = new EnumMap<>(InvalidationEvent.class);

    public void register(InvalidationEvent event, Consumer<InvalidationEvent> listener) {
        listeners.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void fire(InvalidationEvent event) {
        var list = listeners.get(event);
        if (list != null) {
            for (var listener : list) {
                listener.accept(event);
            }
        }
    }

    public void clear() {
        listeners.clear();
    }
}
