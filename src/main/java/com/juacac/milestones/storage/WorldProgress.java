package com.juacac.milestones.storage;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorldProgress {
	public Map<String, Long> globalProgress = new HashMap<>();
	public Map<String, Long> teamProgress = new HashMap<>();
	public Map<String, Long> globalSectionProgress = new HashMap<>();
	public Map<String, Long> teamSectionProgress = new HashMap<>();
	public Set<String> completedGlobal = new HashSet<>();
	public Set<String> completedTeams = new HashSet<>();
	public Set<String> rewardedGlobalSections = new HashSet<>();
	public Set<String> rewardedTeamSections = new HashSet<>();
	public Set<String> milestonesAdmins = new HashSet<>();
	public Map<String, List<JsonObject>> globalRewardDefinitions = new HashMap<>();
	public long globalRewardSerial;
	public Map<String, CategoryRotation> categoryRotations = new HashMap<>();
	public String weeklyRotationKey = "";
	public java.util.List<String> activeWeeklyQuests = new ArrayList<>();

	public static final class CategoryRotation {
		public long intervalMillis;
		public long nextRotationAt;
		public List<String> activeQuestIds = new ArrayList<>();
	}
}