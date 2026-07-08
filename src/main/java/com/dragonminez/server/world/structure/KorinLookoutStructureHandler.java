package com.dragonminez.server.world.structure;

import com.dragonminez.server.world.structure.helper.DMZStructures;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Korin's tower spans many blocks vertically and the lookout platform spans multiple horizontal chunks.
 * Structure pieces only place when their chunk is generated, so the center column (pillars) can appear
 * before the lookout deck in neighboring chunks. Force-generate the full horizontal footprint once
 * the structure start is known.
 */
public final class KorinLookoutStructureHandler {
	private static final int CHUNK_PADDING = 1;
	private static final Set<Long> PINNED = ConcurrentHashMap.newKeySet();

	private KorinLookoutStructureHandler() {
	}

	public static void onChunkLoad(ServerLevel level, ChunkAccess chunk) {
		if (!level.dimension().equals(Level.OVERWORLD)) {
			return;
		}

		var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		Structure kamilookout = structureRegistry.get(DMZStructures.KAMILOOKOUT.location());
		if (kamilookout == null) {
			return;
		}

		for (StructureStart start : chunk.getAllStarts().values()) {
			if (start == null || !start.isValid() || start.getStructure() != kamilookout) {
				continue;
			}

			long key = ChunkPos.asLong(start.getBoundingBox().minX() >> 4, start.getBoundingBox().minZ() >> 4);
			if (!PINNED.add(key)) {
				continue;
			}

			forceFootprint(level, start.getBoundingBox());
		}
	}

	private static void forceFootprint(ServerLevel level, BoundingBox box) {
		int minChunkX = (box.minX() >> 4) - CHUNK_PADDING;
		int maxChunkX = (box.maxX() >> 4) + CHUNK_PADDING;
		int minChunkZ = (box.minZ() >> 4) - CHUNK_PADDING;
		int maxChunkZ = (box.maxZ() >> 4) + CHUNK_PADDING;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
			}
		}
	}
}
