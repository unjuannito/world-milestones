package com.juacac.milestones.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class WorldMilestonesScreen extends Screen {
	private static JsonObject snapshot = new JsonObject();
	private final Screen parent;
	private String selectedCategory;
	private String selectedQuest;

	public WorldMilestonesScreen(Screen parent) {
		super(Component.translatable("gui.worldmilestones.title"));
		this.parent = parent;
	}

	public static void setSnapshot(JsonObject newSnapshot) {
		snapshot = newSnapshot;
	}

	@Override
	protected void init() {
		if (selectedCategory == null) {
			addCategoryButtons();
		} else {
			addRenderableWidget(Button.builder(Component.translatable("gui.worldmilestones.back"), button -> {
				selectedCategory = null;
				selectedQuest = null;
				clearWidgets();
				init();
			}).bounds(12, 12, 100, 20).build());
			addQuestButtons();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		extractBackground(graphics, mouseX, mouseY, delta);
		graphics.fill(0, 0, width, height, 0xA9101718);
		graphics.centeredText(font, title, width / 2, 18, 0xFFFFFF);
		if (selectedCategory == null) {
			drawCategoriesHeader(graphics);
		} else {
			drawQuestDetails(graphics);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	private void addCategoryButtons() {
		JsonArray categories = array("categories");
		int maxRows = Math.max(1, (height - 72) / 28);
		for (int index = 0; index < categories.size() && index < maxRows; index++) {
			JsonObject category = categories.get(index).getAsJsonObject();
			String id = string(category, "id", "");
			String name = string(category, "name", id);
			int y = 46 + index * 28;
			addRenderableWidget(Button.builder(Component.literal(name), button -> {
				selectedCategory = id;
				init();
			}).bounds(width / 2 - 110, y, 220, 22).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(width / 2 - 50, height - 30, 100, 20).build());
	}

	private void addQuestButtons() {
		JsonArray quests = array("quests");
		int maxRows = Math.max(1, (height - 80) / 26);
		int row = 0;
		for (JsonElement element : quests) {
			JsonObject quest = element.getAsJsonObject();
			if (!selectedCategory.equals(string(quest, "category", ""))) {
				continue;
			}
			if (row >= maxRows) {
				break;
			}
			String id = string(quest, "id", "");
			String label = string(quest, "name", id);
			long current = snapshot.has("progress") && snapshot.getAsJsonObject("progress").has(id)
					? snapshot.getAsJsonObject("progress").get(id).getAsLong() : 0;
			long required = Math.max(1, number(quest, "required_progress", 1));
			label += "  " + Math.min(current, required) + "/" + required;
			int y = 48 + row++ * 26;
			addRenderableWidget(Button.builder(Component.literal(label), button -> {
				selectedQuest = id;
				init();
			}).bounds(12, y, Math.max(120, width / 3 - 20), 22).build());
		}
	}

	private void drawCategoriesHeader(GuiGraphicsExtractor graphics) {
		graphics.centeredText(font, Component.translatable("gui.worldmilestones.categories"), width / 2, 32, 0xA8D8A8);
	}

	private void drawQuestDetails(GuiGraphicsExtractor graphics) {
		JsonObject quest = findQuest(selectedQuest);
		if (quest == null) {
			graphics.text(font, Component.translatable("gui.worldmilestones.empty"), width / 2, height / 2, 0xFFFFFF);
			return;
		}
		int left = Math.max(width / 3 + 18, 150);
		int right = width - 18;
		int textWidth = Math.max(100, right - left);
		graphics.fill(left - 8, 42, right, height - 14, 0xB8202928);
		graphics.text(font, string(quest, "icon", "minecraft:book") + "  " + string(quest, "name", selectedQuest), left, 52, 0xFFFFFF);
		int y = drawWrapped(graphics, string(quest, "description", ""), left, 72, textWidth, 0xD0D8D0);
		long current = snapshot.has("progress") && snapshot.getAsJsonObject("progress").has(selectedQuest)
				? snapshot.getAsJsonObject("progress").get(selectedQuest).getAsLong() : 0;
		long required = Math.max(1, number(quest, "required_progress", 1));
		int barWidth = Math.max(80, textWidth - 48);
		int filled = (int) (barWidth * Math.min(current, required) / required);
		graphics.fill(left, y + 4, left + barWidth, y + 12, 0xFF394541);
		graphics.fill(left, y + 4, left + filled, y + 12, 0xFF67C587);
		graphics.text(font, Math.min(current, required) + "/" + required, left + barWidth + 8, y + 2, 0xFFFFFF);
		y += 23;
		boolean completed = array("completed").asList().stream().anyMatch(element -> selectedQuest.equals(element.getAsString()));
		graphics.text(font, Component.translatable(completed ? "gui.worldmilestones.completed" : "gui.worldmilestones.in_progress"), left, y, completed ? 0x7FE09A : 0xE5D28A);
		y += 16;
		graphics.text(font, Component.translatable("gui.worldmilestones.sections"), left, y, 0xA8D8A8);
		y += 13;
		if (quest.has("sections") && quest.get("sections").isJsonArray()) {
			for (JsonElement element : quest.getAsJsonArray("sections")) {
				if (y > height - 90) {
					break;
				}
				JsonObject section = element.getAsJsonObject();
				String sectionId = string(section, "id", "");
				long sectionCurrent = 0;
				if (snapshot.has("section_progress") && snapshot.getAsJsonObject("section_progress").has(selectedQuest)) {
					JsonObject progressData = snapshot.getAsJsonObject("section_progress").getAsJsonObject(selectedQuest);
					if (progressData.has(sectionId)) {
						sectionCurrent = progressData.get(sectionId).getAsLong();
					}
				}
				long sectionRequired = Math.max(1, number(section, "required_progress", 1));
				boolean sectionComplete = sectionCurrent >= sectionRequired;
				String marker = sectionComplete ? "[x] " : "[ ] ";
				graphics.text(font, marker + string(section, "name", sectionId) + " "
						+ Math.min(sectionCurrent, sectionRequired) + "/" + sectionRequired, left, y, 0xE4E8E4);
				y += 12;
			}
		}
		y += 7;
		y = drawJsonList(graphics, quest, "requirements", "gui.worldmilestones.requirements", left, y, textWidth, 0xD7C98D);
		drawJsonList(graphics, quest, "rewards", "gui.worldmilestones.rewards", left, y, textWidth, 0x88D99A);
	}

	private int drawJsonList(GuiGraphicsExtractor graphics, JsonObject quest, String field, String titleKey, int x, int y, int maxWidth, int color) {
		if (!quest.has(field) || !quest.get(field).isJsonArray() || quest.getAsJsonArray(field).isEmpty()) {
			return y;
		}
		graphics.text(font, Component.translatable(titleKey), x, y, 0xA8D8A8);
		y += 13;
		for (JsonElement element : quest.getAsJsonArray(field)) {
			if (y > height - 42) {
				break;
			}
			JsonObject data = element.getAsJsonObject();
			String label = data.has("type") ? data.get("type").getAsString() : element.toString();
			y = drawWrapped(graphics, "- " + label, x, y, maxWidth, color);
		}
		return y + 5;
	}

	private int drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
		for (var line : font.split(Component.literal(text), maxWidth)) {
			graphics.text(font, line, x, y, color);
			y += 10;
		}
		return y + 3;
	}

	private JsonObject findQuest(String id) {
		for (JsonElement element : array("quests")) {
			JsonObject quest = element.getAsJsonObject();
			if (id.equals(string(quest, "id", ""))) {
				return quest;
			}
		}
		return null;
	}

	private static JsonArray array(String field) {
		return snapshot.has(field) && snapshot.get(field).isJsonArray() ? snapshot.getAsJsonArray(field) : new JsonArray();
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
	}

	private static long number(JsonObject object, String key, long fallback) {
		return object.has(key) ? object.get(key).getAsLong() : fallback;
	}

	private static boolean bool(JsonObject object, String key, boolean fallback) {
		return object.has(key) ? object.get(key).getAsBoolean() : fallback;
	}
}