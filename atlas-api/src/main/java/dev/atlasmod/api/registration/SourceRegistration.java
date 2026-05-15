package dev.atlasmod.api.registration;

import dev.atlasmod.api.internal.AtlasRegistrationSink;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.unlock.UnlockCondition;
import dev.atlasmod.core.visibility.VisibilityPolicy;

import java.util.Objects;

public final class SourceRegistration {

    private final AtlasRegistrationSink sink;
    private final String ownerModId;
    private final String id;
    private EntryKey entry;
    private AcquisitionSource.SourceType type = AcquisitionSource.SourceType.CUSTOM;
    private String description;
    private String detail;
    private UnlockCondition unlockCondition;
    private VisibilityPolicy visibility = VisibilityPolicy.VISIBLE;
    private boolean renewable;

    public SourceRegistration(AtlasRegistrationSink sink, String ownerModId, String id) {
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.ownerModId = Objects.requireNonNull(ownerModId, "ownerModId must not be null");
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    public SourceRegistration entry(EntryKey entry) {
        this.entry = entry;
        return this;
    }

    public SourceRegistration type(AcquisitionSource.SourceType type) {
        this.type = type;
        return this;
    }

    public SourceRegistration description(String description) {
        this.description = description;
        return this;
    }

    public SourceRegistration detail(String detail) {
        this.detail = detail;
        return this;
    }

    public SourceRegistration unlock(UnlockCondition unlockCondition) {
        this.unlockCondition = unlockCondition;
        return this;
    }

    public SourceRegistration visibility(VisibilityPolicy visibility) {
        this.visibility = visibility;
        return this;
    }

    public SourceRegistration renewable(boolean renewable) {
        this.renewable = renewable;
        return this;
    }

    public void register() {
        if (entry == null) {
            throw new IllegalStateException("Source " + id + " must declare an entry");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalStateException("Source " + id + " must declare a description");
        }
        sink.addSource(new AcquisitionSource(
                id,
                ownerModId,
                entry,
                Objects.requireNonNull(type, "type must not be null"),
                description,
                detail,
                unlockCondition,
                visibility,
                renewable
        ));
    }
}
