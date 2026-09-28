package com.juacac.milestones.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juacac.milestones.network.MilestoneSnapshotPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class TemplateModClient implements ClientModInitializer {
	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("worldmilestones", "main"));
	private static Screen currentScreen;
	private static final KeyMapping OPEN_MILESTONES = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.worldmilestones.open",
			GLFW.GLFW_KEY_O,
			KEY_CATEGORY
	));

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(MilestoneSnapshotPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					JsonObject snapshot = JsonParser.parseString(payload.json()).getAsJsonObject();
					WorldMilestonesScreen.setSnapshot(snapshot);
					if (snapshot.has("open") && snapshot.get("open").getAsBoolean()) {
						context.client().setScreenAndShow(new WorldMilestonesScreen(currentScreen));
					}
				}));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_MILESTONES.consumeClick()) {
				if (currentScreen instanceof WorldMilestonesScreen) {
					client.setScreenAndShow(null);
				} else {
					requestOpen(client);
				}
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			currentScreen = screen;
			ScreenEvents.remove(screen).register(removed -> {
				if (currentScreen == removed) {
					currentScreen = null;
				}
			});
			if (screen instanceof InventoryScreen) {
				Screens.getWidgets(screen).add(Button.builder(Component.translatable("gui.worldmilestones.open"), button ->
						requestOpen(client)).bounds(8, 8, 132, 20).build());
			}
		});
	}

	private static void requestOpen(Minecraft client) {
		if (client.player != null) {
			client.player.connection.sendCommand("wm open");
		}
	}
}