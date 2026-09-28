package com.juacac.milestones.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.annotations.SerializedName;
import com.juacac.milestones.TemplateMod;
import com.juacac.milestones.category.CategoryDefinition;
import com.juacac.milestones.quest.QuestDefinition;
import com.juacac.milestones.tracking.AutomaticProgressTracker;
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
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
			Path categoriesFile = ROOT.resolve("categories.json");
			Path questsPath = ROOT.resolve("quests");
			Files.createDirectories(questsPath);
			copyDefault("defaults/categories.json", categoriesFile);
			copyDefault("defaults/quests/exploration/example.json", questsPath.resolve("exploration/example.json"));
			copyDefault("defaults/quests/semanal/weekly_miner.json", questsPath.resolve("semanal/weekly_miner.json"));
			copyDefault("defaults/quests/world/spawn.json", questsPath.resolve("world/spawn.json"));
			copyDefault("defaults/quests/projects/nether_hub.json", questsPath.resolve("projects/nether_hub.json"));
			Path settingsPath = ROOT.resolve("config.json");
			copyDefault("defaults/config.json", settingsPath);
			GlobalSettings settings = GSON.fromJson(Files.readString(settingsPath), GlobalSettings.class);
			syncOnJoin = settings == null || settings.syncOnJoin;
			weeklyEnabled = settings == null || settings.weeklyEnabled;
			weeklyCount = settings == null ? 3 : Math.max(0, settings.weeklyCount);
			categories = loadCategories(categoriesFile);
			for (CategoryDefinition category : categories.values()) {
				if (category.rotationInterval != null && !category.rotationInterval.isBlank() && category.rotationIntervalMillis() <= 0) {
					TemplateMod.LOGGER.warn("Invalid rotation_interval '{}' for category {}", category.rotationInterval, category.id);
				}
			}
			quests = loadQuests(questsPath);
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
		return quests.values().stream().filter(quest -> quest.weekly && !isCategoryRotating(categories.get(quest.category)))
				.sorted(Comparator.comparing(quest -> quest.id)).toList();
	}

	public static void updateWeeklyRotation(MinecraftServer server) {
		boolean changed = false;
		Set<String> activeRotatingQuestIds = new HashSet<>();
		Set<String> timedCategoryQuestIds = new HashSet<>();
		long now = System.currentTimeMillis();
		for (CategoryDefinition category : categories.values()) {
			long intervalMillis = category.rotationIntervalMillis();
			if (intervalMillis <= 0) {
				continue;
			}
			List<String> pool = quests.values().stream().filter(quest -> category.id.equals(quest.category))
					.map(quest -> quest.id).sorted().toList();
			timedCategoryQuestIds.addAll(pool);
			changed |= TemplateMod.progress().rotateCategoryQuests(category.id, now, intervalMillis, pool, category.activeQuestCount);
			activeRotatingQuestIds.addAll(TemplateMod.progress().activeCategoryQuests(category.id));
		}
		if (weeklyEnabled) {
			LocalDate date = LocalDate.now(ZoneOffset.UTC);
			String weekKey = date.get(IsoFields.WEEK_BASED_YEAR) + "-W" + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
			changed |= TemplateMod.progress().rotateWeeklyQuests(weekKey, weeklyPool().stream().map(quest -> quest.id).toList(), weeklyCount,
					timedCategoryQuestIds);
			activeRotatingQuestIds.addAll(TemplateMod.progress().activeWeeklyQuests());
		}
		if (changed) {
			AutomaticProgressTracker.captureActivationBaselines(server, activeRotatingQuestIds);
			server.getPlayerList().getPlayers().forEach(player -> TemplateMod.sendSnapshot(player, false));
		}
	}

	public static boolean isQuestActive(QuestDefinition quest) {
		CategoryDefinition category = categories.get(quest.category);
		if (isCategoryRotating(category)) {
			return TemplateMod.progress().activeCategoryQuests(category.id).contains(quest.id);
		}
		return !quest.weekly || (weeklyEnabled && TemplateMod.progress().activeWeeklyQuests().contains(quest.id));
	}

	public static boolean isRotatingQuest(QuestDefinition quest) {
		return quest.weekly || isCategoryRotating(categories.get(quest.category));
	}

	public static long nextRotationAt(String categoryId) {
		return TemplateMod.progress().nextCategoryRotationAt(categoryId);
	}

	private static boolean isCategoryRotating(CategoryDefinition category) {
		return category != null && category.rotationIntervalMillis() > 0;
	}

	private static Map<String, CategoryDefinition> loadCategories(Path categoriesFile) throws IOException {
		Map<String, CategoryDefinition> loaded = new LinkedHashMap<>();
		JsonArray definitions = GSON.fromJson(Files.readString(categoriesFile), JsonArray.class);
		if (definitions == null) {
			throw new IllegalArgumentException("categories.json must contain an array");
		}
		for (var element : definitions) {
			try {
				CategoryDefinition category = GSON.fromJson(element, CategoryDefinition.class);
				putDefinition(loaded, category.id, category, "category", categoriesFile);
			} catch (RuntimeException exception) {
				TemplateMod.LOGGER.error("Skipping invalid category in {}", categoriesFile, exception);
			}
		}
		return Map.copyOf(loaded);
	}

	private static Map<String, QuestDefinition> loadQuests(Path directory) throws IOException {
		Map<String, QuestDefinition> questsById = new LinkedHashMap<>();
		try (var paths = Files.walk(directory, 2)) {
			paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.filter(path -> directory.relativize(path).getNameCount() == 2)
					.sorted(Comparator.comparing(path -> path.toString()))
					.forEach(path -> {
						try {
							QuestDefinition quest = GSON.fromJson(Files.readString(path), QuestDefinition.class);
							Path relativePath = directory.relativize(path);
							String categoryDirectory = relativePath.getName(0).toString();
							if (!categoryDirectory.equals(quest.category)) {
								throw new IllegalArgumentException("Quest category must match directory " + categoryDirectory);
							}
							putDefinition(questsById, quest.id, quest, "quest", path);
						} catch (Exception exception) {
							TemplateMod.LOGGER.error("Skipping invalid quest file {}", path, exception);
						}
					});
		}
		return Map.copyOf(questsById);
	}

	private static <T> void putDefinition(Map<String, T> loaded, String id, T definition, String kind, Path path) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("Missing " + kind + " id in " + path);
		}
		if (loaded.putIfAbsent(id, definition) != null) {
			TemplateMod.LOGGER.error("Skipping duplicate {} id '{}' in {}", kind, id, path);
		}
	}

	private static void copyDefault(String resource, Path target) throws IOException {
		if (Files.exists(target)) {
			return;
		}
		Files.createDirectories(target.getParent());
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