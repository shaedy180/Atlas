package dev.atlasmod.api.registration;

import dev.atlasmod.api.internal.AtlasRegistrationSink;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.registry.AtlasInfoPage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InfoPageRegistration {

    private final AtlasRegistrationSink sink;
    private final String ownerModId;
    private final String id;
    private EntryKey entry;
    private String title;
    private String body;
    private final List<String> references = new ArrayList<>();

    public InfoPageRegistration(AtlasRegistrationSink sink, String ownerModId, String id) {
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.ownerModId = Objects.requireNonNull(ownerModId, "ownerModId must not be null");
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    public InfoPageRegistration entry(EntryKey entry) {
        this.entry = entry;
        return this;
    }

    public InfoPageRegistration title(String title) {
        this.title = title;
        return this;
    }

    public InfoPageRegistration body(String body) {
        this.body = body;
        return this;
    }

    public InfoPageRegistration reference(String reference) {
        this.references.add(Objects.requireNonNull(reference, "reference must not be null"));
        return this;
    }

    public void register() {
        if (entry == null) {
            throw new IllegalStateException("Info page " + id + " must declare an entry");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalStateException("Info page " + id + " must declare a title");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Info page " + id + " must declare a body");
        }
        sink.addInfoPage(new AtlasInfoPage(id, ownerModId, entry, title, body, references));
    }
}
