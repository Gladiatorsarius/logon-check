package logon.check.swordsmp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerActivityManagerTest {
    @TempDir Path directory;
    private final MutableClock clock = new MutableClock();
    private PlayerActivityManager manager;
    private final UUID player = UUID.randomUUID();

    @BeforeEach void initialize() {
        manager = new PlayerActivityManager(clock);
        manager.initialize(directory.toFile());
    }

    @Test void disconnectCountsSessionBetweenPeriodicChecks() {
        manager.startSession(player);
        clock.advanceMinutes(31);
        assertEquals(31, manager.endSession(player, 30));
        assertEquals(clock.millis(), manager.getLastLogin(player));
    }

    @Test void shortSessionDoesNotCount() {
        manager.startSession(player);
        clock.advanceMinutes(29);
        manager.endSession(player, 30);
        assertNull(manager.getLastLogin(player));
    }

    @Test void qualifiedOnlineSessionStaysActive() {
        manager.startSession(player);
        clock.advanceMinutes(30);
        manager.checkSessions(List.of(player), 30);
        clock.advanceMinutes(120);
        manager.checkSessions(List.of(player), 30);
        assertFalse(manager.isInactive(player, 1));
        assertEquals(clock.millis(), manager.getLastLogin(player));
        clock.advanceMinutes(1);
        manager.endSession(player, 30);
        assertEquals(clock.millis(), manager.getLastLogin(player));
    }

    @Test void batchSavesAllPlayersAndReloads() {
        UUID second = UUID.randomUUID();
        manager.startSession(player);
        manager.startSession(second);
        clock.advanceMinutes(30);
        manager.checkSessions(List.of(player, second), 30);
        PlayerActivityManager reloaded = new PlayerActivityManager(clock);
        reloaded.initialize(directory.toFile());
        assertEquals(clock.millis(), reloaded.getLastLogin(player));
        assertEquals(clock.millis(), reloaded.getLastLogin(second));
    }

    @Test void initializeClearsAllPreviousServerState() throws Exception {
        manager.startSession(player);
        clock.advanceMinutes(30);
        manager.checkAndCountSession(player, 30);
        Path fresh = Files.createDirectory(directory.resolve("fresh"));
        manager.initialize(fresh.toFile());
        assertNull(manager.getLastLogin(player));
        assertFalse(manager.checkAndCountSession(player, 30));
        assertEquals(-1, manager.endSession(player, 30));
    }

    @Test void failedSavePreservesOriginalAndRetries() throws Exception {
        manager.updateLastLogin(player);
        Path target = directory.resolve("logon-check-data.json");
        Path backup = directory.resolve("original.json");
        Files.move(target, backup);
        Files.createDirectory(target);
        Files.writeString(target.resolve("block-replacement"), "occupied");
        clock.advanceMinutes(1);
        manager.updateLastLogin(player);
        PlayerActivityManager original = new PlayerActivityManager(clock);
        Path originalDirectory = Files.createDirectory(directory.resolve("original"));
        Files.copy(backup, originalDirectory.resolve("logon-check-data.json"));
        original.initialize(originalDirectory.toFile());
        assertEquals(clock.millis() - 60_000, original.getLastLogin(player));
        Files.delete(target.resolve("block-replacement"));
        Files.delete(target);
        manager.checkSessions(List.of(), 30);
        PlayerActivityManager reloaded = new PlayerActivityManager(clock);
        reloaded.initialize(directory.toFile());
        assertEquals(clock.millis(), reloaded.getLastLogin(player));
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(file -> file.getFileName().toString().endsWith(".tmp")));
        }
    }

    private java.util.List<Path> backups() throws Exception {
        Path backups = directory.resolve("logon-check-backups");
        if (!Files.exists(backups)) return java.util.List.of();
        try (var files = Files.list(backups)) {
            return files.sorted(java.util.Comparator.reverseOrder()).toList();
        }
    }

    @Test void backupTimingAndRetentionKeepTenNewest() throws Exception {
        manager.updateLastLogin(player);
        clock.advanceMinutes(29);
        manager.backupIfDue();
        assertTrue(backups().isEmpty());
        clock.advanceMinutes(1);
        manager.backupIfDue();
        assertEquals(1, backups().size());
        for (int i = 0; i < 11; i++) {
            clock.advanceMinutes(30);
            manager.updateLastLogin(player);
            manager.backupIfDue();
        }
        assertEquals(10, backups().size());
        Files.writeString(directory.resolve("logon-check-data.json"), "{broken");
        manager.initialize(directory.toFile());
        assertEquals(clock.millis(), manager.getLastLogin(player));
    }

    @Test void corruptPrimaryRecoversNewestValidAndPreservesDamagedBytes() throws Exception {
        manager.updateLastLogin(player);
        clock.advanceMinutes(30);
        manager.backupIfDue();
        long backedUp = manager.getLastLogin(player);
        clock.advanceMinutes(30);
        manager.updateLastLogin(player);
        manager.backupIfDue();
        Files.writeString(backups().get(0), "invalid newest backup");
        String damaged = "{broken primary";
        Files.writeString(directory.resolve("logon-check-data.json"), damaged);
        manager.initialize(directory.toFile());
        assertEquals(backedUp, manager.getLastLogin(player));
        try (var files = Files.list(directory)) {
            Path archive = files.filter(path -> path.getFileName().toString().startsWith("logon-check-data.corrupt-")).findFirst().orElseThrow();
            assertEquals(damaged, Files.readString(archive));
        }
        manager.initialize(directory.toFile());
        assertEquals(backedUp, manager.getLastLogin(player));
    }

    @Test void corruptPrimaryAndBackupStopWithoutChangingEither() throws Exception {
        manager.updateLastLogin(player);
        clock.advanceMinutes(30);
        manager.backupIfDue();
        Path backup = backups().get(0);
        Files.writeString(backup, "null");
        Path primary = directory.resolve("logon-check-data.json");
        Files.writeString(primary, "{broken");
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> manager.initialize(directory.toFile()));
        assertTrue(failure.getMessage().contains("startup stopped"));
        assertEquals("{broken", Files.readString(primary));
        assertEquals("null", Files.readString(backup));
    }

    @Test void invalidRecordsAndTruncatedEmptyHistoryDoNotSilentlyReset() throws Exception {
        for (String contents : java.util.List.of("", "null", "[]", "{\"" + player + "\":null}",
                "{\"" + player + "\":-1}", "{\"" + player + "\":1.5}", "{\"bad-uuid\":1}")) {
            Path primary = directory.resolve("logon-check-data.json");
            Files.writeString(primary, contents);
            assertThrows(IllegalStateException.class, () -> manager.initialize(directory.toFile()), contents);
            assertEquals(contents, Files.readString(primary));
        }
    }

    @Test void missingPrimaryCanRecoverBackup() throws Exception {
        manager.updateLastLogin(player);
        clock.advanceMinutes(30);
        manager.backupIfDue();
        Files.delete(directory.resolve("logon-check-data.json"));
        manager.initialize(directory.toFile());
        assertNotNull(manager.getLastLogin(player));
        assertTrue(Files.exists(directory.resolve("logon-check-data.json")));
    }

    @Test void failedBackupDoesNotDeletePreviousRecoveryFiles() throws Exception {
        manager.updateLastLogin(player);
        clock.advanceMinutes(30);
        manager.backupIfDue();
        Path first = backups().get(0);
        String content = Files.readString(first);
        Path primary = directory.resolve("logon-check-data.json");
        Files.delete(primary);
        Files.createDirectory(primary);
        Files.writeString(primary.resolve("block"), "block");
        manager.updateLastLogin(player);
        clock.advanceMinutes(30);
        manager.backupIfDue();
        assertEquals(1, backups().size());
        assertEquals(content, Files.readString(first));
    }

    private static class MutableClock extends Clock {
        private long millis = 1_800_000_000_000L;
        void advanceMinutes(long minutes) { millis += minutes * 60_000; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
