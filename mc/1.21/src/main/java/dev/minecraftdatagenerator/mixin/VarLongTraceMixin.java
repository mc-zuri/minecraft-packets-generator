package dev.minecraftdatagenerator.mixin;
import dev.minecraftdatagenerator.packetshapes.WireTrace;
import net.minecraft.network.VarLong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import io.netty.buffer.ByteBuf;
@Mixin(VarLong.class)
public class VarLongTraceMixin {
    @Inject(method = "write(Lio/netty/buffer/ByteBuf;J)Lio/netty/buffer/ByteBuf;", at = @At("HEAD"))
    private static void begin(CallbackInfoReturnable<ByteBuf> ci) { WireTrace.enter("varlong"); }
    @Inject(method = "write(Lio/netty/buffer/ByteBuf;J)Lio/netty/buffer/ByteBuf;", at = @At("RETURN"))
    private static void end(CallbackInfoReturnable<ByteBuf> ci) { WireTrace.leave(); }
}
