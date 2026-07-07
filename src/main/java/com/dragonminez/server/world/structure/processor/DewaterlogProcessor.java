package com.dragonminez.server.world.structure.processor;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Clears fluids at structure block positions before placement so waterloggable
 * decoration blocks stay dry when a template generates underwater.
 */
public class DewaterlogProcessor extends StructureProcessor {
	public static final Codec<DewaterlogProcessor> CODEC = Codec.unit(DewaterlogProcessor::new);
	public static final DewaterlogProcessor INSTANCE = new DewaterlogProcessor();

	private DewaterlogProcessor() {
	}

	@Nullable
	@Override
	public StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos, StructureBlockInfo original, StructureBlockInfo relative, StructurePlaceSettings settings) {
		BlockState state = relative.state();
		if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
			return new StructureBlockInfo(relative.pos(), state.setValue(BlockStateProperties.WATERLOGGED, false), relative.nbt());
		}
		return relative;
	}

	@Override
	public List<StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pos, List<StructureBlockInfo> originalInfos, List<StructureBlockInfo> processedInfos, StructurePlaceSettings settings) {
		BoundingBox box = settings.getBoundingBox();
		for (StructureBlockInfo info : processedInfos) {
			BlockState state = info.state();
			if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.JIGSAW)) {
				continue;
			}
			BlockPos blockPos = info.pos();
			if (box != null && !box.isInside(blockPos)) {
				continue;
			}
			if (!level.getFluidState(blockPos).isEmpty()) {
				level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		return processedInfos;
	}

	@Override
	protected StructureProcessorType<?> getType() {
		return MainStructureProcessors.DEWATERLOG.get();
	}
}
