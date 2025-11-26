package com.ddev14.kkschat.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import com.mojang.blaze3d.platform.Window;

/**
 * Mixin для доступа к window handle без reflection
 */
@Mixin(Window.class)
public interface WindowMixin {
	@Accessor("window")
	long getWindowHandle();
}

