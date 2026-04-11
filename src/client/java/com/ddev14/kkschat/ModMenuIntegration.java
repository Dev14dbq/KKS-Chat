package com.ddev14.kkschat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;

/**
 * Интеграция с ModMenu — открывает стандартный экран настроек чата
 * с инжектированными настройками KKS Chat внизу.
 */
public class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new ChatOptionsScreen(parent, Minecraft.getInstance().options);
	}
}
