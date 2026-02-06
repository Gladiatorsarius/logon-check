package logon.check.swordsmp.mixin;

import logon.check.swordsmp.LogonCheckGameRules;
import logon.check.swordsmp.PlayerActivityManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class ServerTickMixin {
    
    @Shadow
    public abstract Iterable<ServerLevel> getAllLevels();
    
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 1200; // Check every 60 seconds (20 ticks/sec * 60 sec)
    
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void onServerTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        tickCounter++;
        
        // Only check every 60 seconds to avoid performance issues
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            checkPlayerSessions();
        }
    }
    
    private void checkPlayerSessions() {
        PlayerActivityManager activityManager = PlayerActivityManager.getInstance();
        
        // Iterate through all server levels to find players
        for (ServerLevel level : getAllLevels()) {
            // Get game rule from this level
            int minimumSessionMinutes = level.getGameRules().get(LogonCheckGameRules.MINIMUM_SESSION_MINUTES);
            
            // Check each online player's session
            List<ServerPlayer> players = level.players();
            for (ServerPlayer player : players) {
                activityManager.checkAndCountSession(player.getUUID(), minimumSessionMinutes);
            }
            
            // Only need to check one level since game rules are server-wide
            break;
        }
    }
}
