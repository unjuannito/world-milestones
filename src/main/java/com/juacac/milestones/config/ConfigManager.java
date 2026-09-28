package com.juacac.milestones.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.juacac.milestones.TemplateMod;
import com.juacac.milestones.category.CategoryDefinition;
import com.juacac.milestones.quest.QuestDefinition;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("worldmilestones");
	private static Map<String, CategoryDefinition> categories = Map.of();
	private static Map<String, QuestDefinition> quests = Map.of();
	private static boolean syncOnJoin = true;
	private static boolean weeklyEnabled = true;
	private static int weeklyCount = 3;

	private ConfigManager() {
	}

	public static synchronized void reload() {
		try {
			Path categoriesPath = ROOT.resolve("categories");
			Path questsPath = ROOT.resolve("quests");
			Files.createDirectories(categoriesPath);
			Files.createDirectories(questsPath);
			for (String category : new String[]{"world", "quests", "projects", "challenges", "achievements", "polls"}) {
				copyDefault("defaults/categories/" + category + ".json", categoriesPath.resolve(category + ".json"));
			}
			copyDefault("defaults/quests/example.json", questsPath.resolve("example.json"));
			copyDefault("defaults/quests/spawn.json", questsPath.resolve("spawn.json"));
			copyDefault("defaults/quests/nether_hub.json", questsPath.resolve("nether_hub.json"));
			copyDefault("defaults/quests/weekly_miner.json", questsPath.resolve("weekly_miner.json"));
			Path settingsPath = ROOT.resolve("config.json");
			copyDefault("defaults/config.json", settingsPath);
			GlobalSettings settings = GSON.fromJson(Files.readString(settingsPath), GlobalSettings.class);
			syncOnJoin = settings == null || settings.syncOnJoin;
			weeklyEnabled = settings == null || settings.weeklyEnabled;
			weeklyCount = settings == null ? 3 : Math.max(0, settings.weeklyCount);
			categories = loadDirectory(categoriesPath, CategoryDefinition.class, "category");
			quests = loadDirectory(questsPath, QuestDefinition.class, "quest");
			TemplateMod.LOGGER.info("Loaded {} categories and {} quests", categories.size(), quests.size());
		} catch (IOException | RuntimeException exception) {
			TemplateMod.LOGGER.error("Could not load World Milestones configuration", exception);
		}
	}

	public static Map<String, CategoryDefinition> categories() {
		return categories;
	}

	public static Map<String, QuestDefinition> quests() {
		return quests;
	}

	public static QuestDefinition quest(String id) {
		return quests.get(id);
	}

	public static boolean syncOnJoin() {
		return syncOnJoin;
	}

	public static boolean weeklyEnabled() {
		return weeklyEnabled;
	}

	public static int weeklyCount() {
		return weeklyCount;
	}

	public static List<QuestDefinition> weeklyPool() {
		return quests.values().stream().filter(quest -> quest.weekly).sorted(Comparator.comparing(quest -> quest.id)).toList();
	}

	public static void updateWeeklyRotation(MinecraftServer server) {
		if (!weeklyEnabled) {
			return;
		}
		LocalDate date = LocalDate.now(ZoneOffset.UTC);
		String weekKey = date.get(IsoFields.WEEK_BASED_YEAR) + "-W" + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
		boolean changed = TemplateMod.progress().rotateWeeklyQuests(weekKey, weeklyPool().stream().map(quest -> quest.id).toList(), weeklyCount);
		if (changed) {
			server.getPlayerList().getPlayers().forEach(player -> TemplateMod.sendSnapshot(player, false));
		}
	}

	private static <T> Map<String, T> loadDirectory(Path directory, Class<T> type, String kind) throws IOException {
		Map<String, T> loaded = new LinkedHashMap<>();
		try (var paths = Files.list(directory)) {
			paths.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted(Comparator.comparing(path -> path.getFileName().toString()))
					.forEach(path -> {
						try {
							T definition = GSON.fromJson(Files.readString(path), type);
							String id = definition instanceof CategoryDefinition category ? category.id
									: ((QuestDefinition) definition).id;
							if (id == null || id.isBlank()) {
								throw new IllegalArgumentException("Missing id");
							}
							if (loaded.putIfAbsent(id, definition) != null) {
								throw new IllegalArgumentException("Duplicate id: " + id);
							}
						} catch (Exception exception) {
							TemplateMod.LOGGER.error("Skipping invalid {} file {}", kind, path, exception);
						}
					});
		}
		return Map.copyOf(loaded);
	}

	private static void copyDefault(String resource, Path target) throws IOException {
		if (Files.exists(target)) {
			return;
		}
		try (InputStream input = ConfigManager.class.getClassLoader().getResourceAsStream(resource)) {
			if (input != null) {
				Files.copy(input, target);
			} else {
				TemplateMod.LOGGER.warn("Default configuration resource is missing: {}", resource);
			}
		}
	}

	private static final class GlobalSettings {
		@SerializedName("sync_on_join")
		private boolean syncOnJoin = true;
		@SerializedName("weekly_enabled")
		private boolean weeklyEnabled = true;
		@SerializedName("weekly_count")
		private int weeklyCount = 3;
	}
}