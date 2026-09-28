package com.juacac.milestones.category;

import com.google.gson.annotations.SerializedName;

public final class CategoryDefinition {
	public String id = "";
	public String name = "";
	public String description = "";
	public String icon = "minecraft:book";
	@SerializedName("icon_texture")
	public String iconTexture = "";
	public String color = "#FFFFFF";
	public int order;
	public boolean visible = true;
	public int requiredPermissionLevel;
	@SerializedName("rotation_interval")
	public String rotationInterval = "";
	@SerializedName("active_quest_count")
	public int activeQuestCount = 1;

	public long rotationIntervalMillis() {
		return parseDurationMillis(rotationInterval);
	}

	public static long parseDurationMillis(String duration) {
		if (duration == null || duration.isBlank()) {
			return 0;
		}
		String value = duration.trim().toLowerCase(java.util.Locale.ROOT);
		char unit = value.charAt(value.length() - 1);
		try {
			long amount = Long.parseLong(value.substring(0, value.length() - 1));
			if (amount <= 0) {
				return 0;
			}
			long unitMillis = switch (unit) {
				case 's' -> 1_000L;
				case 'm' -> 60_000L;
				case 'h' -> 3_600_000L;
				case 'd' -> 86_400_000L;
				case 'w' -> 604_800_000L;
				default -> 0L;
			};
			return unitMillis == 0 ? 0 : Math.multiplyExact(amount, unitMillis);
		} catch (ArithmeticException | NumberFormatException exception) {
			return 0;
		}
	}
}