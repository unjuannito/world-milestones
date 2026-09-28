package com.juacac.milestones.network;

import com.juacac.milestones.TemplateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MilestoneSnapshotPayload(String json) implements CustomPacketPayload {
	public static final Type<MilestoneSnapshotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TemplateMod.MOD_ID, "snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MilestoneSnapshotPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			MilestoneSnapshotPayload::json,
			MilestoneSnapshotPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}