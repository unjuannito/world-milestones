package com.juacac.milestones.reward;

import com.google.gson.JsonObject;
import com.juacac.milestones.TemplateMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class RewardTypeRegistry {
	private static final Map<String, Function<JsonObject, Reward>> TYPES = new HashMap<>();

	static {
		register("experience", json -> (player, server) -> player.giveExperiencePoints(json.get("amount").getAsInt()));
		register("item", json -> new ItemReward(
				Identifier.parse(json.get("item").getAsString()),
					json.has("count") ? json.get("count").getAsInt() : 1
		));
		register("command", json -> new CommandReward(json.get("command").getAsString()));
	}

	private RewardTypeRegistry() {
	}

	public static void register(String type, Function<JsonObject, Reward> factory) {
		TYPES.put(type, factory);
	}

	public static Optional<Reward> create(JsonObject json) {
		if (!json.has("type")) {
			return Optional.empty();
		}
		Function<JsonObject, Reward> factory = TYPES.get(json.get("type").getAsString());
		if (factory == null) {
			TemplateMod.LOGGER.warn("Ignoring unknown reward type: {}", json.get("type").getAsString());
			return Optional.empty();
		}
		return Optional.of(factory.apply(json));
	}

	private record ItemReward(Identifier itemId, int count) implements Reward {
		@Override
		public void grant(ServerPlayer player, MinecraftServer server) {
			var item = BuiltInRegistries.ITEM.get(itemId).orElse(null);
			if (item == null || count <= 0) {
				return;
			}
			ItemStack stack = new ItemStack(item, count);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}
	}

	private record CommandReward(String command) implements Reward {
		@Override
		public void grant(ServerPlayer player, MinecraftServer server) {
			String resolvedCommand = command.replace("%player%", player.getGameProfile().name());
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), resolvedCommand);
		}
	}
}