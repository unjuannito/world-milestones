package com.juacac.milestones.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.juacac.milestones.TemplateMod;
import com.juacac.milestones.category.CategoryDefinition;
import com.juacac.milestones.quest.QuestDefinition;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("worldmilestones");
	private static Map<String, CategoryDefinition> categories = Map.of();
	private static Map<String, QuestDefinition> quests = Map.of();
	private static boolean syncOnJoin = true;

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
			Path settingsPath = ROOT.resolve("config.json");
			copyDefault("defaults/config.json", settingsPath);
			GlobalSettings settings = GSON.fromJson(Files.readString(settingsPath), GlobalSettings.class);
			syncOnJoin = settings == null || settings.syncOnJoin;
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
		private boolean syncOnJoin = true;
	}
}