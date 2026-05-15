package dev.atlasmod.fabric;

import dev.atlasmod.core.category.RecipeCategory;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.registry.AtlasInfoPage;
import dev.atlasmod.core.registry.AtlasRegistrySnapshot;
import dev.atlasmod.core.registry.AtlasRendererBinding;
import dev.atlasmod.core.unlock.UnlockCondition;
import dev.atlasmod.core.visibility.VisibilityPolicy;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AtlasSnapshotSerialization {

    private AtlasSnapshotSerialization() {
    }

    public static void writeSnapshot(FriendlyByteBuf buf, AtlasRegistrySnapshot snapshot) {
        buf.writeCollection(snapshot.categories(), AtlasSnapshotSerialization::writeCategory);
        buf.writeCollection(snapshot.recipes(), AtlasSnapshotSerialization::writeRecipe);

        List<AcquisitionSource> allSources = new ArrayList<>();
        snapshot.sourcesByEntry().values().forEach(allSources::addAll);
        buf.writeCollection(allSources, AtlasSnapshotSerialization::writeSource);

        List<AtlasInfoPage> allPages = new ArrayList<>();
        snapshot.infoPagesByEntry().values().forEach(allPages::addAll);
        buf.writeCollection(allPages, AtlasSnapshotSerialization::writeInfoPage);

        buf.writeCollection(snapshot.renderersByKey().values(), AtlasSnapshotSerialization::writeRenderer);
    }

    public static AtlasRegistrySnapshot readSnapshot(FriendlyByteBuf buf) {
        Map<String, RecipeCategory> categories = new LinkedHashMap<>();
        for (RecipeCategory category : buf.readCollection(ArrayList::new, AtlasSnapshotSerialization::readCategory)) {
            categories.put(category.id(), category);
        }

        Map<String, RecipeNode> recipes = new LinkedHashMap<>();
        for (RecipeNode recipe : buf.readCollection(ArrayList::new, AtlasSnapshotSerialization::readRecipe)) {
            recipes.put(recipe.id(), recipe);
        }

        Map<EntryKey, List<AcquisitionSource>> sources = new LinkedHashMap<>();
        for (AcquisitionSource source : buf.readCollection(ArrayList::new, AtlasSnapshotSerialization::readSource)) {
            sources.computeIfAbsent(source.entry(), ignored -> new ArrayList<>()).add(source);
        }

        Map<EntryKey, List<AtlasInfoPage>> pages = new LinkedHashMap<>();
        for (AtlasInfoPage page : buf.readCollection(ArrayList::new, AtlasSnapshotSerialization::readInfoPage)) {
            pages.computeIfAbsent(page.entry(), ignored -> new ArrayList<>()).add(page);
        }

        Map<String, AtlasRendererBinding> renderers = new LinkedHashMap<>();
        for (AtlasRendererBinding renderer : buf.readCollection(ArrayList::new, AtlasSnapshotSerialization::readRenderer)) {
            renderers.put(renderer.key(), renderer);
        }

        return new AtlasRegistrySnapshot(categories, recipes, sources, pages, renderers);
    }

    private static void writeCategory(FriendlyByteBuf buf, RecipeCategory category) {
        buf.writeUtf(category.id());
        buf.writeUtf(category.ownerModId());
        buf.writeUtf(category.name());
        writeNullableString(buf, category.icon());
        buf.writeVarInt(category.order());
    }

    private static RecipeCategory readCategory(FriendlyByteBuf buf) {
        return new RecipeCategory(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                readNullableString(buf),
                buf.readVarInt()
        );
    }

    private static void writeRecipe(FriendlyByteBuf buf, RecipeNode recipe) {
        buf.writeUtf(recipe.id());
        buf.writeUtf(recipe.ownerModId());
        buf.writeUtf(recipe.categoryId());
        buf.writeCollection(recipe.inputs(), AtlasSnapshotSerialization::writeIngredient);
        buf.writeCollection(recipe.outputs(), AtlasSnapshotSerialization::writeEntry);
        buf.writeNullable(recipe.station().orElse(null), AtlasSnapshotSerialization::writeStation);
        buf.writeVarInt(recipe.processingTime());
        buf.writeVarInt(recipe.energyCost());
        buf.writeVarInt(recipe.gridWidth());
        buf.writeVarInt(recipe.gridHeight());
        writeUnlock(buf, recipe.unlockCondition().orElse(null));
        buf.writeEnum(recipe.visibilityPolicy());
        buf.writeCollection(recipe.searchAliases(), FriendlyByteBuf::writeUtf);
        writeNullableString(buf, recipe.rendererKey().orElse(null));
        buf.writeCollection(recipe.sources(), AtlasSnapshotSerialization::writeSource);
    }

    private static RecipeNode readRecipe(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String ownerModId = buf.readUtf();
        String categoryId = buf.readUtf();
        List<IngredientKey> inputs = buf.readCollection(ArrayList::new, AtlasSnapshotSerialization::readIngredient);
        List<EntryKey> outputs = buf.readCollection(ArrayList::new, AtlasSnapshotSerialization::readEntry);
        StationKey station = buf.readNullable(AtlasSnapshotSerialization::readStation);
        int processingTime = buf.readVarInt();
        int energyCost = buf.readVarInt();
        int gridWidth = buf.readVarInt();
        int gridHeight = buf.readVarInt();
        UnlockCondition unlockCondition = readUnlock(buf);
        VisibilityPolicy visibilityPolicy = buf.readEnum(VisibilityPolicy.class);
        List<String> aliases = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        String rendererKey = readNullableString(buf);
        List<AcquisitionSource> sources = buf.readCollection(ArrayList::new, AtlasSnapshotSerialization::readSource);

        return RecipeNode.builder(id, ownerModId, categoryId)
                .inputs(inputs)
                .outputs(outputs)
                .station(station)
                .processingTime(processingTime)
                .energyCost(energyCost)
                .grid(gridWidth, gridHeight)
                .unlockCondition(unlockCondition)
                .visibilityPolicy(visibilityPolicy)
                .searchAliases(aliases)
                .rendererKey(rendererKey)
                .sources(sources)
                .build();
    }

    private static void writeSource(FriendlyByteBuf buf, AcquisitionSource source) {
        buf.writeUtf(source.id());
        buf.writeUtf(source.ownerModId());
        writeEntry(buf, source.entry());
        buf.writeEnum(source.type());
        buf.writeUtf(source.description());
        writeNullableString(buf, source.optionalDetail().orElse(null));
        writeUnlock(buf, source.optionalUnlockCondition().orElse(null));
        buf.writeEnum(source.visibilityPolicy());
        buf.writeBoolean(source.renewable());
    }

    private static AcquisitionSource readSource(FriendlyByteBuf buf) {
        return new AcquisitionSource(
                buf.readUtf(),
                buf.readUtf(),
                readEntry(buf),
                buf.readEnum(AcquisitionSource.SourceType.class),
                buf.readUtf(),
                readNullableString(buf),
                readUnlock(buf),
                buf.readEnum(VisibilityPolicy.class),
                buf.readBoolean()
        );
    }

    private static void writeInfoPage(FriendlyByteBuf buf, AtlasInfoPage infoPage) {
        buf.writeUtf(infoPage.id());
        buf.writeUtf(infoPage.ownerModId());
        writeEntry(buf, infoPage.entry());
        buf.writeUtf(infoPage.title());
        buf.writeUtf(infoPage.body());
        buf.writeCollection(infoPage.references(), FriendlyByteBuf::writeUtf);
    }

    private static AtlasInfoPage readInfoPage(FriendlyByteBuf buf) {
        return new AtlasInfoPage(
                buf.readUtf(),
                buf.readUtf(),
                readEntry(buf),
                buf.readUtf(),
                buf.readUtf(),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf)
        );
    }

    private static void writeRenderer(FriendlyByteBuf buf, AtlasRendererBinding renderer) {
        buf.writeUtf(renderer.key());
        buf.writeUtf(renderer.ownerModId());
        buf.writeUtf(renderer.description());
        writeNullableString(buf, renderer.optionalCategoryId().orElse(null));
        writeNullableString(buf, renderer.optionalRecipeId().orElse(null));
    }

    private static AtlasRendererBinding readRenderer(FriendlyByteBuf buf) {
        return new AtlasRendererBinding(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                readNullableString(buf),
                readNullableString(buf)
        );
    }

    private static void writeEntry(FriendlyByteBuf buf, EntryKey entry) {
        buf.writeUtf(entry.type());
        buf.writeUtf(entry.id());
    }

    private static EntryKey readEntry(FriendlyByteBuf buf) {
        return new EntryKey(buf.readUtf(), buf.readUtf());
    }

    private static void writeIngredient(FriendlyByteBuf buf, IngredientKey ingredient) {
        buf.writeUtf(ingredient.type());
        buf.writeUtf(ingredient.id());
        buf.writeBoolean(ingredient.tagBased());
    }

    private static IngredientKey readIngredient(FriendlyByteBuf buf) {
        String type = buf.readUtf();
        String id = buf.readUtf();
        boolean tagBased = buf.readBoolean();
        if ("empty".equals(type) && id.isEmpty() && !tagBased) {
            return IngredientKey.EMPTY;
        }
        return new IngredientKey(type, id, tagBased);
    }

    private static void writeStation(FriendlyByteBuf buf, StationKey station) {
        buf.writeUtf(station.id());
    }

    private static StationKey readStation(FriendlyByteBuf buf) {
        return new StationKey(buf.readUtf());
    }

    private static void writeUnlock(FriendlyByteBuf buf, UnlockCondition condition) {
        if (condition == null) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        switch (condition) {
            case UnlockCondition.RequiresStation requiresStation -> {
                buf.writeVarInt(0);
                buf.writeUtf(requiresStation.stationId());
            }
            case UnlockCondition.RequiresDimension requiresDimension -> {
                buf.writeVarInt(1);
                buf.writeUtf(requiresDimension.dimensionId());
            }
            case UnlockCondition.RequiresBiome requiresBiome -> {
                buf.writeVarInt(2);
                buf.writeUtf(requiresBiome.biomeTagOrId());
            }
            case UnlockCondition.RequiresAdvancement requiresAdvancement -> {
                buf.writeVarInt(3);
                buf.writeUtf(requiresAdvancement.advancementId());
            }
            case UnlockCondition.RequiresProgression requiresProgression -> {
                buf.writeVarInt(4);
                buf.writeUtf(requiresProgression.stageKey());
            }
            case UnlockCondition.RequiresCatalyst requiresCatalyst -> {
                buf.writeVarInt(5);
                buf.writeUtf(requiresCatalyst.catalystId());
            }
            case UnlockCondition.Custom custom -> {
                buf.writeVarInt(6);
                buf.writeUtf(custom.reason());
            }
        }
    }

    private static UnlockCondition readUnlock(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        return switch (buf.readVarInt()) {
            case 0 -> new UnlockCondition.RequiresStation(buf.readUtf());
            case 1 -> new UnlockCondition.RequiresDimension(buf.readUtf());
            case 2 -> new UnlockCondition.RequiresBiome(buf.readUtf());
            case 3 -> new UnlockCondition.RequiresAdvancement(buf.readUtf());
            case 4 -> new UnlockCondition.RequiresProgression(buf.readUtf());
            case 5 -> new UnlockCondition.RequiresCatalyst(buf.readUtf());
            case 6 -> new UnlockCondition.Custom(buf.readUtf());
            default -> throw new IllegalArgumentException("Unknown unlock condition type");
        };
    }

    private static void writeNullableString(FriendlyByteBuf buf, String value) {
        buf.writeNullable(value, FriendlyByteBuf::writeUtf);
    }

    private static String readNullableString(FriendlyByteBuf buf) {
        return buf.readNullable(FriendlyByteBuf::readUtf);
    }
}
