/*
 * This file is part of Maintenance - https://github.com/kennytv/Maintenance
 * Copyright (C) 2026 Maintenance contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
// Centering formula and font metrics adapted from AdvancedServerList (MIT).
// See META-INF/maintenance-centering-NOTICE.txt for attribution and license.
package eu.kennytv.maintenance.core.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

public final class MotdCentering {
    private static final String CENTER_TAG = "<center>";
    private static final int MOTD_WIDTH = 270;
    private static final int SPACE_WIDTH = 4;

    private MotdCentering() {
    }

    /**
     * Centers marked lines after placeholders have been resolved. Parsing the entire
     * message once preserves formatting (including gradients) spanning line breaks.
     *
     * @param message the MOTD with resolved placeholders
     * @param parser the existing MiniMessage parser
     * @return the formatted MOTD
     */
    public static Component center(final String message, final Function<String, Component> parser) {
        final String[] sourceLines = message.replace("<br>", "\n").replace("\r\n", "\n").split("\n", -1);
        final boolean[] centered = new boolean[sourceLines.length];
        boolean hasCenteredLine = false;
        for (int i = 0; i < sourceLines.length; i++) {
            if (sourceLines[i].regionMatches(true, 0, CENTER_TAG, 0, CENTER_TAG.length())) {
                centered[i] = true;
                hasCenteredLine = true;
                sourceLines[i] = sourceLines[i].substring(CENTER_TAG.length());
            }
        }
        if (!hasCenteredLine) {
            return parser.apply(message);
        }

        final List<Component> lines = splitLines(parser.apply(String.join("\n", sourceLines)));
        final TextComponent.Builder result = Component.text();
        for (int i = 0; i < lines.size(); i++) {
            if (i != 0) {
                result.append(Component.newline());
            }
            if (i < centered.length && centered[i]) {
                final WidthListener listener = new WidthListener();
                ComponentFlattener.textOnly().flatten(lines.get(i), listener);
                // Match AdvancedServerList's one-pixel adjustment and rounding.
                final int padding = (int) Math.max(0, Math.floor((MOTD_WIDTH - listener.width) / 2.0) - 1);
                result.append(Component.text(" ".repeat(padding / SPACE_WIDTH)));
            }
            result.append(lines.get(i));
        }
        return result.build();
    }

    private static List<Component> splitLines(final Component component) {
        final List<Component> lines = new ArrayList<>();
        if (component instanceof TextComponent text) {
            for (final String line : text.content().split("\n", -1)) {
                // Materialize text so MiniMessage's virtual gradient metadata cannot
                // reintroduce the original line breaks during later rendering.
                lines.add(Component.text(line, text.style()));
            }
        } else {
            lines.add(component.children(List.of()));
        }
        for (final Component child : component.children()) {
            final List<Component> childLines = splitLines(child);
            final int last = lines.size() - 1;
            lines.set(last, lines.get(last).append(childLines.getFirst()));
            for (int i = 1; i < childLines.size(); i++) {
                lines.add(Component.empty().style(component.style()).append(childLines.get(i)));
            }
        }
        return lines;
    }

    private static Map<String, FontInfo> loadFontWidths() {
        try (final InputStream stream = MotdCentering.class.getResourceAsStream("/maintenance-char-widths.json.gz")) {
            if (stream == null) {
                return Map.of();
            }
            try (final InputStreamReader reader = new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8)) {
                return new Gson().fromJson(reader, new TypeToken<Map<String, FontInfo>>() { }.getType());
            }
        } catch (final IOException | RuntimeException e) {
            // A missing/damaged resource must not break server pings; use vanilla fallback widths.
            return Map.of();
        }
    }

    private static final class Fonts {
        private static final Map<String, FontInfo> WIDTHS = loadFontWidths();
    }

    private static final class WidthListener implements FlattenerListener {
        private final Deque<Style> styles = new ArrayDeque<>();
        private double width;

        @Override
        public void pushStyle(final Style style) {
            styles.push(styles.isEmpty() ? style : style.merge(styles.peek(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET));
        }

        @Override
        public void component(final String text) {
            final Style style = styles.peek();
            final boolean bold = style.hasDecoration(TextDecoration.BOLD);
            final String fontName = style.font() == null ? "minecraft:default" : style.font().asString();
            final FontInfo font = Fonts.WIDTHS.getOrDefault(fontName, Fonts.WIDTHS.get("minecraft:default"));
            text.codePoints().forEach(codePoint -> {
                final int index = font == null ? -1 : Arrays.binarySearch(font.codePoints, codePoint);
                width += index < 0 ? (bold ? 7 : 6) : (bold ? font.advanceBf[index] : font.advance[index]);
            });
        }

        @Override
        public void popStyle(final Style style) {
            styles.pop();
        }
    }

    // Keep this a class for the Gson version provided by all supported platforms.
    private static final class FontInfo {
        private int[] codePoints;
        private float[] advance;
        private float[] advanceBf;
    }
}
