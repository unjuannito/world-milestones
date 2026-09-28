package com.juacac.milestones.requirement;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class RequirementTypeRegistry {
	private static final Map<String, Function<JsonObject, Requirement>> TYPES = new HashMap<>();

	private RequirementTypeRegistry() {
	}

	public static void register(String type, Function<JsonObject, Requirement> factory) {
		TYPES.put(type, factory);
	}

	public static Optional<Requirement> create(JsonObject json) {
		if (!json.has("type")) {
			return Optional.empty();
		}
		Function<JsonObject, Requirement> factory = TYPES.get(json.get("type").getAsString());
		return factory == null ? Optional.empty() : Optional.of(factory.apply(json));
	}
}