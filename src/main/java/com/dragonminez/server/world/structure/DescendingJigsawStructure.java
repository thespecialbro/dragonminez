package com.dragonminez.server.world.structure;

import com.dragonminez.Reference;
import com.dragonminez.server.world.structure.helper.DMZProcessorLists;
import com.dragonminez.server.world.structure.helper.MainStructureTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.Optional;

public class DescendingJigsawStructure extends Structure {
	public static final Codec<DescendingJigsawStructure> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					settingsCodec(instance),
					StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
					ResourceLocation.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(s -> s.startJigsawName),
					ResourceLocation.CODEC.fieldOf("start_template").forGetter(s -> s.startTemplate),
					ResourceLocation.CODEC.fieldOf("pillar_template").forGetter(s -> s.pillarTemplate),
					Codec.STRING.fieldOf("downward_pool").forGetter(s -> s.downwardPool),
					Codec.intRange(1, 20).fieldOf("max_depth").forGetter(s -> s.maxDepthCap),
					HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
					Codec.BOOL.fieldOf("use_expansion_hack").forGetter(s -> s.useExpansionHack),
					Codec.intRange(1, 512).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
					Heightmap.Types.CODEC.fieldOf("surface_heightmap").forGetter(s -> s.surfaceHeightmap)
			).apply(instance, DescendingJigsawStructure::new));

	private static final int DEFAULT_PILLAR_HEIGHT = 14;
	private static final int PILLAR_TOP_JIGSAW_Y = 13;
	private static final int LOOKOUT_JIGSAW_X = 13;
	private static final int LOOKOUT_JIGSAW_Z = 16;
	private static final int PILLAR_TOP_JIGSAW_X = 3;
	private static final int PILLAR_TOP_JIGSAW_Z = 3;

	private final Holder<StructureTemplatePool> startPool;
	private final Optional<ResourceLocation> startJigsawName;
	private final ResourceLocation startTemplate;
	private final ResourceLocation pillarTemplate;
	private final String downwardPool;
	private final int maxDepthCap;
	private final HeightProvider startHeight;
	private final boolean useExpansionHack;
	private final int maxDistanceFromCenter;
	private final Heightmap.Types surfaceHeightmap;

	public DescendingJigsawStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool,
									 Optional<ResourceLocation> startJigsawName, ResourceLocation startTemplate,
									 ResourceLocation pillarTemplate, String downwardPool, int maxDepthCap,
									 HeightProvider startHeight,
									 boolean useExpansionHack, int maxDistanceFromCenter,
									 Heightmap.Types surfaceHeightmap) {
		super(settings);
		this.startPool = startPool;
		this.startJigsawName = startJigsawName;
		this.startTemplate = startTemplate;
		this.pillarTemplate = pillarTemplate;
		this.downwardPool = downwardPool;
		this.maxDepthCap = maxDepthCap;
		this.startHeight = startHeight;
		this.useExpansionHack = useExpansionHack;
		this.maxDistanceFromCenter = maxDistanceFromCenter;
		this.surfaceHeightmap = surfaceHeightmap;
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int x = chunkPos.getMinBlockX();
		int z = chunkPos.getMinBlockZ();

		int surfaceY = sampleHighestSurface(context, x, z);
		int pillarHeight = resolvePillarHeight(context);
		int targetAttachY = this.startHeight.sample(context.random(),
				new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()));

		int rise = targetAttachY - surfaceY - PILLAR_TOP_JIGSAW_Y;
		if (rise < 0) {
			return Optional.empty();
		}

		int depth = Math.min(this.maxDepthCap, (rise / pillarHeight) + 1);
		int attachY = surfaceY + (depth - 1) * pillarHeight + PILLAR_TOP_JIGSAW_Y;
		int pillarOriginX = x + LOOKOUT_JIGSAW_X - PILLAR_TOP_JIGSAW_X;
		int pillarOriginZ = z + LOOKOUT_JIGSAW_Z - PILLAR_TOP_JIGSAW_Z;
		BlockPos lookoutOrigin = new BlockPos(x, attachY, z);

		return Optional.of(new GenerationStub(lookoutOrigin, builder ->
				placeTower(context, builder, lookoutOrigin, pillarOriginX, pillarOriginZ, surfaceY, depth)));
	}

	private void placeTower(GenerationContext context, StructurePiecesBuilder builder, BlockPos lookoutOrigin,
							int pillarOriginX, int pillarOriginZ, int surfaceY, int depth) {
		var templates = context.structureTemplateManager();
		Registry<StructureProcessorList> processorLists =
				context.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST);
		Holder<StructureProcessorList> pillarProcessors =
				processorLists.getHolderOrThrow(DMZProcessorLists.KORIN_PILLAR_FOUNDATION);
		Rotation rotation = Rotation.NONE;

		for (int i = 0; i < depth; i++) {
			int pillarOriginY = surfaceY + (i * DEFAULT_PILLAR_HEIGHT);
			boolean isBase = i == 0;
			ResourceLocation pillarId = isBase
					? ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "korin_pillar_base")
					: pickPillarVariant(context.random());
			StructurePoolElement pillarElement = (isBase
					? StructurePoolElement.single(pillarId.toString(), pillarProcessors)
					: StructurePoolElement.single(pillarId.toString()))
					.apply(StructureTemplatePool.Projection.RIGID);
			BlockPos pillarOrigin = new BlockPos(pillarOriginX, pillarOriginY, pillarOriginZ);
			BoundingBox pillarBox = pillarElement.getBoundingBox(templates, pillarOrigin, rotation);
			builder.addPiece(new PoolElementStructurePiece(
					templates, pillarElement, pillarOrigin, 0, rotation, pillarBox));
		}

		StructurePoolElement lookoutElement = StructurePoolElement
				.single(this.startTemplate.toString())
				.apply(StructureTemplatePool.Projection.RIGID);
		BoundingBox lookoutBox = lookoutElement.getBoundingBox(templates, lookoutOrigin, rotation);
		builder.addPiece(new PoolElementStructurePiece(
				templates, lookoutElement, lookoutOrigin, 0, rotation, lookoutBox));
	}

	private static ResourceLocation pickPillarVariant(RandomSource random) {
		int roll = random.nextInt(100);
		String path;
		if (roll < 45) {
			path = "korin_pillar";
		} else if (roll < 90) {
			path = "korin_pillar_2";
		} else {
			path = "korin_pillar_3";
		}
		return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, path);
	}

	private int sampleHighestSurface(GenerationContext context, int chunkMinX, int chunkMinZ) {
		int best = Integer.MIN_VALUE;
		for (int dx = 0; dx < 16; dx += 4) {
			for (int dz = 0; dz < 16; dz += 4) {
				int h = context.chunkGenerator().getFirstFreeHeight(
						chunkMinX + dx,
						chunkMinZ + dz,
						this.surfaceHeightmap,
						context.heightAccessor(),
						context.randomState()
				);
				if (h > best) {
					best = h;
				}
			}
		}
		return best;
	}

	private int resolvePillarHeight(GenerationContext context) {
		int tallest = DEFAULT_PILLAR_HEIGHT;
		String namespace = this.pillarTemplate.getNamespace();
		for (String path : new String[]{"korin_pillar_base", "korin_pillar", "korin_pillar_2", "korin_pillar_3"}) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
			int height = context.structureTemplateManager()
					.get(id)
					.map(template -> template.getSize().getY())
					.orElse(0);
			tallest = Math.max(tallest, height);
		}
		return tallest;
	}

	@Override
	public StructureType<?> type() {
		return MainStructureTypes.DESCENDING_JIGSAW.get();
	}
}
