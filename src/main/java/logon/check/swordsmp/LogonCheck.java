package logon.check.swordsmp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class LogonCheck implements ModInitializer {
	public static final String MOD_ID = "logon-check";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Logon Check mod initialized!");
		LOGGER.info("Player activity tracking is now active");
		
		// Initialize game rules
		LogonCheckGameRules.initialize();
		
		// Register server start event to initialize player activity manager
		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStart);
	}
	
	private void onServerStart(MinecraftServer server) {
		// Initialize player activity manager
		File configDir = new File("config");
		PlayerActivityManager.getInstance().initialize(configDir);
		LOGGER.info("Initialized player activity tracking system");
	}
}