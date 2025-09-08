package net.eliotex.zirconium.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.level.storage.WorldSaveException;
import org.spongepowered.asm.mixin.Mixin;
import org.apache.logging.log4j.Logger;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Unique private boolean savingWorlds = false;
    @Unique private boolean queuedSave = false;
    @Unique private static final Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("MinecraftServer");
    @Shadow public ServerWorld[] worlds;
    @Shadow private boolean shouldResetWorld;

    /**
     * @author Elio
     * @reason Queue to save the worlds more effectively
     * */
    @Overwrite public void saveWorlds(boolean silent) {
        if (this.savingWorlds) {
            this.queuedSave = true;
            return;
        }
        this.savingWorlds = true;
        try {
            if (!this.shouldResetWorld) {
                for (ServerWorld serverWorld : this.worlds) {
                    if (serverWorld != null) {
                        if (!silent) {
                            LOGGER.info("Saving chunks for level '"
                                    + serverWorld.getLevelProperties().getLevelName()
                                    + "'/" + serverWorld.dimension.getName());
                        }
                        try {
                            serverWorld.save(true, null);
                        } catch (WorldSaveException e) {
                            LOGGER.warn(e.getMessage());
                        }
                    }
                }
            }
        } finally {
            this.savingWorlds = false;
            if (this.queuedSave) {
                this.queuedSave = false;
                this.saveWorlds(silent);
            }
        }
    }
}




