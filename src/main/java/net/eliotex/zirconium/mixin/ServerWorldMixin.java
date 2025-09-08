package net.eliotex.zirconium.mixin;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ScheduledTick;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;


import java.util.List;


@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    @Final @Shadow private Set<ScheduledTick> field_2811; // "seen" / de-dup set
    @Final @Shadow private TreeSet<ScheduledTick> scheduledTicks; // pending (sorted)
    @Shadow private List<ScheduledTick> field_6729; // running
    @Unique private final Map<Long, List<ScheduledTick>> fastScheduledTicks = new HashMap<>();
    @Unique private final Map<Long, List<ScheduledTick>> fastRunningTicks = new HashMap<>();

    @Unique private static long toChunkKey(int cx, int cz) {
        return ((long) cx & 0xffffffffL) << 32 | ((long) cz & 0xffffffffL);
    }

    @Unique private static long keyFor(BlockPos pos) {
        return toChunkKey(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Unique private static <T> void bucketAdd(Map<Long, List<T>> map, long key, T value) {
        List<T> b = map.get(key);
        if (b == null) {
            b = new ArrayList<>();
            map.put(key, b);
        } b.add(value);
    }

    @Unique private static <T> void bucketRemove(Map<Long, List<T>> map, long key, T value) {
        List<T> b = map.get(key);
        if (b != null) {
            b.remove(value);
            if (b.isEmpty()) {
                map.remove(key);
            }
        }
    }

    @Redirect(method = {"createAndScheduleBlockTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;II)V",
            "scheduleTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;II)V"}, at = @At(value = "INVOKE",
            target = "Ljava/util/TreeSet;add(Ljava/lang/Object;)Z"))
    private boolean optimizedchunkloader$indexAdd(TreeSet<ScheduledTick> set, Object obj) {
        ScheduledTick tick = (ScheduledTick) obj;
        boolean added = set.add(tick);
        if (added) {
            bucketAdd(this.fastScheduledTicks, keyFor(tick.pos), tick);
        }
        return added;
    }

    @Redirect(method = "method_3644(Z)Z", at = @At(value = "INVOKE", target = "Ljava/util/TreeSet;remove(Ljava/lang/Object;)Z"))
    private boolean optimizedchunkloader$indexRemovePending(@NotNull TreeSet<ScheduledTick> set, Object obj) {
        ScheduledTick tick = (ScheduledTick) obj;
        boolean removed = set.remove(tick);
        if (removed) {
            bucketRemove(this.fastScheduledTicks, keyFor(tick.pos), tick);
        }
        return removed;
    }

    @Redirect(method = "method_3644(Z)Z", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean optimizedchunkloader$indexAddRunning(List<ScheduledTick> list, Object obj) {
        ScheduledTick tick = (ScheduledTick) obj;
        bucketAdd(this.fastRunningTicks, keyFor(tick.pos), tick);
        return list.add((ScheduledTick) obj);
    }

    @Inject(method = "method_3644(Z)Z", at = @At("TAIL"))
    private void optimizedchunkloader$clearRunningIndex(boolean bl, CallbackInfoReturnable<Boolean> cir) {
        this.fastRunningTicks.clear();
    }
    /**
     * @author Elio
     * @reason chunk based blockupdate list, which has the same order like vanilla
     */
    @Overwrite
    public List<ScheduledTick> getScheduledTicks(BlockBox box, boolean remove) {
        List<ScheduledTick> pendingMatches = null;
        List<ScheduledTick> runningMatches = null;

        int minChunkX = box.minX >> 4;
        int maxChunkX = (box.maxX - 1) >> 4;
        int minChunkZ = box.minZ >> 4;
        int maxChunkZ = (box.maxZ - 1) >> 4;

        // 1) pending (scheduledTicks)
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = toChunkKey(cx, cz);
                List<ScheduledTick> bucket = fastScheduledTicks.get(key);
                if (bucket == null) continue;
                Iterator<ScheduledTick> it = bucket.iterator();
                while (it.hasNext()) {
                    ScheduledTick tick = it.next();
                    BlockPos pos = tick.pos;
                    if (pos.getX() >= box.minX && pos.getX() < box.maxX && pos.getZ() >= box.minZ && pos.getZ() < box.maxZ) {
                        if (remove) {
                            it.remove(); // remove from bucket
                            field_2811.remove(tick);
                            scheduledTicks.remove(tick);
                        }
                        if (pendingMatches == null) pendingMatches = new ArrayList<>();
                        pendingMatches.add(tick);
                    }
                }
                if (bucket.isEmpty()) {
                    fastScheduledTicks.remove(key);
                }
            }
        }

        // sort the collected pending ticks (like TreeSet.iterator()).
        if (pendingMatches != null && !pendingMatches.isEmpty()) {
            Collections.sort(pendingMatches);
        }

        // 2) running (field_6729)
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = toChunkKey(cx, cz);
                List<ScheduledTick> bucket = fastRunningTicks.get(key);
                if (bucket == null) continue;
                Iterator<ScheduledTick> it = bucket.iterator();
                while (it.hasNext()) {
                    ScheduledTick tick = it.next();
                    BlockPos pos = tick.pos;
                    if (pos.getX() >= box.minX && pos.getX() < box.maxX && pos.getZ() >= box.minZ && pos.getZ() < box.maxZ) {
                        if (remove) {
                            it.remove();
                            field_2811.remove(tick);
                            field_6729.remove(tick);
                        }
                        if (runningMatches == null) runningMatches = new ArrayList<>();
                        runningMatches.add(tick);
                    }
                }
                if (bucket.isEmpty()) {
                    fastRunningTicks.remove(key);
                }
            }
        }
        if (pendingMatches == null && runningMatches == null) {
            return null;
        }
        if (pendingMatches == null) {
            return runningMatches;
        }
        if (runningMatches == null) {
            return pendingMatches;
        }
        List<ScheduledTick> result = new ArrayList<>(pendingMatches.size() + runningMatches.size());
        result.addAll(pendingMatches);
        result.addAll(runningMatches);
        return result;
    }
}