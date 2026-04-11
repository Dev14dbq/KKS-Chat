package com.ddev14.kkschat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class KksChatModClient implements ClientModInitializer {

	private static final KksChatHud HUD = new KksChatHud();
	
	public static KksChatHud getHud() {
		return HUD;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void onInitializeClient() {
		// Render our custom chat box above the hotbar.
		// Note: HudRenderCallback is deprecated but still functional in 1.21.9
		// TODO: Update to new API when available
		HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> HUD.onHudRender(guiGraphics));

		// Block vanilla chat и одновременно прокинуть сообщения в наш HUD
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			// Всегда сохраняем сообщения в наш HUD (даже когда выключен)
			HUD.onSystemMessage(message);
			// Блокируем стандартный чат только если наш чат включен
			// Сообщения от модов, которые добавляются напрямую в ChatComponent, будут заблокированы в ChatComponentMixin
			return !HUD.isEnabled();
		});

		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, chatType, timestamp) -> {
			// Всегда сохраняем сообщения в наш HUD (даже когда выключен)
			// Передаем информацию об отправителе (GameProfile), если доступна
			HUD.onPlayerMessage(message, sender);
			// Блокируем стандартный чат только если наш чат включен
			// Сообщения от модов, которые добавляются напрямую в ChatComponent, будут заблокированы в ChatComponentMixin
			return !HUD.isEnabled();
		});

		// Обработка открытия экрана достижений теперь происходит через MinecraftMixin
		// который перехватывает handleKeybinds и открывает экран достижений

		// Обработка F3+D для очистки чата теперь происходит через ChatComponentMixin
		// который перехватывает вызов clearMessages() в ChatComponent
	}
}

