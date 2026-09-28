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
					.then(Commands.literal("reload").requires(source -> hasPermissionLevel(source, 2)).executes(context -> reload(context.getSource())))
					.then(Commands.literal("open").executes(context -> open(context.getSource())))
					.then(Commands.literal("info")
							.then(Commands.argument("quest", StringArgumentType.greedyString())
									.executes(context -> info(context.getSource(), StringArgumentType.getString(context, "quest")))))
					.then(Commands.literal("complete").requires(source -> hasPermissionLevel(source, 2))
							.then(Commands.argument("quest", StringArgumentType.greedyString())
									.executes(context -> complete(context.getSource(), StringArgumentType.getString(context, "quest")))))
					.then(Commands.literal("reset").requires(source -> hasPermissionLevel(source, 2))
							.then(Commands.argument("quest", StringArgumentType.greedyString())
									.executes(context -> reset(context.getSource(), StringArgumentType.getString(context, "quest")))))
					.then(Commands.literal("progress").requires(source -> hasPermissionLevel(source, 2))
							.then(Commands.argument("quest", StringArgumentType.word())
									.then(Commands.argument("amount", LongArgumentType.longArg(0))
										.executes(context -> progress(context.getSource(), StringArgumentType.getString(context, "quest"), LongArgumentType.getLong(context, "amount"))))))
					.then(Commands.literal("section").requires(source -> hasPermissionLevel(source, 2))
							.then(Commands.argument("quest", StringArgumentType.word())
									.then(Commands.argument("section", StringArgumentType.word())
											.executes(context -> completeSection(context.getSource(), StringArgumentType.getString(context, "quest"), StringArgumentType.getString(context, "section"))))));
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
		source.getServer().getPlayerList().getPlayers().forEach(player -> TemplateMod.sendSnapshot(player, false));
		source.sendSuccess(() -> Component.literal("Loaded " + ConfigManager.categories().size() + " categories and " + ConfigManager.quests().size() + " quests."), true);
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
		grantRewards(quest, player);
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
			grantRewards(quest, player);
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
			grantRewards(section.rewards, quest.id + "/" + section.id, player);
		}
		source.sendSuccess(() -> Component.literal("Completed section: " + section.name), true);
		if (meetsSectionMode(quest, store, player) && store.markCompleted(quest, player)) {
			store.setProgress(quest, player, quest.requiredProgress);
			grantRewards(quest, player);
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
					grantRewards(section.rewards, quest.id + "/" + section.id, player);
				}
			}
		}
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

	private static boolean visibleTo(QuestDefinition quest, CommandSourceStack source) {
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

	private static void grantRewards(java.util.List<com.google.gson.JsonObject> rewards, String sourceId, ServerPlayer player) {
		for (var rewardData : rewards) {
			try {
				RewardTypeRegistry.create(rewardData).ifPresent(reward -> reward.grant(player, player.getServer()));
			} catch (RuntimeException exception) {
				TemplateMod.LOGGER.error("Could not grant reward for {}", sourceId, exception);
			}
		}
	}
}