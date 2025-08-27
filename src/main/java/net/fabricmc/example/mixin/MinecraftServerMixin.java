package net.fabricmc.example.mixin;

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

    /***/
    @Overwrite
    protected void saveWorlds(boolean silent) {
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

/*
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow public abstract PlayerManager getPlayerManager();
    @Shadow @Final public long[] lastTickLengths;
    @Unique private long lastHeadTime = -1L;
    @Unique private double recentTPS = 20.0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        long now = System.nanoTime();
        if (lastHeadTime != -1L) {
            long diff = now - lastHeadTime;
            double seconds = diff / 1.0E9;
            if (seconds > 0) {
                recentTPS = 1.0 / seconds;
            }
        }
        lastHeadTime = now;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void stepViewDistanceGradually(CallbackInfo ci) {
        PlayerManager pm = this.getPlayerManager();
        GradualViewDistance g = (GradualViewDistance) (Object) pm;
        if (!g.isGradualActive()) return;
        int checked = 0;
        int needed = 3;
        for (int i = lastTickLengths.length - 1; i >= 0 && checked < needed; i--) {
            long tickTime = lastTickLengths[i];
            if (tickTime > 0L) {
                double ms = tickTime * 1.0E-6;
                if (ms > 100.0) return;
                checked++;
            }
        }
        if (checked < needed) return;
        int current = ((PlayerManagerAccessor)(Object) pm).getViewDistanceRaw();
        if (recentTPS < 14 || recentTPS > 21) {
            return;
        }
        int target = g.getGradualTargetDistance();
        if (target > current) {
            pm.setViewDistance(current + 1);
            if (current + 1 >= target) {
                g.clearGradual();
            }
        } else {
            g.clearGradual();
        }
    }
}
 */




