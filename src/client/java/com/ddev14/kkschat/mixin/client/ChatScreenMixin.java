package com.ddev14.kkschat.mixin.client;

import com.ddev14.kkschat.KksChatHud;
import com.ddev14.kkschat.KksChatModClient;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

	protected ChatScreenMixin(Component title) {
		super(title);
	}

	@Inject(at = @At("HEAD"), method = "mouseScrolled", cancellable = false)
	private void onMouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
		// Перехватываем прокрутку мыши для истории чата
		KksChatHud hud = KksChatModClient.getHud();
		if (hud != null) {
			// Используем deltaY для вертикальной прокрутки
			hud.handleMouseScroll(deltaY);
		}
	}
	
	@Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
	private void onMouseClicked(MouseButtonEvent event, boolean handled, CallbackInfoReturnable<Boolean> cir) {
		// Обрабатываем клик по сжатым сообщениям
		if (!handled && event.button() == 0) { // Левая кнопка мыши
			KksChatHud hud = KksChatModClient.getHud();
			if (hud != null) {
				if (hud.handleMessageClick(event.x(), event.y())) {
					// Клик обработан, отменяем дальнейшую обработку
					cir.setReturnValue(true);
					cir.cancel();
				}
			}
		}
	}
	
	@Inject(at = @At("HEAD"), method = "onClose")
	private void onChatClose(CallbackInfo ci) {
		// Сворачиваем развернутые сообщения при закрытии чата
		KksChatHud hud = KksChatModClient.getHud();
		if (hud != null) {
			hud.collapseMessagesOnChatClose();
		}
	}
}
