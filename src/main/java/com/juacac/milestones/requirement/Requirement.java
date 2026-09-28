package com.juacac.milestones.requirement;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface Requirement {
	boolean isMet(ServerPlayer player);
}