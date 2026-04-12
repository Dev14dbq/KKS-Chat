package com.ddev14.kkschat.mixin.client;

import com.ddev14.kkschat.KksChatModClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin для перехвата очистки чата (F3+D) и всех сообщений, добавляемых в чат
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	
	@Inject(
		method = "clearMessages",
		at = @At("HEAD")
	)
	private void onClearMessages(boolean clearHistory, CallbackInfo ci) {
		// Когда стандартный чат очищается (F3+D), очищаем и наш кастомный чат
		KksChatModClient.getHud().clearChat();
	}
	
	/**
	 * Перехватывает клиентские системные сообщения (от модов и т.д.),
	 * добавляемые напрямую в ChatComponent в 26.1+.
	 */
	@Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true)
	private void kkschat$onAddClientSystemMessage(Component message, CallbackInfo ci) {
		if (message != null && !message.getString().isEmpty()) {
			KksChatModClient.getHud().onSystemMessage(message);
			if (KksChatModClient.getHud().isEnabled()) {
				ci.cancel();
			}
		}
	}

	/**
	 * Перехватывает серверные системные сообщения, добавляемые напрямую в ChatComponent в 26.1+.
	 */
	@Inject(method = "addServerSystemMessage", at = @At("HEAD"), cancellable = true)
	private void kkschat$onAddServerSystemMessage(Component message, CallbackInfo ci) {
		if (message != null && !message.getString().isEmpty()) {
			KksChatModClient.getHud().onSystemMessage(message);
			if (KksChatModClient.getHud().isEnabled()) {
				ci.cancel();
			}
		}
	}
}

