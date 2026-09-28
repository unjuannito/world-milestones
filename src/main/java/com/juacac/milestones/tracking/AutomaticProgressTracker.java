package com.juacac.milestones.tracking;

import com.google.gson.JsonObject;
import com.juacac.milestones.TemplateMod;
import com.juacac.milestones.command.WorldMilestonesCommand;
import com.juacac.milestones.config.ConfigManager;
import com.juacac.milestones.quest.QuestDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AutomaticProgressTracker {
	private static int tickCounter;
	private static final Map<UUID, Map<EquipmentSlot, Item>> ARMOR_SNAPSHOTS = new java.util.HashMap<>();
	private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	private AutomaticProgressTracker() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AutomaticProgressTracker::onServerTick);
	}

	public static void onJoin(ServerPlayer player) {
		ARMOR_SNAPSHOTS.put(player.getUUID(), readArmor(player));
		for (QuestDefinition quest : ConfigManager.quests().values()) {
			if (ConfigManager.isRotatingQuest(quest) && ConfigManager.isQuestActive(quest)) {
				captureActivationBaselines(player, quest);
			}
		}
		for (QuestDefinition quest : ConfigManager.quests().values()) {
			if (!ConfigManager.isQuestActive(quest)) {
				continue;
			}
			for (JsonObject trigger : quest.automatic) {
				if ("server_join".equals(trigger.get("type").getAsString())) {
					long current = TemplateMod.progress().getProgress(quest, player);
					WorldMilestonesCommand.applyAutomaticProgress(quest, player, current + 1);
					break;
				}
			}
		}
	}

	public static void captureActivationBaselines(MinecraftServer server, Set<String> questIds) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (String questId : questIds) {
				QuestDefinition quest = ConfigManager.quest(questId);
				if (quest != null && ConfigManager.isRotatingQuest(quest)) {
					captureActivationBaselines(player, quest);
				}
			}
		}
	}

	private static void captureActivationBaselines(ServerPlayer player, QuestDefinition quest) {
		for (JsonObject trigger : quest.automatic) {
			if (!trigger.has("type") || "server_join".equals(trigger.get("type").getAsString())) {
				continue;
			}
			try {
				long value = readRawStat(player, trigger);
				if (value >= 0) {
					TemplateMod.progress().setAutomaticBaseline(player.getUUID(), baselineKey(quest, trigger), value);
				}
			} catch (RuntimeException exception) {
				TemplateMod.LOGGER.warn("Could not capture automatic baseline for {}", trigger, exception);
			}
		}
	}

	private static void onServerTick(MinecraftServer server) {
		if (++tickCounter < 20) {
			return;
		}
		tickCounter = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			trackArmorChanges(player);
			for (QuestDefinition quest : ConfigManager.quests().values()) {
				if (quest.automatic.isEmpty() || !ConfigManager.isQuestActive(quest)) {
					continue;
				}
				long observed = 0;
				for (JsonObject trigger : quest.automatic) {
					observed = Math.max(observed, readStat(quest, player, trigger));
				}
				if (observed > 0) {
					WorldMilestonesCommand.applyAutomaticProgress(quest, player, observed);
				}
			}
		}
	}

	private static long readStat(QuestDefinition quest, ServerPlayer player, JsonObject trigger) {
		if (!trigger.has("type")) {
			return 0;
		}
		try {
			long value = readRawStat(player, trigger);
			if (value < 0) {
				return 0;
			}
			if (ConfigManager.isRotatingQuest(quest)) {
				String baselineKey = baselineKey(quest, trigger);
				long baseline = TemplateMod.progress().getAutomaticBaseline(player.getUUID(), baselineKey);
				if (baseline < 0) {
					TemplateMod.progress().setAutomaticBaseline(player.getUUID(), baselineKey, value);
					return 0;
				}
				return Math.max(0, value - baseline);
			}
			return value;
		} catch (RuntimeException exception) {
			TemplateMod.LOGGER.warn("Invalid automatic milestone trigger {}", trigger, exception);
			return 0;
		}
	}

	private static long readRawStat(ServerPlayer player, JsonObject trigger) {
		String type = trigger.get("type").getAsString();
		String target = trigger.has("target") ? trigger.get("target").getAsString() : "";
		Stat<?> stat = switch (type) {
				case "mob_killed" -> {
					var entityType = BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(target)).orElse(null);
					yield entityType == null ? null : Stats.ENTITY_KILLED.get(entityType.value());
				}
				case "item_crafted" -> {
					var item = BuiltInRegistries.ITEM.get(Identifier.parse(target)).orElse(null);
					yield item == null ? null : Stats.ITEM_CRAFTED.get(item.value());
				}
				case "item_smelted" -> {
					var item = BuiltInRegistries.ITEM.get(Identifier.parse(target)).orElse(null);
					yield item == null ? null : Stats.ITEM_CRAFTED.get(item.value());
				}
				case "item_used" -> {
					var item = BuiltInRegistries.ITEM.get(Identifier.parse(target)).orElse(null);
					yield item == null ? null : Stats.ITEM_USED.get(item.value());
				}
				case "block_placed" -> {
					var item = BuiltInRegistries.ITEM.get(Identifier.parse(target)).orElse(null);
					yield item == null ? null : Stats.ITEM_USED.get(item.value());
				}
				case "block_mined" -> {
					var block = BuiltInRegistries.BLOCK.get(Identifier.parse(target)).orElse(null);
					yield block == null ? null : Stats.BLOCK_MINED.get(block.value());
				}
				case "animals_bred" -> Stats.CUSTOM.get(Stats.ANIMALS_BRED);
				case "target_hit" -> Stats.CUSTOM.get(Stats.TARGET_HIT);
				case "mob_kills" -> Stats.CUSTOM.get(Stats.MOB_KILLS);
				case "armor_worn" -> null;
				default -> null;
		};
		if ("armor_worn".equals(type)) {
			return TemplateMod.progress().getAutomaticCount(player.getUUID(), type);
		}
		if (stat == null) {
			return -1;
		}
		return player.getStats().getValue(stat);
	}

	private static String baselineKey(QuestDefinition quest, JsonObject trigger) {
		return quest.id + "#" + trigger;
	}

	private static void trackArmorChanges(ServerPlayer player) {
		Map<EquipmentSlot, Item> current = readArmor(player);
		Map<EquipmentSlot, Item> previous = ARMOR_SNAPSHOTS.put(player.getUUID(), current);
		if (previous == null) {
			return;
		}
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			Item item = current.get(slot);
			if (item != Items.AIR && item != previous.get(slot)) {
				TemplateMod.progress().incrementAutomaticCount(player.getUUID(), "armor_worn");
			}
		}
	}

	private static Map<EquipmentSlot, Item> readArmor(ServerPlayer player) {
		Map<EquipmentSlot, Item> armor = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			armor.put(slot, player.getItemBySlot(slot).getItem());
		}
		return armor;
	}
}
