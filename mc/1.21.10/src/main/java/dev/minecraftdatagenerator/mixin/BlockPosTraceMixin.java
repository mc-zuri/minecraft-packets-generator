package dev.minecraftdatagenerator.mixin;
import dev.minecraftdatagenerator.packetshapes.WireTrace;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FriendlyByteBuf.class)
public class BlockPosTraceMixin {
    @Inject(method = "writeBlockPos(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/network/FriendlyByteBuf;", at = @At("HEAD"))
    private void begin(CallbackInfoReturnable<FriendlyByteBuf> ci) { WireTrace.enter("blockpos"); }
    @Inject(method = "writeBlockPos(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/network/FriendlyByteBuf;", at = @At("RETURN"))
    private void end(CallbackInfoReturnable<FriendlyByteBuf> ci) { WireTrace.leave(); }
    @Inject(method = "writeBlockPos(Lio/netty/buffer/ByteBuf;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"))
    private static void beginStatic(CallbackInfo ci) { WireTrace.enter("blockpos"); }
    @Inject(method = "writeBlockPos(Lio/netty/buffer/ByteBuf;Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"))
    private static void endStatic(CallbackInfo ci) { WireTrace.leave(); }
}
