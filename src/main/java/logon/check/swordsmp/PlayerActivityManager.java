package logon.check.swordsmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.time.Clock;
import java.util.Collection;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player login activity data
 * Tracks last login timestamps for all players and current session start times
 */
public class PlayerActivityManager {
    private static final PlayerActivityManager INSTANCE = new PlayerActivityManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Map<UUID, Long> lastLoginTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> sessionCounted = new ConcurrentHashMap<>(); // Track if session already counted
    private File dataFile;
    private final Clock clock;
    private boolean dirty;
    
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
        loadData();
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
        if (!dataFile.exists()) {
            LogonCheck.LOGGER.info("No existing player activity data found, starting fresh");
            return;
        }
        
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, Long>>(){}.getType();
            Map<String, Long> loadedData = GSON.fromJson(reader, type);
            
            if (loadedData != null) {
                // Convert String UUIDs back to UUID objects
                for (Map.Entry<String, Long> entry : loadedData.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        lastLoginTimes.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        LogonCheck.LOGGER.warn("Invalid UUID in data file: {}", entry.getKey());
                    }
                }
                LogonCheck.LOGGER.info("Loaded {} player activity records", lastLoginTimes.size());
            }
        } catch (IOException e) {
            LogonCheck.LOGGER.error("Failed to load player activity data", e);
        }
    }
    
    /**
     * Save player activity data to file
     */
    private void saveData() {
        if (!dirty) {
            return;
        }
        Path target = dataFile.toPath();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.toAbsolutePath().getParent(), "logon-check-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(lastLoginTimes, writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException e) {
            LogonCheck.LOGGER.error("Failed to save player activity data; will retry on the next check", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    LogonCheck.LOGGER.warn("Failed to remove temporary activity file {}", temporary, e);
                }
            }
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
