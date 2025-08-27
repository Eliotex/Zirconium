package net.fabricmc.example.mixin;

import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Redirect( method = "setupWorld()V", at = @At( value = "INVOKE", target = "Lnet/minecraft/server/integrated/IntegratedServer;saveWorlds(Z)V" ) )
    private void removeSaving(IntegratedServer instance, boolean b) {
    }
}
