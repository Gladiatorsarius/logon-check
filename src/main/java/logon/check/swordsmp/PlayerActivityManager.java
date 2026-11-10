package logon.check.swordsmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Comparator;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player login activity data
 * Tracks last login timestamps for all players and current session start times
 */
public class PlayerActivityManager {
    private static final PlayerActivityManager INSTANCE = new PlayerActivityManager();
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();
    
    private final Map<UUID, Long> lastLoginTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> sessionCounted = new ConcurrentHashMap<>(); // Track if session already counted
    private File dataFile;
    private final Clock clock;
    private boolean dirty;
    private Path backupDirectory;
    private long nextBackupAt;
    private static final long BACKUP_INTERVAL_MILLIS = 30 * 60_000L;
    
    private PlayerActivityManager() { this(Clock.systemUTC()); }

    PlayerActivityManager(Clock clock) { this.clock = clock; }
    
    public static PlayerActivityManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize the manager with a data file location
     */
    public void initialize(File configDir) {
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        this.dataFile = new File(configDir, "logon-check-data.json");
        lastLoginTimes.clear();
        sessionStartTimes.clear();
        sessionCounted.clear();
        dirty = false;
        backupDirectory = configDir.toPath().resolve("logon-check-backups");
        loadData();
        nextBackupAt = clock.millis() + BACKUP_INTERVAL_MILLIS;
        LogonCheck.LOGGER.info("Initialized player activity manager");
    }
    
    /**
     * Start tracking a session for a player
     * Called when player logs in
     */
    public void startSession(UUID playerUuid) {
        long now = clock.millis();
        sessionStartTimes.put(playerUuid, now);
        sessionCounted.put(playerUuid, false); // Mark as not yet counted
        LogonCheck.LOGGER.debug("Started session tracking for player {}", playerUuid);
    }
    
    /**
     * Check session duration and update activity if minimum time reached
     * Returns true if session was counted (either now or previously)
     */
    public boolean checkAndCountSession(UUID playerUuid, int minimumSessionMinutes) {
        boolean counted = updateSession(playerUuid, minimumSessionMinutes);
        saveData();
        return counted;
    }

    public void checkSessions(Collection<UUID> players, int minimumSessionMinutes) {
        for (UUID player : players) {
            updateSession(player, minimumSessionMinutes);
        }
        saveData();
    }

    private boolean updateSession(UUID playerUuid, int minimumSessionMinutes) {
        Long sessionStart = sessionStartTimes.get(playerUuid);
        if (sessionStart == null) {
            return false; // No active session
        }
        
        // Qualified sessions keep their activity current while online.
        Boolean alreadyCounted = sessionCounted.get(playerUuid);
        if (Boolean.TRUE.equals(alreadyCounted)) {
            lastLoginTimes.put(playerUuid, clock.millis());
            dirty = true;
            return true;
        }
        
        long now = clock.millis();
        long sessionDuration = now - sessionStart;
        double sessionMinutes = sessionDuration / (60.0 * 1000.0); // milliseconds to minutes
        
        // Check if session has reached minimum duration
        if (sessionMinutes >= minimumSessionMinutes) {
            lastLoginTimes.put(playerUuid, now);
            sessionCounted.put(playerUuid, true); // Mark as counted
            dirty = true;
            LogonCheck.LOGGER.info("Session for player {} reached {} minutes (minimum: {} min) - counted as activity",
                playerUuid, String.format(java.util.Locale.ROOT, "%.1f", sessionMinutes), minimumSessionMinutes);
            return true;
        }
        
        return false; // Not yet reached minimum
    }
    
    /**
     * End a session and clean up tracking data
     * Returns the session duration in minutes, or -1 if no session was being tracked
     */
    public double endSession(UUID playerUuid, int minimumSessionMinutes) {
        checkAndCountSession(playerUuid, minimumSessionMinutes);
        Long sessionStart = sessionStartTimes.remove(playerUuid);
        sessionCounted.remove(playerUuid); // Clean up counted flag
        
        if (sessionStart == null) {
            LogonCheck.LOGGER.debug("No session to end for player {}", playerUuid);
            return -1;
        }
        
        long now = clock.millis();
        long sessionDuration = now - sessionStart;
        double sessionMinutes = sessionDuration / (60.0 * 1000.0); // milliseconds to minutes
        
        return sessionMinutes;
    }
    
    /**
     * Update the last login time for a player to now
     * This is used for direct updates (e.g., when system is disabled)
     */
    public void updateLastLogin(UUID playerUuid) {
        long now = clock.millis();
        lastLoginTimes.put(playerUuid, now);
        dirty = true;
        saveData();
        LogonCheck.LOGGER.debug("Updated last login time for player {}", playerUuid);
    }
    
    /**
     * Get the last login time for a player
     * Returns null if player has never logged in
     */
    public Long getLastLogin(UUID playerUuid) {
        return lastLoginTimes.get(playerUuid);
    }
    
    /**
     * Check if a player has been inactive for longer than the specified hours
     * Returns true if player should be considered inactive (or has never logged in)
     */
    public boolean isInactive(UUID playerUuid, int inactivityHours) {
        Long lastLogin = lastLoginTimes.get(playerUuid);
        
        // If never logged in before, not inactive (first time player)
        if (lastLogin == null) {
            return false;
        }
        
        long now = clock.millis();
        long inactivityMillis = (long) inactivityHours * 60 * 60 * 1000; // hours to milliseconds
        long timeSinceLastLogin = now - lastLogin;
        
        return timeSinceLastLogin > inactivityMillis;
    }
    
    /**
     * Get time since last login in hours
     * Returns -1 if player has never logged in
     */
    public double getHoursSinceLastLogin(UUID playerUuid) {
        Long lastLogin = lastLoginTimes.get(playerUuid);
        if (lastLogin == null) {
            return -1;
        }
        
        long now = clock.millis();
        long timeSinceLastLogin = now - lastLogin;
        return timeSinceLastLogin / (60.0 * 60.0 * 1000.0); // milliseconds to hours
    }
    
    /**
     * Load player activity data from file
     */
    private void loadData() {
        Path primary = dataFile.toPath();
        List<Path> backups;
        try {
            backups = listBackups();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot inspect activity backups; startup stopped without resetting history", e);
        }
        if (!Files.exists(primary) && backups.isEmpty()) {
            LogonCheck.LOGGER.info("No existing player activity data found, starting fresh");
            return;
        }
        try {
            lastLoginTimes.putAll(readHistory(primary));
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            Map<UUID, Long> restored = null;
            Path recoverySource = null;
            for (Path backup : backups) {
                try {
                    restored = readHistory(backup);
                    recoverySource = backup;
                    break;
                } catch (IOException | JsonParseException | IllegalArgumentException invalidBackup) {
                    e.addSuppressed(invalidBackup);
                }
            }
            if (restored == null) {
                throw new IllegalStateException("Cannot load activity history " + primary
                    + " and no valid backup exists in " + backupDirectory
                    + "; startup stopped without resetting history. Repair or restore these files.", e);
            }
            try {
                if (Files.exists(primary)) {
                    Path damaged = Files.createTempFile(primary.toAbsolutePath().getParent(),
                        "logon-check-data.corrupt-", ".json");
                    Files.copy(primary, damaged, StandardCopyOption.REPLACE_EXISTING);
                    LogonCheck.LOGGER.error("Damaged activity history preserved at {}", damaged);
                }
                writeHistory(primary, restored);
                lastLoginTimes.putAll(restored);
                LogonCheck.LOGGER.warn("Recovered player activity history from {}", recoverySource);
            } catch (IOException recoveryFailure) {
                throw new IllegalStateException("Cannot preserve/restore activity history; startup stopped without resetting history",
                    recoveryFailure);
            }
        }
        LogonCheck.LOGGER.info("Loaded {} player activity records", lastLoginTimes.size());
    }

    private List<Path> listBackups() throws IOException {
        if (!Files.exists(backupDirectory)) {
            return List.of();
        }
        try (var paths = Files.list(backupDirectory)) {
            return paths.filter(path -> path.getFileName().toString().matches("activity-[0-9]{19}-[0-9]+\\.json"))
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed()).toList();
        }
    }

    private Map<UUID, Long> readHistory(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject object = GSON.fromJson(reader, JsonObject.class);
            if (object == null) {
                throw new JsonParseException("Activity history must be a JSON object: " + path);
            }
            Map<UUID, Long> records = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                if (!uuid.toString().equalsIgnoreCase(entry.getKey()) || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                    throw new JsonParseException("Invalid activity record in " + path);
                }
                long timestamp;
                try {
                    timestamp = entry.getValue().getAsBigDecimal().longValueExact();
                } catch (ArithmeticException e) {
                    throw new JsonParseException("Invalid activity timestamp in " + path, e);
                }
                if (timestamp < 0) {
                    throw new JsonParseException("Negative activity timestamp in " + path);
                }
                records.put(uuid, timestamp);
            }
            return records;
        }
    }

    /** Called on the server thread each tick; does I/O only when the wall-clock interval is due. */
    public void backupIfDue() {
        long now = clock.millis();
        if (now < nextBackupAt) {
            return;
        }
        // Failed writes retry at the next interval, never every tick.
        nextBackupAt = now + BACKUP_INTERVAL_MILLIS;
        saveData();
        if (dirty) {
            LogonCheck.LOGGER.error("Activity backup skipped because the primary save failed");
            return;
        }
        try {
            Files.createDirectories(backupDirectory);
            Path backup = backupDirectory.resolve(String.format(java.util.Locale.ROOT,
                "activity-%019d-%s.json", now, Long.toUnsignedString(java.util.concurrent.ThreadLocalRandom.current().nextLong())));
            writeHistory(backup, lastLoginTimes);
            // Verify the completed file before removing any older recovery option.
            readHistory(backup);
            List<Path> backups = listBackups();
            for (int i = 10; i < backups.size(); i++) {
                Files.delete(backups.get(i));
            }
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            LogonCheck.LOGGER.error("Failed to create/prune activity backup; existing recovery files retained where possible", e);
        }
    }

    private void saveData() {
        if (!dirty) {
            return;
        }
        try {
            writeHistory(dataFile.toPath(), lastLoginTimes);
            dirty = false;
        } catch (IOException e) {
            LogonCheck.LOGGER.error("Failed to save player activity data; will retry on the next check", e);
        }
    }

    private void writeHistory(Path target, Map<UUID, Long> records) throws IOException {
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), "logon-check-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(records, writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Clear data for a specific player (e.g., after ban)
     */
    public void clearPlayerData(UUID playerUuid) {
        lastLoginTimes.remove(playerUuid);
        dirty = true;
        saveData();
        LogonCheck.LOGGER.info("Cleared activity data for player {}", playerUuid);
    }
}
