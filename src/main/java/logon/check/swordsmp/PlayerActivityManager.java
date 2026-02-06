package logon.check.swordsmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player login activity data
 * Tracks last login timestamps for all players
 */
public class PlayerActivityManager {
    private static final PlayerActivityManager INSTANCE = new PlayerActivityManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Map<UUID, Long> lastLoginTimes = new ConcurrentHashMap<>();
    private File dataFile;
    
    private PlayerActivityManager() {}
    
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
        loadData();
        LogonCheck.LOGGER.info("Initialized player activity manager");
    }
    
    /**
     * Update the last login time for a player to now
     */
    public void updateLastLogin(UUID playerUuid) {
        long now = Instant.now().toEpochMilli();
        lastLoginTimes.put(playerUuid, now);
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
        
        long now = Instant.now().toEpochMilli();
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
        
        long now = Instant.now().toEpochMilli();
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
        // Convert UUID keys to strings for JSON serialization
        Map<String, Long> saveData = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, Long> entry : lastLoginTimes.entrySet()) {
            saveData.put(entry.getKey().toString(), entry.getValue());
        }
        
        try (FileWriter writer = new FileWriter(dataFile)) {
            GSON.toJson(saveData, writer);
        } catch (IOException e) {
            LogonCheck.LOGGER.error("Failed to save player activity data", e);
        }
    }
    
    /**
     * Clear data for a specific player (e.g., after ban)
     */
    public void clearPlayerData(UUID playerUuid) {
        lastLoginTimes.remove(playerUuid);
        saveData();
        LogonCheck.LOGGER.info("Cleared activity data for player {}", playerUuid);
    }
}
