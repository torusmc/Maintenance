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
package eu.kennytv.maintenance.core;

import eu.kennytv.maintenance.core.dump.PluginDump;
import eu.kennytv.maintenance.core.util.SenderInfo;
import eu.kennytv.maintenance.core.util.ServerType;
import eu.kennytv.maintenance.core.util.Task;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SettingsPingMessageTest {
    @TempDir
    static Path directory;
    private TestPlugin plugin;

    @BeforeAll
    void initPlugin() {
        plugin = new TestPlugin(directory.toFile());
    }

    @Test
    void resolvesDynamicPlaceholdersBeforeMeasuringEachLine() throws Exception {
        final Settings settings = new Settings(plugin);
        plugin.settings = settings;
        plugin.timer = "00:30";
        settings.config.set("ping-message.messages.default", List.of("<center>%REASON%<br><center>%TIMER%"));
        settings.config.save();
        settings.reloadConfigs();

        settings.setActiveReason("<bold>AAAAAAAA</bold>"); // 56 pixels, including resolved formatting
        assertEquals(" ".repeat(26) + "AAAAAAAA\n" + " ".repeat(30) + "00:30", plain(settings.getRandomPingMessage()));

        settings.setActiveReason("<font:minecraft:uniform>AAAA</font>"); // 16 pixels
        plugin.timer = "00:00:30"; // 40 pixels
        assertEquals(" ".repeat(31) + "AAAA\n" + " ".repeat(28) + "00:00:30", plain(settings.getRandomPingMessage()));
    }

    @Test
    void preservesUncenteredMessagesAndConfiguredModes() throws Exception {
        final Settings settings = new Settings(plugin);
        plugin.settings = settings;
        plugin.timer = "00:30";
        settings.config.set("ping-message.messages.default", List.of("<red>%REASON%<br>Come back in %TIMER%"));
        settings.config.set("ping-message.messages.release", List.of("<center>%MODE%<br>%REASON%"));
        settings.config.save();
        settings.reloadConfigs();
        settings.setActiveReason("AAAA");

        assertEquals("AAAA\nCome back in 00:30", plain(settings.getRandomPingMessage()));
        assertEquals(" ".repeat(28) + "release\nAAAA", plain(settings.getRandomPingMessage("release")));
    }

    private static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    // Exercise real Settings/configuration and placeholder resolution without a running platform.
    private static final class TestPlugin extends MaintenancePlugin {
        private final File directory;
        private String timer = "00:30"; // 26 pixels

        private TestPlugin(final File directory) {
            super("5.1.1", ServerType.VELOCITY);
            this.directory = directory;
        }

        @Override
        public String getTimerMessage() {
            return timer;
        }

        @Override
        public void async(final Runnable runnable) {
        }

        @Override
        protected void executeConsoleCommand(final String command) {
        }

        @Override
        public void broadcast(final Component component) {
        }

        @Override
        public Task startMaintenanceRunnable(final Runnable runnable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<SenderInfo> getOfflinePlayer(final String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<SenderInfo> getOfflinePlayer(final UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getDataFolder() {
            return directory;
        }

        @Override
        public Logger getLogger() {
            return Logger.getLogger("MaintenanceTest");
        }

        @Override
        public String getServerVersion() {
            return "Test";
        }

        @Override
        public List<PluginDump> getPlugins() {
            return List.of();
        }

        @Override
        protected void loadIcon(final File file) {
        }

        @Override
        protected void kickPlayers() {
        }

        @Override
        protected File getPluginFile() {
            return directory;
        }

        @Override
        protected int getOnlinePlayers() {
            return 5;
        }

        @Override
        protected int getMaxPlayers() {
            return 100;
        }
    }
}
