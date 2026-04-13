package com.ddev14.kkschat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public class KksChatModClient implements ClientModInitializer {

	private static final KksChatHud HUD = createHud();

	private static KksChatHud createHud() {
		KksChatHud hud = new KksChatHud();
		hud.loadConfig();
		KksChatConfig.save(hud);
		return hud;
	}
	
	public static KksChatHud getHud() {
		return HUD;
	}

	@Override
	public void onInitializeClient() {
		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath("kks-chat", "chat_hud"),
			(guiGraphics, deltaTracker) -> HUD.onHudRender(guiGraphics)
		);

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

