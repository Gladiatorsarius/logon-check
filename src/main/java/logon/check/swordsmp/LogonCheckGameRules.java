package logon.check.swordsmp;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Custom game rules for logon check system
 */
public class LogonCheckGameRules {
    /**
     * When true, enables the logon activity check system.
     * Players who haven't logged in within the configured time will be killed and banned on next login.
     * Default: false (disabled)
     */
    public static final GameRule<Boolean> ENABLE_LOGON_CHECK = GameRuleBuilder.forBoolean(false)
        .category(GameRuleCategory.PLAYER)
        .buildAndRegister(Identifier.fromNamespaceAndPath("logon-check", "enableLogonCheck"));
    
    /**
     * Configures the maximum inactivity time in hours before a player is considered inactive.
     * If a player hasn't logged in within this many hours, they will be killed and banned on next login.
     * Default: 168 hours (7 days)
     * Minimum: 1 hour
     * Maximum: 8760 hours (365 days / 1 year)
     */
    public static final GameRule<Integer> INACTIVITY_HOURS = GameRuleBuilder.forInteger(168)
        .category(GameRuleCategory.PLAYER)
        .buildAndRegister(Identifier.fromNamespaceAndPath("logon-check", "inactivityHours"));
    
    /**
     * Configures the minimum session time in minutes for a login to count as activity.
     * Players must stay online for at least this many minutes for the session to reset their inactivity timer.
     * Sessions shorter than this will not count toward activity.
     * Default: 30 minutes
     * Minimum: 1 minute
     * Maximum: 1440 minutes (24 hours)
     */
    public static final GameRule<Integer> MINIMUM_SESSION_MINUTES = GameRuleBuilder.forInteger(30)
        .category(GameRuleCategory.PLAYER)
        .buildAndRegister(Identifier.fromNamespaceAndPath("logon-check", "minimumSessionMinutes"));
    
    /**
     * Initialize game rules (called during mod initialization)
     */
    public static void initialize() {
        // Game rules are registered statically above
        LogonCheck.LOGGER.info("Registered logon check game rules");
    }
}
