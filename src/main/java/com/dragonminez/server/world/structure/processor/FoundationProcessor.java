package com.dragonminez.server.world.structure.processor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FoundationProcessor extends StructureProcessor {
	private static final int UNLIMITED_ANCHOR_LOCAL_Y = Integer.MAX_VALUE;

	public static final Codec<FoundationProcessor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.intRange(1, 256).optionalFieldOf("max_depth", 32).forGetter(p -> p.maxDepth),
			Codec.intRange(0, 512).optionalFieldOf("max_anchor_local_y", UNLIMITED_ANCHOR_LOCAL_Y)
					.forGetter(p -> p.maxAnchorLocalY)
	).apply(instance, FoundationProcessor::new));

	private final int maxDepth;
	private final int maxAnchorLocalY;

	public FoundationProcessor(int maxDepth) {
		this(maxDepth, UNLIMITED_ANCHOR_LOCAL_Y);
	}

	public FoundationProcessor(int maxDepth, int maxAnchorLocalY) {
		this.maxDepth = maxDepth;
		this.maxAnchorLocalY = maxAnchorLocalY;
	}

	@Nullable
	@Override
	public StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos, StructureBlockInfo original, StructureBlockInfo relative, StructurePlaceSettings settings) {
		return relative;
	}

	@Override
	public List<StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pos, List<StructureBlockInfo> originalInfos, List<StructureBlockInfo> processedInfos, StructurePlaceSettings settings) {
		if (processedInfos.isEmpty()) {
			return processedInfos;
		}

		// Jigsaw passes the chunk clip box here, not the structure template bounds.
		BoundingBox clipBox = settings.getBoundingBox();

		int structureMinY = Integer.MAX_VALUE;
		for (StructureBlockInfo info : processedInfos) {
			if (!isFoundationAnchor(info.state())) {
				continue;
			}
			structureMinY = Math.min(structureMinY, info.pos().getY());
		}
		if (structureMinY == Integer.MAX_VALUE) {
			return processedInfos;
		}

		Map<Long, StructureBlockInfo> lowestByColumn = new HashMap<>();
		for (StructureBlockInfo info : processedInfos) {
			if (!isFoundationAnchor(info.state())) {
				continue;
			}
			BlockPos p = info.pos();
			if (clipBox != null && !clipBox.isInside(p)) {
				continue;
			}
			if (localY(structureMinY, p) > this.maxAnchorLocalY) {
				continue;
			}

			long column = ChunkPos.asLong(p.getX(), p.getZ());
			StructureBlockInfo current = lowestByColumn.get(column);
			if (current == null || p.getY() < current.pos().getY()) {
				lowestByColumn.put(column, info);
			}
		}
		if (lowestByColumn.isEmpty()) {
			return processedInfos;
		}

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int worldFloor = level.getMinBuildHeight();
		for (StructureBlockInfo info : lowestByColumn.values()) {
			BlockPos p = info.pos();
			int bottomY = p.getY();
			BlockState fill = info.state();

			for (int y = bottomY - 1; y >= worldFloor && y >= bottomY - this.maxDepth; y--) {
				cursor.set(p.getX(), y, p.getZ());
				BlockState existing = level.getBlockState(cursor);
				if (!existing.isAir() && existing.getFluidState().isEmpty() && !existing.canBeReplaced()) {
					break;
				}
				level.setBlock(cursor, fill, 2);
			}
		}
		return processedInfos;
	}

	private static int localY(int structureMinY, BlockPos pos) {
		return pos.getY() - structureMinY;
	}

	private static boolean isFoundationAnchor(BlockState state) {
		return !state.isAir() && !state.is(Blocks.STRUCTURE_VOID) && !state.is(Blocks.JIGSAW);
	}

	@Override
	protected StructureProcessorType<?> getType() {
		return MainStructureProcessors.FOUNDATION.get();
	}
}
