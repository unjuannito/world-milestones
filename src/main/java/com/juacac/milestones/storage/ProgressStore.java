package com.juacac.milestones.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.juacac.milestones.quest.QuestDefinition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProgressStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path file;
	private final Map<UUID, PlayerProgress> players = new HashMap<>();
	private final WorldProgress world = new WorldProgress();

	private ProgressStore(Path file) {
		this.file = file;
	}

	public static ProgressStore load(MinecraftServer server) {
		Path file = server.getWorldPath(LevelResource.ROOT).resolve("data/worldmilestones-progress.json");
		ProgressStore store = new ProgressStore(file);
		if (Files.exists(file)) {
			try {
				StoredData stored = GSON.fromJson(Files.readString(file), StoredData.class);
				if (stored != null) {
					if (stored.world != null) {
						store.copyWorld(stored.world);
					}
					if (stored.players != null) {
						stored.players.forEach((uuid, progress) -> {
							try {
								store.players.put(UUID.fromString(uuid), progress);
							} catch (IllegalArgumentException ignored) {
							}
						});
					}
				}
			} catch (IOException | RuntimeException exception) {
				com.juacac.milestones.TemplateMod.LOGGER.error("Could not load World Milestones progress from {}", file, exception);
			}
		}
		return store;
	}

	public long getProgress(QuestDefinition quest, ServerPlayer player) {
		return switch (quest.progressScope) {
			case GLOBAL -> world.globalProgress.getOrDefault(quest.id, 0L);
			case TEAM -> world.teamProgress.getOrDefault(teamKey(quest, player), 0L);
			case PERSONAL -> playerProgress(player).progress.getOrDefault(quest.id, 0L);
		};
	}

	public void setProgress(QuestDefinition quest, ServerPlayer player, long amount) {
		long boundedAmount = Math.max(0, amount);
		switch (quest.progressScope) {
			case GLOBAL -> world.globalProgress.put(quest.id, boundedAmount);
			case TEAM -> world.teamProgress.put(teamKey(quest, player), boundedAmount);
			case PERSONAL -> playerProgress(player).progress.put(quest.id, boundedAmount);
		}
		save();
	}

	public long getSectionProgress(QuestDefinition quest, QuestDefinition.Section section, ServerPlayer player) {
		String key = sectionKey(quest, section);
		return switch (quest.progressScope) {
			case GLOBAL -> world.globalSectionProgress.getOrDefault(key, 0L);
			case TEAM -> world.teamSectionProgress.getOrDefault(teamKey(quest, player) + "#" + section.id, 0L);
			case PERSONAL -> playerProgress(player).sectionProgress.getOrDefault(key, 0L);
		};
	}

	public void setSectionProgress(QuestDefinition quest, QuestDefinition.Section section, ServerPlayer player, long amount) {
		String key = sectionKey(quest, section);
		long boundedAmount = Math.max(0, amount);
		switch (quest.progressScope) {
			case GLOBAL -> world.globalSectionProgress.put(key, boundedAmount);
			case TEAM -> world.teamSectionProgress.put(teamKey(quest, player) + "#" + section.id, boundedAmount);
			case PERSONAL -> playerProgress(player).sectionProgress.put(key, boundedAmount);
		}
		save();
	}

	public boolean markSectionRewarded(QuestDefinition quest, QuestDefinition.Section section, ServerPlayer player) {
		String key = sectionKey(quest, section);
		boolean added = switch (quest.progressScope) {
			case GLOBAL -> world.rewardedGlobalSections.add(key);
			case TEAM -> world.rewardedTeamSections.add(teamKey(quest, player) + "#" + section.id);
			case PERSONAL -> playerProgress(player).rewardedSections.add(key);
		};
		if (added) {
			save();
		}
		return added;
	}

	public boolean isCompleted(QuestDefinition quest, ServerPlayer player) {
		return switch (quest.progressScope) {
			case GLOBAL -> world.completedGlobal.contains(quest.id);
			case TEAM -> world.completedTeams.contains(teamKey(quest, player));
			case PERSONAL -> playerProgress(player).completed.contains(quest.id);
		};
	}

	public boolean markCompleted(QuestDefinition quest, ServerPlayer player) {
		if (quest.repeatable) {
			return true;
		}
		boolean added = switch (quest.progressScope) {
			case GLOBAL -> world.completedGlobal.add(quest.id);
			case TEAM -> world.completedTeams.add(teamKey(quest, player));
			case PERSONAL -> playerProgress(player).completed.add(quest.id);
		};
		if (added) {
			save();
		}
		return added;
	}

	public void reset(QuestDefinition quest, ServerPlayer player) {
		for (QuestDefinition.Section section : quest.sections) {
			String key = sectionKey(quest, section);
			switch (quest.progressScope) {
				case GLOBAL -> {
					world.globalSectionProgress.remove(key);
					world.rewardedGlobalSections.remove(key);
				}
				case TEAM -> {
					String teamSectionKey = teamKey(quest, player) + "#" + section.id;
					world.teamSectionProgress.remove(teamSectionKey);
					world.rewardedTeamSections.remove(teamSectionKey);
				}
				case PERSONAL -> {
					playerProgress(player).sectionProgress.remove(key);
					playerProgress(player).rewardedSections.remove(key);
				}
			}
		}
		switch (quest.progressScope) {
			case GLOBAL -> {
				world.globalProgress.remove(quest.id);
				world.completedGlobal.remove(quest.id);
			}
			case TEAM -> {
				String teamKey = teamKey(quest, player);
				world.teamProgress.remove(teamKey);
				world.completedTeams.remove(teamKey);
			}
			case PERSONAL -> {
				playerProgress(player).progress.remove(quest.id);
				playerProgress(player).completed.remove(quest.id);
			}
		}
		save();
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			StoredData stored = new StoredData();
			stored.world = world;
			players.forEach((uuid, progress) -> stored.players.put(uuid.toString(), progress));
			Files.writeString(file, GSON.toJson(stored));
		} catch (IOException exception) {
			com.juacac.milestones.TemplateMod.LOGGER.error("Could not save World Milestones progress to {}", file, exception);
		}
	}

	private PlayerProgress playerProgress(ServerPlayer player) {
		return players.computeIfAbsent(player.getUUID(), ignored -> new PlayerProgress());
	}

	private String teamKey(QuestDefinition quest, ServerPlayer player) {
		var team = player.getTeam();
		return quest.id + "@" + (team == null ? "player:" + player.getUUID() : team.getName());
	}

	private String sectionKey(QuestDefinition quest, QuestDefinition.Section section) {
		return quest.id + "#" + section.id;
	}

	private void copyWorld(WorldProgress source) {
		world.globalProgress.putAll(source.globalProgress);
		world.teamProgress.putAll(source.teamProgress);
		world.globalSectionProgress.putAll(source.globalSectionProgress);
		world.teamSectionProgress.putAll(source.teamSectionProgress);
		world.completedGlobal.addAll(source.completedGlobal);
		world.completedTeams.addAll(source.completedTeams);
		world.rewardedGlobalSections.addAll(source.rewardedGlobalSections);
		world.rewardedTeamSections.addAll(source.rewardedTeamSections);
	}

	private static final class StoredData {
		private WorldProgress world = new WorldProgress();
		private Map<String, PlayerProgress> players = new HashMap<>();
	}
}