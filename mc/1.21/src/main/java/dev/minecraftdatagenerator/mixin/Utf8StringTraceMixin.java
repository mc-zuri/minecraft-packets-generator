package dev.minecraftdatagenerator.mixin;
import dev.minecraftdatagenerator.packetshapes.WireTrace;
import net.minecraft.network.Utf8String;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Utf8String.class)
public class Utf8StringTraceMixin {
    @Inject(method = "write(Lio/netty/buffer/ByteBuf;Ljava/lang/CharSequence;I)V", at = @At("HEAD"))
    private static void begin(CallbackInfo ci) { WireTrace.enter("string"); }
    @Inject(method = "write(Lio/netty/buffer/ByteBuf;Ljava/lang/CharSequence;I)V", at = @At("RETURN"))
    private static void end(CallbackInfo ci) { WireTrace.leave(); }
}
