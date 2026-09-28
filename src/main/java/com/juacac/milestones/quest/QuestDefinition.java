package com.juacac.milestones.quest;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public final class QuestDefinition {
	public String id = "";
	public String name = "";
	public String description = "";
	public String category = "";
	public String icon = "minecraft:book";
	public String type = "quest";
	public String visibility = "public";
	public boolean hidden;
	public boolean repeatable;
	@SerializedName("required_progress")
	public long requiredProgress = 1;
	@SerializedName("progress_scope")
	public ProgressScope progressScope = ProgressScope.PERSONAL;
	@SerializedName("completion_mode")
	public CompletionMode completionMode = CompletionMode.ALL;
	@SerializedName("required_sections")
	public int requiredSections;
	public int order;
	public List<JsonObject> requirements = new ArrayList<>();
	public List<JsonObject> rewards = new ArrayList<>();
	public List<Section> sections = new ArrayList<>();

	public enum ProgressScope {
		PERSONAL,
		GLOBAL,
		TEAM
	}

	public enum CompletionMode {
		ALL,
		ANY,
		X_OF_Y
	}

	public static final class Section {
		public String id = "";
		public String name = "";
		public String description = "";
		public String icon = "minecraft:book";
		public boolean required = true;
		@SerializedName("required_progress")
		public long requiredProgress = 1;
		public List<JsonObject> requirements = new ArrayList<>();
		public List<JsonObject> rewards = new ArrayList<>();
	}
}