package com.juacac.milestones;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.juacac.milestones.category.CategoryDefinition;
import com.juacac.milestones.command.WorldMilestonesCommand;
import com.juacac.milestones.config.ConfigManager;
import com.juacac.milestones.network.MilestoneSnapshotPayload;
import com.juacac.milestones.quest.QuestDefinition;
import com.juacac.milestones.storage.ProgressStore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

public class TemplateMod implements ModInitializer {
	public static final String MOD_ID = "template-mod";
	private static final Gson GSON = new GsonBuilder().create();
	private static ProgressStore progressStore;

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("World Milestones initialized");
		ConfigManager.reload();
		PayloadTypeRegistry.clientboundPlay().register(MilestoneSnapshotPayload.TYPE, MilestoneSnapshotPayload.CODEC);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> WorldMilestonesCommand.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> progressStore = ProgressStore.load(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (progressStore != null) {
				progressStore.save();
			}
			progressStore = null;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ConfigManager.syncOnJoin()) {
				sendSnapshot(handler.player, false);
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static ProgressStore progress() {
		if (progressStore == null) {
			throw new IllegalStateException("World Milestones progress is not loaded yet");
		}
		return progressStore;
	}

	public static boolean hasPermissionLevel(ServerPlayer player, int permissionLevel) {
		return permissionLevel <= 0 || player.serverLevel().getServer().getPlayerList().isOp(player.getGameProfile());
	}

	public static void sendSnapshot(ServerPlayer player, boolean openScreen) {
		JsonObject snapshot = new JsonObject();
		snapshot.addProperty("open", openScreen);
		JsonArray categories = new JsonArray();
		for (CategoryDefinition category : ConfigManager.categories().values().stream().sorted(Comparator.comparingInt(value -> value.order)).toList()) {
			if (category.visible && hasPermissionLevel(player, category.requiredPermissionLevel)) {
				categories.add(GSON.toJsonTree(category));
			}
		}
		snapshot.add("categories", categories);
		JsonArray quests = new JsonArray();
		JsonObject progress = new JsonObject();
		JsonObject sectionProgress = new JsonObject();
		JsonArray completed = new JsonArray();
		for (QuestDefinition quest : ConfigManager.quests().values().stream().sorted(Comparator.comparingInt(value -> value.order)).toList()) {
			boolean operatorOnly = quest.hidden || "operator".equalsIgnoreCase(quest.visibility);
			CategoryDefinition category = ConfigManager.categories().get(quest.category);
			if ((operatorOnly && !hasPermissionLevel(player, 2)) || category == null
					|| !category.visible || !hasPermissionLevel(player, category.requiredPermissionLevel)) {
				continue;
			}
			quests.add(GSON.toJsonTree(quest));
			progress.addProperty(quest.id, progress().getProgress(quest, player));
			JsonObject questSections = new JsonObject();
			for (QuestDefinition.Section section : quest.sections) {
				questSections.addProperty(section.id, progress().getSectionProgress(quest, section, player));
			}
			sectionProgress.add(quest.id, questSections);
			if (progress().isCompleted(quest, player)) {
				completed.add(quest.id);
			}
		}
		snapshot.add("quests", quests);
		snapshot.add("progress", progress);
		snapshot.add("section_progress", sectionProgress);
		snapshot.add("completed", completed);
		ServerPlayNetworking.send(player, new MilestoneSnapshotPayload(GSON.toJson(snapshot)));
	}

	public static void syncProgress(QuestDefinition quest, ServerPlayer actor) {
		var server = actor.serverLevel().getServer();
		var actorTeam = actor.getTeam();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean affected = switch (quest.progressScope) {
				case GLOBAL -> true;
				case PERSONAL -> player.getUUID().equals(actor.getUUID());
				case TEAM -> actorTeam == null ? player.getUUID().equals(actor.getUUID())
						: player.getTeam() != null && actorTeam.getName().equals(player.getTeam().getName());
			};
			if (affected) {
				sendSnapshot(player, false);
			}
		}
	}
}
