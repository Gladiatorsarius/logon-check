package logon.check.swordsmp.mixin;

import logon.check.swordsmp.LogonCheckGameRules;
import logon.check.swordsmp.PlayerActivityManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class ServerTickMixin {
    
    @Shadow
    public abstract net.minecraft.server.players.PlayerList getPlayerList();

    @Shadow
    public abstract net.minecraft.server.level.ServerLevel overworld();
    
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 1200; // Check every 60 seconds (20 ticks/sec * 60 sec)
    
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void onServerTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        PlayerActivityManager.getInstance().backupIfDue();
        tickCounter++;
        
        // Only check every 60 seconds to avoid performance issues
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            checkPlayerSessions();
        }
    }
    
    private void checkPlayerSessions() {
        PlayerActivityManager activityManager = PlayerActivityManager.getInstance();
        
        int minimumSessionMinutes = overworld().getGameRules().get(LogonCheckGameRules.MINIMUM_SESSION_MINUTES);
        activityManager.checkSessions(getPlayerList().getPlayers().stream().map(ServerPlayer::getUUID).toList(),
            minimumSessionMinutes);
    }
}
