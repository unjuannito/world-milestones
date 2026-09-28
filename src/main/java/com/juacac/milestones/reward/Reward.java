package com.juacac.milestones.reward;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public interface Reward {
	void grant(ServerPlayer player, MinecraftServer server);
}