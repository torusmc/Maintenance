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
package eu.kennytv.maintenance.core.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MotdCenteringTest {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    @Test
    void plainTextUsesVariableGlyphWidthsAndSpaces() {
        assertPlain(30, "Hello", "Hello"); // 24 pixels
        assertPlain(31, "iiiiiiii", "iiiiiiii"); // 16 pixels
        assertPlain(27, "WWWWWWWW", "WWWWWWWW"); // 48 pixels
        assertPlain(29, "A A A A", "A A A A"); // 36 pixels including spaces
    }

    @Test
    void colorsDoNotAffectWidth() {
        assertStyled(30, "<red>Hello</red>");
    }

    @Test
    void boldUsesGlyphAdvancesIncludingBoldSpaces() {
        assertStyled(29, "<bold>Hello</bold>"); // 29 pixels
        assertStyled(26, "<bold>A A A A A</bold>"); // 55 pixels
    }

    @Test
    void nestedStylesAndExplicitBoldFalseRestoreInheritedState() {
        assertStyled(20, "<bold>AAAAA<red>AAAAA</red>AAAAA</bold>"); // 105 pixels
        assertStyled(25, "<bold>AAAAA<!bold>AAAAA</!bold></bold>"); // 65 pixels
    }

    @Test
    void gradientAndItalicFormattingArePreserved() {
        assertStyled(30, "<gradient:red:blue><italic>Hello</italic></gradient>");
    }

    @Test
    void bothLinesAreCenteredIndependently() {
        for (final String separator : List.of("<br>", "\n", "\r\n")) {
            assertEquals(" ".repeat(30) + "Hello\n" + " ".repeat(27) + "WWWWWWWW",
                    plain(center("<center>Hello" + separator + "<center>WWWWWWWW")));
        }
    }

    @Test
    void mixedLinesKeepTheirOwnAlignment() {
        assertEquals("Hello\n" + " ".repeat(27) + "WWWWWWWW",
                plain(center("Hello<br><center>WWWWWWWW")));
        assertEquals(" ".repeat(30) + "Hello\nWWWWWWWW",
                plain(center("<center>Hello<br>WWWWWWWW")));
    }

    @Test
    void formattingAndGradientsCanSpanLineBreaks() {
        final String text = "<gradient:red:blue><bold>Hello\nWWWWWWWW</bold></gradient>";
        final Component actual = center("<center>" + text.replace("\n", "\n<center>"));
        final List<Glyph> expected = glyphs(MINI_MESSAGE.deserialize(text)).stream().filter(glyph -> glyph.codePoint != '\n').toList();
        final List<Glyph> withoutPadding = glyphs(actual).stream().filter(glyph -> glyph.codePoint != ' ' && glyph.codePoint != '\n').toList();
        assertEquals(expected, withoutPadding);
        assertEquals(" ".repeat(29) + "Hello\n" + " ".repeat(26) + "WWWWWWWW", plain(actual));
    }

    @Test
    void emptyAndTrailingLinesArePreserved() {
        assertEquals(" ".repeat(33) + "\n" + " ".repeat(33), plain(center("<center><br><center>")));
        assertEquals("\n" + " ".repeat(30) + "Hello\n", plain(center("<br><center>Hello<br>")));
    }

    @Test
    void longAndExactlyFullLinesAreNotPadded() {
        assertPlain(0, "W".repeat(45), "W".repeat(45)); // 270 pixels
        assertPlain(0, "W".repeat(80), "W".repeat(80));
        assertPlain(0, "W".repeat(44), "W".repeat(44)); // Rounding leaves no room for a space
    }

    @Test
    void allBundledFontsUseTheirOwnMetrics() {
        assertStyled(27, "<font:minecraft:default>AAAAAAAA</font>"); // 48 pixels
        assertStyled(29, "<font:minecraft:uniform>AAAAAAAA</font>"); // 32 pixels
        assertStyled(27, "<font:minecraft:alt>AAAAAAAA</font>");
        assertStyled(29, "<font:minecraft:alt>llllllll</font>"); // 32 pixels; default is 24
        assertStyled(25, "<font:minecraft:uniform><bold>" + "A".repeat(15) + "</bold></font>"); // 67.5 pixels
    }

    @Test
    void nestedFontsRestorePreviousFont() {
        assertStyled(26, "<font:minecraft:uniform>AAAA<font:minecraft:alt>AAAA</font>AAAA</font>"); // 56 pixels
    }

    @Test
    void customFontsAndGlyphsUseFallbackWithoutLosingTheirStyle() {
        assertStyled(27, "<font:custom:icons>AAAAAAAA</font>");
        assertStyled(27, "<font:custom:icons>" + "\ue000".repeat(8) + "</font>");
        assertStyled(26, "<bold>" + "\ue000".repeat(8) + "</bold>");
        assertStyled(27, "😀".repeat(8)); // Count each supplementary code point once
    }

    @Test
    void markerIsCaseInsensitiveAndRemoved() {
        assertEquals(" ".repeat(30) + "Hello", plain(center("<CeNtEr>Hello")));
    }

    @Test
    void messagesWithoutLeadingMarkerAreUnmodified() {
        for (final String message : List.of("", "<red>Hello", "<bold>Hello<br>World", "<gradient:red:blue>Hello\nWorld",
                "<font:custom:icons>\ue000", "Text <center>Hello", "\\<center>Hello")) {
            final Component parsed = MINI_MESSAGE.deserialize(message);
            assertSame(parsed, MotdCentering.center(message, input -> {
                assertEquals(message, input);
                return parsed;
            }));
        }
    }

    private static Component center(final String message) {
        return MotdCentering.center(message, MINI_MESSAGE::deserialize);
    }

    private static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static void assertPlain(final int spaces, final String message, final String visible) {
        assertEquals(" ".repeat(spaces) + visible, plain(center("<center>" + message)));
    }

    private static void assertStyled(final int spaces, final String message) {
        final Component expected = Component.text(" ".repeat(spaces)).append(MINI_MESSAGE.deserialize(message));
        assertEquals(glyphs(expected), glyphs(center("<center>" + message)));
    }

    private static List<Glyph> glyphs(final Component component) {
        final List<Glyph> glyphs = new ArrayList<>();
        ComponentFlattener.textOnly().flatten(component, new FlattenerListener() {
            private final Deque<Style> styles = new ArrayDeque<>();

            @Override
            public void pushStyle(final Style style) {
                styles.push(styles.isEmpty() ? style : style.merge(styles.peek(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET));
            }

            @Override
            public void component(final String text) {
                text.codePoints().forEach(codePoint -> glyphs.add(new Glyph(codePoint, styles.peek())));
            }

            @Override
            public void popStyle(final Style style) {
                styles.pop();
            }
        });
        return glyphs;
    }

    private record Glyph(int codePoint, Style style) {
    }
}
