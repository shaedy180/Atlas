package dev.atlasmod.api.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link AtlasPlugin} implementation for discovery on NeoForge.
 * The class needs a public no-argument constructor. Fabric ignores this and
 * uses the {@code "atlas"} entrypoint instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AtlasEntrypoint {
}
