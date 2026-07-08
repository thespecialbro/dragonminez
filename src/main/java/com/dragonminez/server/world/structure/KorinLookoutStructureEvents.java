package com.dragonminez.server.world.structure;

import com.dragonminez.Reference;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class KorinLookoutStructureEvents {
	private KorinLookoutStructureEvents() {
	}

	@SubscribeEvent
	public static void onChunkLoad(ChunkEvent.Load event) {
		if (!(event.getLevel() instanceof ServerLevel level)) {
			return;
		}
		KorinLookoutStructureHandler.onChunkLoad(level, event.getChunk());
	}
}
