package logon.check.swordsmp.mixin;

import logon.check.swordsmp.LogonCheck;
import logon.check.swordsmp.LogonCheckGameRules;
import logon.check.swordsmp.PlayerActivityManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerList.class)
public abstract class PlayerLoginMixin {
    
    @Shadow
    public abstract MinecraftServer getServer();
    
    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void onPlayerLogin(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        MinecraftServer server = this.getServer();
        UUID playerUuid = player.getUUID();
        String playerName = player.getName().getString();
        
        // Get the server level to access game rules
        ServerLevel serverLevel = (ServerLevel) player.level();
        
        // Check if logon check system is enabled
        boolean enabled = Boolean.TRUE.equals(serverLevel.getGameRules().get(LogonCheckGameRules.ENABLE_LOGON_CHECK));
        
        if (!enabled) {
            // System is disabled, just update last login and return
            PlayerActivityManager.getInstance().updateLastLogin(playerUuid);
            return;
        }
        
        // Get configured inactivity threshold in hours
        int inactivityHours = serverLevel.getGameRules().get(LogonCheckGameRules.INACTIVITY_HOURS);
        
        // Check if player has been inactive
        PlayerActivityManager activityManager = PlayerActivityManager.getInstance();
        boolean isInactive = activityManager.isInactive(playerUuid, inactivityHours);
        
        if (isInactive) {
            double hoursSinceLastLogin = activityManager.getHoursSinceLastLogin(playerUuid);
            
            LogonCheck.LOGGER.warn("Player {} has been inactive for {:.1f} hours (threshold: {} hours) - enforcing punishment",
                playerName, hoursSinceLastLogin, inactivityHours);
            
            // Kill the player
            player.hurt(player.damageSources().generic(), Float.MAX_VALUE);
            
            // Ban the player using server command
            CommandSourceStack commandSource = server.createCommandSourceStack();
            String banCommand = "ban " + playerName + " Inactive for too long (" + String.format("%.1f", hoursSinceLastLogin) + " hours)";
            server.getCommands().performPrefixedCommand(commandSource, banCommand);
            
            // Clear their data
            activityManager.clearPlayerData(playerUuid);
            
            // Disconnect with message
            player.connection.disconnect(
                Component.literal("§c§lInactivity Ban\n\n" +
                    "§eYou have been inactive for too long.\n" +
                    "§eTime since last login: §c" + String.format("%.1f", hoursSinceLastLogin) + " hours\n" +
                    "§eMaximum allowed: §a" + inactivityHours + " hours\n\n" +
                    "§7You have been killed and banned from the server.")
            );
            
            LogonCheck.LOGGER.info("Successfully killed and banned player {} for inactivity", playerName);
        } else {
            // Player is active, update their login time
            activityManager.updateLastLogin(playerUuid);
            
            double hoursSinceLastLogin = activityManager.getHoursSinceLastLogin(playerUuid);
            if (hoursSinceLastLogin >= 0) {
                LogonCheck.LOGGER.info("Player {} logged in after {:.1f} hours (threshold: {} hours)",
                    playerName, hoursSinceLastLogin, inactivityHours);
            } else {
                LogonCheck.LOGGER.info("Player {} logged in for the first time", playerName);
            }
        }
    }
}
