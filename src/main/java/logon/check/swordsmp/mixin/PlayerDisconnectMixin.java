package logon.check.swordsmp.mixin;

import logon.check.swordsmp.LogonCheck;
import logon.check.swordsmp.LogonCheckGameRules;
import logon.check.swordsmp.PlayerActivityManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerGamePacketListenerImpl.class)
public class PlayerDisconnectMixin {
    
    @Shadow
    public ServerPlayer player;
    
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void onPlayerDisconnect(Component reason, CallbackInfo ci) {
        UUID playerUuid = this.player.getUUID();
        String playerName = this.player.getName().getString();
        
        // Get the server level to access game rules
        ServerLevel serverLevel = (ServerLevel) player.level();
        
        // Get configured minimum session time in minutes
        int minimumSessionMinutes = serverLevel.getGameRules().get(LogonCheckGameRules.MINIMUM_SESSION_MINUTES);
        
        // End session and get duration
        PlayerActivityManager activityManager = PlayerActivityManager.getInstance();
        double sessionMinutes = activityManager.endSession(playerUuid);
        
        if (sessionMinutes >= 0) {
            if (sessionMinutes >= minimumSessionMinutes) {
                LogonCheck.LOGGER.info("Player {} disconnected after {:.1f} minutes - session duration met requirement",
                    playerName, sessionMinutes);
            } else {
                LogonCheck.LOGGER.info("Player {} disconnected after {:.1f} minutes - session too short (minimum: {} min)",
                    playerName, sessionMinutes, minimumSessionMinutes);
            }
        }
    }
}
