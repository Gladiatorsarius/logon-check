package logon.check.swordsmp.mixin;

import logon.check.swordsmp.LogonCheck;
import logon.check.swordsmp.PlayerActivityManager;
import net.minecraft.network.chat.Component;
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
        
        // Update last login time when player disconnects
        PlayerActivityManager.getInstance().updateLastLogin(playerUuid);
        
        LogonCheck.LOGGER.debug("Updated last login time for player {} on disconnect", playerName);
    }
}
