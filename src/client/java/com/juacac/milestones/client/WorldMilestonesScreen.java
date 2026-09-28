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
	private static long snapshotReceivedAt;
	private final Screen parent;
	private String selectedCategory;
	private String selectedQuest;

	public WorldMilestonesScreen(Screen parent) {
		super(CommonComponents.EMPTY);
		this.parent = parent;
	}

	public static void setSnapshot(JsonObject newSnapshot) {
		snapshot = newSnapshot;
		snapshotReceivedAt = System.currentTimeMillis();
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
		graphics.fill(0, 0, width, height, 0xC9101718);
		float scale = uiScale();
		int marginX = Math.max(4, Math.round(12 * scale));
		int tabSize = Math.max(28, Math.round(42 * scale));
		int tabY = Math.max(4, Math.round(6 * scale));
		int panelLeft = marginX;
		int panelRight = width - marginX;
		int panelTop = categoryPanelTop(tabY, tabSize, scale);
		int panelBottom = Math.min(height - marginX, panelTop + desiredPanelHeight(scale));
		int border = Math.max(2, Math.round(3 * scale));
		graphics.fill(panelLeft + 2, panelTop + 3, panelRight + 2, panelBottom + 3, 0xFF101010);
		graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xFF101112);
		graphics.fill(panelLeft + border, panelTop + border, panelRight - border, panelBottom - border, 0xFF9C8C6E);
		graphics.fill(panelLeft + border + 2, panelTop + border + 2, panelRight - border - 2, panelBottom - border - 2, 0xFF211F1B);

		int contentLeft = panelLeft + Math.round(12 * scale);
		int contentRight = panelRight - Math.round(12 * scale);
		int contentTop = panelTop + Math.round(10 * scale);
		int contentBottom = panelBottom - Math.round(10 * scale);
		graphics.fill(contentLeft, contentTop, contentRight, contentBottom, 0xFF292722);
		int separatorX = contentLeft + Math.max(Math.round(110 * scale), (contentRight - contentLeft) / 3);
		int gradientWidth = contentRight - separatorX;
		for (int band = 0; band < 12; band++) {
			int bandLeft = separatorX + gradientWidth * band / 12;
			int bandRight = separatorX + gradientWidth * (band + 1) / 12;
			int shade = 0x24 + band * 2;
			graphics.fill(bandLeft, contentTop, bandRight, contentBottom, 0xFF000000 | shade << 16 | (shade - 4) << 8 | (shade - 10));
		}
		for (int textureY = contentTop + Math.round(8 * scale); textureY < contentBottom; textureY += Math.max(12, Math.round(18 * scale))) {
			graphics.fill(contentLeft + Math.round(5 * scale), textureY, contentRight - Math.round(5 * scale), textureY + 1, 0x12000000);
		}
		graphics.fill(separatorX, contentTop, separatorX + Math.max(2, Math.round(3 * scale)), contentBottom, 0xFF111110);
		if (selectedQuest == null) {
			graphics.centeredText(font, Component.translatable("gui.worldmilestones.empty"),
					(separatorX + contentRight) / 2, (contentTop + contentBottom) / 2, 0xFFE1E1E1);
		} else {
			drawQuestDetails(graphics, separatorX + Math.round(12 * scale), contentRight,
					contentTop + Math.round(12 * scale), contentBottom, scale);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawQuestRows(graphics, scale, panelTop, panelBottom);
		drawCategoryTabIcons(graphics, categories, panelLeft, tabY, tabSize, scale);
		drawRotationCountdown(graphics, panelLeft, tabY, tabSize, scale);
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
		int tabY = Math.max(4, Math.round(6 * scale));
		int tabSize = Math.max(28, Math.round(42 * scale));
		int panelTop = categoryPanelTop(tabY, tabSize, scale);
		int panelBottom = Math.min(height - marginX, panelTop + desiredPanelHeight(scale));
		int contentLeft = marginX + Math.round(12 * scale);
		int contentRight = width - marginX - Math.round(12 * scale);
		int contentTop = panelTop + Math.round(10 * scale);
		int contentBottom = panelBottom - Math.round(10 * scale);
		int separatorX = contentLeft + Math.max(Math.round(110 * scale), (contentRight - contentLeft) / 3);
		int listWidth = separatorX - contentLeft - Math.round(8 * scale);
		int rowHeight = Math.max(26, Math.round(34 * scale));
		int buttonHeight = Math.max(22, Math.round(28 * scale));
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
			int y = contentTop + Math.round(5 * scale) + row++ * rowHeight;
			if (selectedQuest == null) {
				selectedQuest = id;
			}
			addRenderableWidget(Button.builder(CommonComponents.EMPTY, button -> {
				selectedQuest = id;
				clearWidgets();
				init();
			}).bounds(contentLeft + Math.round(4 * scale), y, Math.max(buttonHeight * 3, listWidth - Math.round(8 * scale)), buttonHeight).build());
		}
	}

	private void drawQuestRows(GuiGraphicsExtractor graphics, float scale, int panelTop, int panelBottom) {
		if (selectedCategory == null) {
			return;
		}
		int marginX = Math.max(4, Math.round(12 * scale));
		int contentLeft = marginX + Math.round(12 * scale);
		int contentRight = width - marginX - Math.round(12 * scale);
		int contentTop = panelTop + Math.round(10 * scale);
		int contentBottom = panelBottom - Math.round(10 * scale);
		int separatorX = contentLeft + Math.max(Math.round(110 * scale), (contentRight - contentLeft) / 3);
		int listWidth = separatorX - contentLeft - Math.round(8 * scale);
		int rowHeight = Math.max(26, Math.round(34 * scale));
		int buttonHeight = Math.max(22, Math.round(28 * scale));
		int maxRows = Math.max(1, (contentBottom - contentTop - Math.round(8 * scale)) / rowHeight);
		int row = 0;
		for (JsonElement element : array("quests")) {
			JsonObject quest = element.getAsJsonObject();
			if (!selectedCategory.equals(string(quest, "category", "")) || row >= maxRows) {
				continue;
			}
			String id = string(quest, "id", "");
			long current = progress(id);
			long required = Math.max(1, number(quest, "required_progress", 1));
			boolean completed = isCompleted(id) || current >= required;
			int x = contentLeft + Math.round(4 * scale);
			int y = contentTop + Math.round(5 * scale) + row++ * rowHeight;
			int right = x + Math.max(buttonHeight * 3, listWidth - Math.round(8 * scale));
			int bottom = y + buttonHeight;
			boolean selected = id.equals(selectedQuest);
			int fill = selected ? 0xFF554324 : completed ? 0xFF28362B : 0xFF33312D;
			int edge = selected ? 0xFFE3B94F : completed ? 0xFF638C64 : 0xFF504C43;
			graphics.fill(x, y, right, bottom, edge);
			graphics.fill(x + 1, y + 1, right - 1, bottom - 1, fill);
			graphics.item(itemStack(string(quest, "icon", "minecraft:book")), x + Math.round(4 * scale), y + (buttonHeight - 16) / 2);
			int progressColor = completed ? 0xFF8DDB9C : selected ? 0xFFE8CA78 : 0xFFC3BFB3;
			String progressText = Math.min(current, required) + "/" + required;
			int progressWidth = font.width(progressText);
			int textX = x + Math.round(24 * scale);
			int textRight = right - Math.round(6 * scale) - progressWidth;
			String name = string(quest, "name", id);
			String clippedName = font.plainSubstrByWidth(name, Math.max(0, textRight - textX));
			graphics.text(font, clippedName, textX, y + (buttonHeight - 9) / 2, completed ? 0xFFE5F3E6 : 0xFFF1EDE3);
			graphics.text(font, progressText, right - Math.round(6 * scale) - progressWidth, y + (buttonHeight - 9) / 2, progressColor);
		}
	}

	private void drawCategoryTabIcons(GuiGraphicsExtractor graphics, JsonArray categories, int panelLeft, int tabY, int tabSize, float scale) {
		int gap = Math.max(2, Math.round(4 * scale));
		int x = panelLeft + Math.round(8 * scale);
		for (int index = 0; index < categories.size(); index++) {
			JsonObject category = categories.get(index).getAsJsonObject();
			int tabX = x + index * (tabSize + gap);
			boolean selected = selectedCategory != null && selectedCategory.equals(string(category, "id", ""));
			graphics.fill(tabX, tabY, tabX + tabSize, tabY + tabSize, selected ? 0xFFE3B94F : 0xFF171819);
			graphics.fill(tabX + 1, tabY + 1, tabX + tabSize - 1, tabY + tabSize - 1, selected ? 0xFF29251D : 0xFF30302D);
			ItemStack icon = itemStack(string(category, "icon", "minecraft:book"));
			graphics.item(icon, tabX + (tabSize - 16) / 2, tabY + (tabSize - 16) / 2);
			if (selected) {
				graphics.fill(tabX + Math.round(5 * scale), tabY + tabSize - Math.max(3, Math.round(4 * scale)),
						tabX + tabSize - Math.round(5 * scale), tabY + tabSize - Math.max(1, Math.round(2 * scale)), 0xFFFFD866);
			}
		}
	}

	private void drawRotationCountdown(GuiGraphicsExtractor graphics, int panelLeft, int tabY, int tabSize, float scale) {
		JsonObject category = findCategory(selectedCategory);
		if (category == null) {
			return;
		}
		long remainingMillis = number(category, "rotation_remaining_ms", -1);
		if (remainingMillis < 0) {
			return;
		}
		remainingMillis = Math.max(0, remainingMillis - Math.max(0, System.currentTimeMillis() - snapshotReceivedAt));
		long seconds = (remainingMillis + 999) / 1000;
		long days = seconds / 86_400;
		long hours = (seconds % 86_400) / 3_600;
		long minutes = (seconds % 3_600) / 60;
		String remaining = days > 0 ? days + "d " + hours + "h"
				: hours > 0 ? hours + "h " + minutes + "m" : minutes + "m " + (seconds % 60) + "s";
		graphics.text(font, Component.translatable("gui.worldmilestones.refresh", remaining),
				panelLeft + Math.round(4 * scale), tabY + tabSize + Math.round(2 * scale), 0xFFE4D7B8);
	}

	private int categoryPanelTop(int tabY, int tabSize, float scale) {
		int top = tabY + tabSize - Math.max(4, Math.round(6 * scale));
		return hasRotationCountdown() ? top + Math.round(22 * scale) : top;
	}

	private boolean hasRotationCountdown() {
		JsonObject category = findCategory(selectedCategory);
		return category != null && number(category, "rotation_remaining_ms", -1) >= 0;
	}

	private void drawQuestDetails(GuiGraphicsExtractor graphics, int left, int right, int top, int bottom, float scale) {
		JsonObject quest = findQuest(selectedQuest);
		if (quest == null) {
			graphics.centeredText(font, Component.translatable("gui.worldmilestones.empty"), (left + right) / 2, (top + bottom) / 2, 0xFFFFFFFF);
			return;
		}
		int textWidth = Math.max(80, right - left);
		graphics.fill(left, top, left + Math.round(30 * scale), top + Math.round(30 * scale), 0xFF51452D);
		graphics.fill(left + 1, top + 1, left + Math.round(30 * scale) - 1, top + Math.round(30 * scale) - 1, 0xFF25221C);
		graphics.item(itemStack(string(quest, "icon", "minecraft:book")), left + Math.round(7 * scale), top + Math.round(7 * scale));
		String title = string(quest, "name", selectedQuest).toUpperCase(java.util.Locale.ROOT);
		String clippedTitle = font.plainSubstrByWidth(title, Math.max(0, textWidth - Math.round(42 * scale)));
		graphics.text(font, clippedTitle, left + Math.round(38 * scale), top + Math.round(5 * scale), 0xFFFFD866);
		int dividerY = top + Math.round(34 * scale);
		graphics.fill(left, dividerY, left + textWidth, dividerY + 1, 0xFF776747);
		int y = drawWrapped(graphics, string(quest, "description", ""), left, dividerY + Math.round(8 * scale), textWidth, 0xFFE2E0D8, scale);
		y += Math.round(2 * scale);
		graphics.text(font, Component.translatable("gui.worldmilestones.progress"), left, y, 0xFFFFD866);
		y += Math.round(13 * scale);
		long current = progress(selectedQuest);
		long required = Math.max(1, number(quest, "required_progress", 1));
		int barWidth = Math.max(50, textWidth - Math.round(48 * scale));
		int filled = (int) (barWidth * Math.min(current, required) / required);
		graphics.fill(left, y + 3, left + barWidth, y + 11, 0xFF171819);
		graphics.fill(left, y + 3, left + filled, y + 11, 0xFFE0B442);
		graphics.text(font, Math.min(current, required) + "/" + required, left + barWidth + Math.round(5 * scale), y + 1, 0xFFECE8DD);
		y += Math.round(20 * scale);
		boolean completed = isCompleted(selectedQuest) || current >= required;
		graphics.text(font, Component.translatable(completed ? "gui.worldmilestones.completed" : "gui.worldmilestones.in_progress"), left, y, completed ? 0xFF7FE09A : 0xFFD0C9B9);
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
				String marker = sectionComplete ? "\u2713 " : "\u25cb ";
				graphics.text(font, marker + string(section, "name", sectionId) + " "
						+ Math.min(sectionCurrent, sectionRequired) + "/" + sectionRequired, left, y, sectionComplete ? 0xFF8DDB9C : 0xFFD2D0C8);
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

	private int desiredPanelHeight(float scale) {
		int questCount = 0;
		for (JsonElement element : array("quests")) {
			if (selectedCategory != null && selectedCategory.equals(string(element.getAsJsonObject(), "category", ""))) {
				questCount++;
			}
		}
		JsonObject quest = findQuest(selectedQuest);
		int sectionCount = quest != null && quest.has("sections") && quest.get("sections").isJsonArray()
				? quest.getAsJsonArray("sections").size() : 0;
		int listHeight = questCount * Math.max(26, Math.round(34 * scale)) + Math.round(30 * scale);
		int detailHeight = Math.round((150 + sectionCount * 13) * scale);
		return Math.max(Math.round(220 * scale), Math.max(listHeight, detailHeight));
	}

	private long progress(String questId) {
		return snapshot.has("progress") && snapshot.getAsJsonObject("progress").has(questId)
				? snapshot.getAsJsonObject("progress").get(questId).getAsLong() : 0;
	}

	private boolean isCompleted(String questId) {
		return array("completed").asList().stream().anyMatch(element -> questId.equals(element.getAsString()));
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

}