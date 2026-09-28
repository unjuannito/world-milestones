package com.juacac.milestones.command;

import com.juacac.milestones.TemplateMod;
import com.juacac.milestones.category.CategoryDefinition;
import com.juacac.milestones.config.ConfigManager;
import com.juacac.milestones.quest.QuestDefinition;
import com.juacac.milestones.reward.RewardTypeRegistry;
import com.juacac.milestones.storage.ProgressStore;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;

public final class WorldMilestonesCommand {
	private WorldMilestonesCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(root("wm"));
		dispatcher.register(root("worldmilestones"));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
		return Commands.literal(name)
				.executes(context -> list(context.getSource()))
					.then(Commands.literal("list").executes(context -> list(context.getSource())))
					.then(Commands.literal("reload").requires(WorldMilestonesCommand::hasAdminPermission).executes(context -> reload(context.getSource())))
					.then(Commands.literal("rotation").requires(WorldMilestonesCommand::hasAdminPermission)
							.then(Commands.literal("reduce")
									.then(Commands.argument("category", StringArgumentType.word())
											.then(Commands.argument("duration", StringArgumentType.word())
												.executes(context -> reduceRotation(context.getSource(),
													StringArgumentType.getString(context, "category"),
													StringArgumentType.getString(context, "duration")))))))
					.then(Commands.literal("open").executes(context -> open(context.getSource())))
					.then(Commands.literal("info")
							.then(Commands.argument("quest", StringArgumentType.greedyString())
									.executes(context -> info(context.getSource(), StringArgumentType.getString(context, "quest")))))
					.then(Commands.literal("complete").requires(WorldMilestonesCommand::hasAdminPermission)
							.then(Commands.argument("quest", StringArgumentType.greedyString())
									.executes(context -> complete(context.getSource(), StringArgumentType.getString(context, "quest")))))
					.then(Commands.literal("reset").requires(WorldMilestonesCommand::hasAdminPermission)
							.then(Commands.argument("quest", StringArgumentType.greedyString())
									.executes(context -> reset(context.getSource(), StringArgumentType.getString(context, "quest")))))
					.then(Commands.literal("progress").requires(WorldMilestonesCommand::hasAdminPermission)
							.then(Commands.argument("quest", StringArgumentType.word())
									.then(Commands.argument("amount", LongArgumentType.longArg(0))
										.executes(context -> progress(context.getSource(), StringArgumentType.getString(context, "quest"), LongArgumentType.getLong(context, "amount"))))))
					.then(Commands.literal("section").requires(WorldMilestonesCommand::hasAdminPermission)
							.then(Commands.argument("quest", StringArgumentType.word())
									.then(Commands.argument("section", StringArgumentType.word())
									.executes(context -> completeSection(context.getSource(), StringArgumentType.getString(context, "quest"), StringArgumentType.getString(context, "section"))))))
					.then(Commands.literal("admin").requires(source -> hasPermissionLevel(source, 2))
							.then(Commands.literal("add")
									.then(Commands.argument("player", EntityArgument.player())
											.executes(context -> addAdmin(context.getSource(), EntityArgument.getPlayer(context, "player")))))
							.then(Commands.literal("remove")
									.then(Commands.argument("player", EntityArgument.player())
											.executes(context -> removeAdmin(context.getSource(), EntityArgument.getPlayer(context, "player")))))
							.then(Commands.literal("list").executes(context -> listAdmins(context.getSource()))));
	}

	private static int addAdmin(CommandSourceStack source, ServerPlayer player) {
		if (!TemplateMod.progress().addMilestonesAdmin(player.getUUID())) {
			source.sendFailure(Component.literal(player.getGameProfile().name() + " is already a milestones-admin."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Added " + player.getGameProfile().name() + " as a milestones-admin."), true);
		return 1;
	}

	private static int removeAdmin(CommandSourceStack source, ServerPlayer player) {
		if (!TemplateMod.progress().removeMilestonesAdmin(player.getUUID())) {
			source.sendFailure(Component.literal(player.getGameProfile().name() + " is not a milestones-admin."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Removed " + player.getGameProfile().name() + " as a milestones-admin."), true);
		return 1;
	}

	private static int listAdmins(CommandSourceStack source) {
		var admins = TemplateMod.progress().milestonesAdmins();
		if (admins.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No milestones-admins are configured."), false);
			return 0;
		}
		for (var id : admins) {
			ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayer(id);
			String name = onlinePlayer == null ? id.toString() : onlinePlayer.getGameProfile().name();
			source.sendSuccess(() -> Component.literal(name + " [" + id + "]"), false);
		}
		return admins.size();
	}

	private static int list(CommandSourceStack source) {
		var categories = ConfigManager.categories().values().stream()
				.filter(category -> category.visible && hasPermissionLevel(source, category.requiredPermissionLevel))
				.sorted(Comparator.comparingInt(category -> category.order))
				.toList();
		source.sendSuccess(() -> Component.literal("World Milestones"), false);
		for (CategoryDefinition category : categories) {
			source.sendSuccess(() -> Component.literal("§e" + category.name + " §7(" + category.id + ")"), false);
		}
		ConfigManager.quests().values().stream()
				.filter(quest -> visibleTo(quest, source))
				.sorted(Comparator.comparingInt(quest -> quest.order)).forEach(quest ->
				source.sendSuccess(() -> Component.literal("§f- " + quest.name + " §7(" + quest.id + ")"), false));
		return ConfigManager.quests().size();
	}

	private static int reload(CommandSourceStack source) {
		ConfigManager.reload();
		ConfigManager.updateWeeklyRotation(source.getServer());
		source.getServer().getPlayerList().getPlayers().forEach(player -> TemplateMod.sendSnapshot(player, false));
		source.sendSuccess(() -> Component.literal("Loaded " + ConfigManager.categories().size() + " categories and " + ConfigManager.quests().size() + " quests."), true);
		return 1;
	}

	private static int reduceRotation(CommandSourceStack source, String categoryId, String duration) {
		CategoryDefinition category = ConfigManager.categories().get(categoryId);
		long reductionMillis = CategoryDefinition.parseDurationMillis(duration);
		if (category == null || category.rotationIntervalMillis() <= 0) {
			source.sendFailure(Component.literal("Category is not configured for timed rotation: " + categoryId));
			return 0;
		}
		if (reductionMillis <= 0) {
			source.sendFailure(Component.literal("Invalid duration. Use a positive value such as 30m, 2h, or 1d."));
			return 0;
		}
		long nextRotationAt = TemplateMod.progress().reduceCategoryRotation(categoryId, reductionMillis, System.currentTimeMillis());
		if (nextRotationAt < 0) {
			source.sendFailure(Component.literal("The category rotation has not been initialized yet."));
			return 0;
		}
		ConfigManager.updateWeeklyRotation(source.getServer());
		source.getServer().getPlayerList().getPlayers().forEach(player -> TemplateMod.sendSnapshot(player, false));
		if (nextRotationAt <= System.currentTimeMillis()) {
			source.sendSuccess(() -> Component.literal("Rotation advanced for " + category.name + "; quests have been refreshed."), true);
		} else {
			long remainingSeconds = Math.max(0, (nextRotationAt - System.currentTimeMillis() + 999) / 1000);
			source.sendSuccess(() -> Component.literal("Reduced " + category.name + " rotation by " + duration
					+ "; " + remainingSeconds + " seconds remain."), true);
		}
		return 1;
	}

	private static int open(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		TemplateMod.sendSnapshot(player, true);
		return 1;
	}

	private static int info(CommandSourceStack source, String id) throws CommandSyntaxException {
		QuestDefinition quest = findQuest(source, id);
		if (quest == null) {
			return 0;
		}
		source.sendSuccess(() -> Component.literal("§e" + quest.name + " §7[" + quest.id + "]"), false);
		source.sendSuccess(() -> Component.literal(quest.description), false);
		source.sendSuccess(() -> Component.literal("Progress scope: " + quest.progressScope + " | Required: " + quest.requiredProgress), false);
		if (source.getEntity() instanceof ServerPlayer player) {
			long progress = TemplateMod.progress().getProgress(quest, player);
			source.sendSuccess(() -> Component.literal("Progress: " + Math.min(progress, quest.requiredProgress) + "/" + quest.requiredProgress
					+ (TemplateMod.progress().isCompleted(quest, player) ? " (completed)" : "")), false);
		}
		for (QuestDefinition.Section section : quest.sections) {
			long sectionProgress = source.getEntity() instanceof ServerPlayer player
					? TemplateMod.progress().getSectionProgress(quest, section, player) : 0;
			source.sendSuccess(() -> Component.literal("§7- " + section.name + " " + Math.min(sectionProgress, section.requiredProgress)
					+ "/" + section.requiredProgress + (section.required ? " *" : " (optional)")), false);
		}
		return 1;
	}

	private static int complete(CommandSourceStack source, String id) throws CommandSyntaxException {
		QuestDefinition quest = findQuest(source, id);
		if (quest == null) {
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		ProgressStore progress = TemplateMod.progress();
		if (!progress.markCompleted(quest, player)) {
			source.sendFailure(Component.literal("That quest has already been completed."));
			return 0;
		}
		progress.setProgress(quest, player, quest.requiredProgress);
		completeRequiredSections(quest, player, progress);
		grantRewardsForScope(quest, quest.rewards, quest.id, player);
		source.sendSuccess(() -> Component.literal("Completed: " + quest.name), true);
		TemplateMod.syncProgress(quest, player);
		return 1;
	}

	private static int reset(CommandSourceStack source, String id) throws CommandSyntaxException {
		QuestDefinition quest = findQuest(source, id);
		if (quest == null) {
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		TemplateMod.progress().reset(quest, player);
		source.sendSuccess(() -> Component.literal("Reset progress: " + quest.name), true);
		TemplateMod.syncProgress(quest, player);
		return 1;
	}

	private static int progress(CommandSourceStack source, String id, long amount) throws CommandSyntaxException {
		QuestDefinition quest = findQuest(source, id);
		if (quest == null) {
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		ProgressStore store = TemplateMod.progress();
		store.setProgress(quest, player, amount);
		if (amount >= quest.requiredProgress && store.markCompleted(quest, player)) {
			completeRequiredSections(quest, player, store);
			grantRewardsForScope(quest, quest.rewards, quest.id, player);
			source.sendSuccess(() -> Component.literal("Completed: " + quest.name), true);
		} else {
			source.sendSuccess(() -> Component.literal("Set " + quest.id + " progress to " + amount + "/" + quest.requiredProgress), true);
		}
		TemplateMod.syncProgress(quest, player);
		return 1;
	}

	private static int completeSection(CommandSourceStack source, String questId, String sectionId) throws CommandSyntaxException {
		QuestDefinition quest = findQuest(source, questId);
		if (quest == null) {
			return 0;
		}
		QuestDefinition.Section section = quest.sections.stream().filter(value -> value.id.equals(sectionId)).findFirst().orElse(null);
		if (section == null) {
			source.sendFailure(Component.literal("Unknown section: " + sectionId));
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		ProgressStore store = TemplateMod.progress();
		if (store.getSectionProgress(quest, section, player) >= section.requiredProgress) {
			source.sendFailure(Component.literal("That section is already complete."));
			return 0;
		}
		store.setSectionProgress(quest, section, player, section.requiredProgress);
		if (store.markSectionRewarded(quest, section, player)) {
			grantRewardsForScope(quest, section.rewards, quest.id + "/" + section.id, player);
		}
		source.sendSuccess(() -> Component.literal("Completed section: " + section.name), true);
		if (meetsSectionMode(quest, store, player) && store.markCompleted(quest, player)) {
			store.setProgress(quest, player, quest.requiredProgress);
			grantRewardsForScope(quest, quest.rewards, quest.id, player);
			source.sendSuccess(() -> Component.literal("Completed: " + quest.name), true);
		}
		TemplateMod.syncProgress(quest, player);
		return 1;
	}

	private static boolean meetsSectionMode(QuestDefinition quest, ProgressStore store, ServerPlayer player) {
		long completed = quest.sections.stream()
				.filter(section -> store.getSectionProgress(quest, section, player) >= section.requiredProgress)
				.count();
		return switch (quest.completionMode) {
			case ALL -> !quest.sections.stream().filter(section -> section.required).toList().isEmpty()
					&& quest.sections.stream().filter(section -> section.required)
						.allMatch(section -> store.getSectionProgress(quest, section, player) >= section.requiredProgress);
			case ANY -> {
				var candidates = quest.sections.stream().filter(section -> section.required).toList();
				if (candidates.isEmpty()) {
					candidates = quest.sections;
				}
				var eligibleSections = candidates;
				yield eligibleSections.stream().anyMatch(section -> store.getSectionProgress(quest, section, player) >= section.requiredProgress);
			}
			case X_OF_Y -> completed >= Math.max(1, quest.requiredSections);
		};
	}

	private static void completeRequiredSections(QuestDefinition quest, ServerPlayer player, ProgressStore store) {
		for (QuestDefinition.Section section : quest.sections) {
			if (section.required) {
				store.setSectionProgress(quest, section, player, section.requiredProgress);
				if (store.markSectionRewarded(quest, section, player)) {
					grantRewardsForScope(quest, section.rewards, quest.id + "/" + section.id, player);
				}
			}
		}
	}

	public static void applyAutomaticProgress(QuestDefinition quest, ServerPlayer player, long amount) {
		ProgressStore store = TemplateMod.progress();
		if (store.isCompleted(quest, player)) {
			return;
		}
		long updated = Math.max(store.getProgress(quest, player), Math.max(0, amount));
		if (updated == store.getProgress(quest, player)) {
			return;
		}
		store.setProgress(quest, player, updated);
		if (updated >= quest.requiredProgress && store.markCompleted(quest, player)) {
			completeRequiredSections(quest, player, store);
			grantRewardsForScope(quest, quest.rewards, quest.id, player);
			player.sendSystemMessage(Component.literal("Milestone completed: " + quest.name));
		}
		TemplateMod.syncProgress(quest, player);
	}

	public static void deliverPendingGlobalRewards(ServerPlayer player) {
		ProgressStore store = TemplateMod.progress();
		for (QuestDefinition quest : ConfigManager.quests().values()) {
			if (quest.progressScope != QuestDefinition.ProgressScope.GLOBAL) {
				continue;
			}
			if (store.isCompleted(quest, player) && !store.hasGlobalRewardDefinition(quest.id)) {
				store.registerGlobalReward(quest.id, quest.rewards, false);
			}
			for (QuestDefinition.Section section : quest.sections) {
				String sourceId = quest.id + "/" + section.id;
				if (store.isSectionRewarded(quest, section, player) && !store.hasGlobalRewardDefinition(sourceId)) {
					store.registerGlobalReward(sourceId, section.rewards, false);
				}
			}
		}
		deliverStoredGlobalRewards(player);
	}

	private static void deliverStoredGlobalRewards(ServerPlayer player) {
		ProgressStore store = TemplateMod.progress();
		store.pendingGlobalRewards(player.getUUID()).forEach((key, rewards) -> {
			grantRewards(rewards, key, player);
			store.markGlobalRewardClaimed(player.getUUID(), key);
		});
	}

	private static QuestDefinition findQuest(CommandSourceStack source, String id) {
		QuestDefinition quest = ConfigManager.quest(id);
		if (quest == null || !visibleTo(quest, source)) {
			source.sendFailure(Component.literal("Unknown quest: " + id));
		}
		return quest;
	}

	private static void grantRewards(QuestDefinition quest, ServerPlayer player) {
		grantRewards(quest.rewards, quest.id, player);
	}

	private static void grantRewardsForScope(QuestDefinition quest, java.util.List<com.google.gson.JsonObject> rewards, String sourceId, ServerPlayer actor) {
		if (quest.progressScope == QuestDefinition.ProgressScope.GLOBAL) {
			ProgressStore store = TemplateMod.progress();
			store.registerGlobalReward(sourceId, rewards, quest.repeatable);
			for (ServerPlayer player : actor.level().getServer().getPlayerList().getPlayers()) {
				deliverStoredGlobalRewards(player);
			}
		} else {
			grantRewards(rewards, sourceId, actor);
		}
	}

	private static boolean visibleTo(QuestDefinition quest, CommandSourceStack source) {
		if (!ConfigManager.isQuestActive(quest)) {
			return false;
		}
		if ((quest.hidden || "operator".equalsIgnoreCase(quest.visibility)) && !hasPermissionLevel(source, 2)) {
			return false;
		}
		CategoryDefinition category = ConfigManager.categories().get(quest.category);
		return category != null && category.visible && hasPermissionLevel(source, category.requiredPermissionLevel);
	}

	private static boolean hasPermissionLevel(CommandSourceStack source, int permissionLevel) {
		if (permissionLevel <= 0 || source.getEntity() == null) {
			return true;
		}
		return source.getEntity() instanceof ServerPlayer player && TemplateMod.hasPermissionLevel(player, permissionLevel);
	}

	private static boolean hasAdminPermission(CommandSourceStack source) {
		if (source.getEntity() == null) {
			return true;
		}
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return false;
		}
		return TemplateMod.hasPermissionLevel(player, 2) || TemplateMod.progress().isMilestonesAdmin(player.getUUID());
	}

	private static void grantRewards(java.util.List<com.google.gson.JsonObject> rewards, String sourceId, ServerPlayer player) {
		for (var rewardData : rewards) {
			try {
				RewardTypeRegistry.create(rewardData).ifPresent(reward -> reward.grant(player, player.level().getServer()));
			} catch (RuntimeException exception) {
				TemplateMod.LOGGER.error("Could not grant reward for {}", sourceId, exception);
			}
		}
	}
}