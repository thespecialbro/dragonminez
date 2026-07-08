package com.dragonminez.server.world.structure.helper;

import com.dragonminez.Reference;
import com.dragonminez.server.world.structure.processor.DewaterlogProcessor;
import com.dragonminez.server.world.structure.processor.FoundationProcessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.List;

public class DMZProcessorLists {
	public static final ResourceKey<StructureProcessorList> FOUNDATION = createKey("foundation"),
			SUBMERGED_FOUNDATION = createKey("submerged_foundation"),
			KORIN_PILLAR_FOUNDATION = createKey("korin_pillar_foundation");

	public static void bootstrap(BootstapContext<StructureProcessorList> context) {
		context.register(FOUNDATION, new StructureProcessorList(List.of(
				new FoundationProcessor(32)
		)));
		context.register(SUBMERGED_FOUNDATION, new StructureProcessorList(List.of(
				new FoundationProcessor(32, 6),
				DewaterlogProcessor.INSTANCE
		)));
		context.register(KORIN_PILLAR_FOUNDATION, new StructureProcessorList(List.of(
				new FoundationProcessor(128, 6)
		)));
	}

	private static ResourceKey<StructureProcessorList> createKey(String name) {
		return ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, name));
	}
}
