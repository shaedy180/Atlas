package dev.atlasmod.search;

import java.util.Objects;

/**
 * A parsed search query supporting the Atlas context-aware search syntax.
 * <p>
 * Prefixes:
 * <ul>
 *   <li>{@code @mod}   — filter by mod id</li>
 *   <li>{@code #text}  — search in tooltips</li>
 *   <li>{@code $tag}   — filter by item/block tag</li>
 *   <li>{@code >source} — filter by source type</li>
 *   <li>{@code =station} — filter by workstation</li>
 *   <li>{@code ~unlocked} — show only unlocked</li>
 *   <li>{@code !hidden}   — include hidden entries</li>
 *   <li>{@code *renewable} — show only renewable sources</li>
 * </ul>
 */
public record SearchQuery(
        String rawInput,
        String textQuery,
        String modFilter,
        String tooltipFilter,
        String tagFilter,
        String sourceFilter,
        String stationFilter,
        boolean onlyUnlocked,
        boolean includeHidden,
        boolean onlyRenewable
) {
    public static SearchQuery parse(String raw) {
        Objects.requireNonNull(raw, "raw must not be null");

        String text = raw;
        String mod = null, tooltip = null, tag = null, source = null, station = null;
        boolean unlocked = false, hidden = false, renewable = false;

        String[] tokens = raw.split("\\s+");
        var textTokens = new java.util.ArrayList<String>();

        for (String token : tokens) {
            if (token.startsWith("@")) {
                mod = token.substring(1);
            } else if (token.startsWith("#")) {
                tooltip = token.substring(1);
            } else if (token.startsWith("$")) {
                tag = token.substring(1);
            } else if (token.startsWith(">")) {
                source = token.substring(1);
            } else if (token.startsWith("=")) {
                station = token.substring(1);
            } else if (token.equals("~unlocked")) {
                unlocked = true;
            } else if (token.equals("!hidden")) {
                hidden = true;
            } else if (token.equals("*renewable")) {
                renewable = true;
            } else {
                textTokens.add(token);
            }
        }

        String joined = String.join(" ", textTokens);
        return new SearchQuery(raw, joined, mod, tooltip, tag, source, station, unlocked, hidden, renewable);
    }
}
