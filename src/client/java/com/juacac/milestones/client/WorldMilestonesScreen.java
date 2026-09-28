package com.juacac.milestones.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class WorldMilestonesScreen extends Screen {
	private static JsonObject snapshot = new JsonObject();
	private final Screen parent;
	private String selectedCategory;
	private String selectedQuest;

	public WorldMilestonesScreen(Screen parent) {
		super(CommonComponents.EMPTY);
		this.parent = parent;
	}

	public static void setSnapshot(JsonObject newSnapshot) {
		snapshot = newSnapshot;
	}

	@Override
	protected void init() {
		JsonArray categories = array("categories");
		if (selectedCategory == null && !categories.isEmpty()) {
			selectedCategory = string(categories.get(0).getAsJsonObject(), "id", "");
		}
		addCategoryTabs(categories);
		addQuestButtons();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		JsonArray categories = array("categories");
		graphics.fill(0, 0, width, height, 0xA9101718);
		float scale = uiScale();
		int marginX = Math.max(4, Math.round(12 * scale));
		int tabSize = Math.max(28, Math.round(42 * scale));
		int tabY = Math.max(4, Math.round(6 * scale));
		int panelLeft = marginX;
		int panelRight = width - marginX;
		int panelTop = tabY + tabSize - Math.max(4, Math.round(6 * scale));
		int panelBottom = height - marginX;
		int border = Math.max(2, Math.round(3 * scale));
		graphics.fill(panelLeft + 2, panelTop + 3, panelRight + 2, panelBottom + 3, 0xFF101010);
		graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xFF202020);
		graphics.fill(panelLeft + border, panelTop + border, panelRight - border, panelBottom - border, 0xFFB8B8B8);
		graphics.fill(panelLeft + border + 2, panelTop + border + 2, panelRight - border - 2, panelBottom - border - 2, 0xFFE1E1E1);

		int contentLeft = panelLeft + Math.round(12 * scale);
		int contentRight = panelRight - Math.round(12 * scale);
		int headerY = panelTop + Math.round(7 * scale);
		JsonObject category = findCategory(selectedCategory);
		if (category != null) {
			graphics.text(font, string(category, "name", selectedCategory), contentLeft, headerY, 0xFF333333);
		}

		int contentTop = panelTop + Math.round(34 * scale);
		int contentBottom = panelBottom - Math.round(10 * scale);
		graphics.fill(contentLeft, contentTop, contentRight, contentBottom, 0xFF514638);
		int separatorX = contentLeft + Math.max(Math.round(110 * scale), (contentRight - contentLeft) / 3);
		graphics.fill(separatorX, contentTop, separatorX + Math.max(2, Math.round(3 * scale)), contentBottom, 0xFF252525);
		if (selectedQuest == null) {
			graphics.centeredText(font, Component.translatable("gui.worldmilestones.empty"),
					(separatorX + contentRight) / 2, (contentTop + contentBottom) / 2, 0xFFE1E1E1);
		} else {
			drawQuestDetails(graphics, separatorX + Math.round(12 * scale), contentRight,
					contentTop + Math.round(12 * scale), contentBottom, scale);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawCategoryTabIcons(graphics, categories, panelLeft, tabY, tabSize, scale);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	private void addCategoryTabs(JsonArray categories) {
		float scale = uiScale();
		int marginX = Math.max(4, Math.round(12 * scale));
		int tabSize = Math.max(28, Math.round(42 * scale));
		int tabY = Math.max(4, Math.round(6 * scale));
		int gap = Math.max(2, Math.round(4 * scale));
		int x = marginX + Math.round(8 * scale);
		for (int index = 0; index < categories.size(); index++) {
			JsonObject category = categories.get(index).getAsJsonObject();
			String id = string(category, "id", "");
			String name = string(category, "name", id);
			int tabX = x + index * (tabSize + gap);
			addRenderableWidget(Button.builder(CommonComponents.EMPTY, button -> {
				selectedCategory = id;
				selectedQuest = null;
				clearWidgets();
				init();
			}).bounds(tabX, tabY, tabSize, tabSize).tooltip(Tooltip.create(Component.literal(name))).build());
		}
	}

	private void addQuestButtons() {
		if (selectedCategory == null) {
			return;
		}
		float scale = uiScale();
		int marginX = Math.max(4, Math.round(12 * scale));
		int panelTop = Math.max(4, Math.round(6 * scale)) + Math.max(28, Math.round(42 * scale)) - Math.max(4, Math.round(6 * scale));
		int panelBottom = height - marginX;
		int contentLeft = marginX + Math.round(12 * scale);
		int contentRight = width - marginX - Math.round(12 * scale);
		int contentTop = panelTop + Math.round(34 * scale);
		int contentBottom = panelBottom - Math.round(10 * scale);
		int separatorX = contentLeft + Math.max(Math.round(110 * scale), (contentRight - contentLeft) / 3);
		int listWidth = separatorX - contentLeft - Math.round(8 * scale);
		int rowHeight = Math.max(18, Math.round(25 * scale));
		int buttonHeight = Math.max(16, Math.round(21 * scale));
		int maxRows = Math.max(1, (contentBottom - contentTop - Math.round(8 * scale)) / rowHeight);
		JsonArray quests = array("quests");
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
			String name = string(quest, "name", id);
			long current = snapshot.has("progress") && snapshot.getAsJsonObject("progress").has(id)
					? snapshot.getAsJsonObject("progress").get(id).getAsLong() : 0;
			long required = Math.max(1, number(quest, "required_progress", 1));
			String label = name + "  " + Math.min(current, required) + "/" + required;
			int y = contentTop + Math.round(5 * scale) + row++ * rowHeight;
			if (selectedQuest == null) {
				selectedQuest = id;
			}
			addRenderableWidget(Button.builder(Component.literal(label), button -> {
				selectedQuest = id;
				clearWidgets();
				init();
			}).bounds(contentLeft + Math.round(4 * scale), y, Math.max(buttonHeight * 3, listWidth - Math.round(8 * scale)), buttonHeight).build());
		}
	}

	private void drawCategoryTabIcons(GuiGraphicsExtractor graphics, JsonArray categories, int panelLeft, int tabY, int tabSize, float scale) {
		int gap = Math.max(2, Math.round(4 * scale));
		int x = panelLeft + Math.round(8 * scale);
		for (int index = 0; index < categories.size(); index++) {
			JsonObject category = categories.get(index).getAsJsonObject();
			int tabX = x + index * (tabSize + gap);
			if (selectedCategory != null && selectedCategory.equals(string(category, "id", ""))) {
				graphics.fill(tabX, tabY, tabX + tabSize, tabY + 2, 0xFFFFD866);
				graphics.fill(tabX, tabY, tabX + 2, tabY + tabSize, 0xFFFFD866);
				graphics.fill(tabX + tabSize - 2, tabY, tabX + tabSize, tabY + tabSize, 0xFFFFD866);
			}
			ItemStack icon = itemStack(string(category, "icon", "minecraft:book"));
			graphics.item(icon, tabX + (tabSize - 16) / 2, tabY + (tabSize - 16) / 2);
		}
	}

	private void drawQuestDetails(GuiGraphicsExtractor graphics, int left, int right, int top, int bottom, float scale) {
		JsonObject quest = findQuest(selectedQuest);
		if (quest == null) {
			graphics.centeredText(font, Component.translatable("gui.worldmilestones.empty"), (left + right) / 2, (top + bottom) / 2, 0xFFFFFFFF);
			return;
		}
		int textWidth = Math.max(80, right - left);
		graphics.item(itemStack(string(quest, "icon", "minecraft:book")), left, top);
		graphics.text(font, string(quest, "name", selectedQuest), left + 22, top + 4, 0xFFFFFFFF);
		int y = drawWrapped(graphics, string(quest, "description", ""), left, top + 23, textWidth, 0xFFE2E2E2, scale);
		long current = snapshot.has("progress") && snapshot.getAsJsonObject("progress").has(selectedQuest)
				? snapshot.getAsJsonObject("progress").get(selectedQuest).getAsLong() : 0;
		long required = Math.max(1, number(quest, "required_progress", 1));
		int barWidth = Math.max(50, textWidth - Math.round(52 * scale));
		int filled = (int) (barWidth * Math.min(current, required) / required);
		graphics.fill(left, y + 4, left + barWidth, y + 12, 0xFF393939);
		graphics.fill(left, y + 4, left + filled, y + 12, 0xFF4E9B58);
		graphics.text(font, Math.min(current, required) + "/" + required, left + barWidth + 5, y + 2, 0xFFFFFFFF);
		y += Math.round(23 * scale);
		boolean completed = array("completed").asList().stream().anyMatch(element -> selectedQuest.equals(element.getAsString()));
		graphics.text(font, Component.translatable(completed ? "gui.worldmilestones.completed" : "gui.worldmilestones.in_progress"), left, y, completed ? 0xFF7FE09A : 0xFFE5D28A);
		y += Math.round(16 * scale);
		graphics.text(font, Component.translatable("gui.worldmilestones.sections"), left, y, 0xFFFFD866);
		y += Math.round(13 * scale);
		if (quest.has("sections") && quest.get("sections").isJsonArray()) {
			for (JsonElement element : quest.getAsJsonArray("sections")) {
				if (y > bottom - 24) {
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
						+ Math.min(sectionCurrent, sectionRequired) + "/" + sectionRequired, left, y, 0xFFE4E8E4);
				y += Math.round(12 * scale);
			}
		}
		y += Math.round(7 * scale);
		y = drawJsonList(graphics, quest, "requirements", "gui.worldmilestones.requirements", left, y, textWidth, 0xFFD7C98D, bottom, scale);
		drawJsonList(graphics, quest, "rewards", "gui.worldmilestones.rewards", left, y, textWidth, 0xFF88D99A, bottom, scale);
	}

	private int drawJsonList(GuiGraphicsExtractor graphics, JsonObject quest, String field, String titleKey, int x, int y, int maxWidth, int color, int bottom, float scale) {
		if (!quest.has(field) || !quest.get(field).isJsonArray() || quest.getAsJsonArray(field).isEmpty()) {
			return y;
		}
		graphics.text(font, Component.translatable(titleKey), x, y, 0xA8D8A8);
		y += Math.round(13 * scale);
		for (JsonElement element : quest.getAsJsonArray(field)) {
			if (y > bottom - 12) {
				break;
			}
			JsonObject data = element.getAsJsonObject();
			String label = data.has("type") ? data.get("type").getAsString() : element.toString();
			y = drawWrapped(graphics, "- " + label, x, y, maxWidth, color, scale);
		}
		return y + Math.round(5 * scale);
	}

	private int drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color, float scale) {
		for (var line : font.split(Component.literal(text), maxWidth)) {
			graphics.text(font, line, x, y, color);
			y += Math.round(10 * scale);
		}
		return y + Math.round(3 * scale);
	}

	private JsonObject findCategory(String id) {
		if (id == null) {
			return null;
		}
		for (JsonElement element : array("categories")) {
			JsonObject category = element.getAsJsonObject();
			if (id.equals(string(category, "id", ""))) {
				return category;
			}
		}
		return null;
	}

	private ItemStack itemStack(String itemId) {
		try {
			return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId)));
		} catch (RuntimeException exception) {
			return ItemStack.EMPTY;
		}
	}

	private float uiScale() {
		return Math.max(0.75F, Math.min(1.5F, Math.min(width / 800.0F, height / 450.0F)));
	}

	private JsonObject findQuest(String id) {
		if (id == null) {
			return null;
		}
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