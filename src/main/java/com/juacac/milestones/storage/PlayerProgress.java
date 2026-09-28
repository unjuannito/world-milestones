package com.juacac.milestones.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PlayerProgress {
	public Map<String, Long> progress = new HashMap<>();
	public Map<String, Long> sectionProgress = new HashMap<>();
	public Map<String, Long> automaticCounts = new HashMap<>();
	public Map<String, Long> automaticBaselines = new HashMap<>();
	public Set<String> completed = new HashSet<>();
	public Set<String> rewardedSections = new HashSet<>();
}