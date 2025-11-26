package com.ddev14.kkschat;

import com.ddev14.kkschat.screen.KksChatSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Интеграция с ModMenu для открытия экрана настроек.
 */
public class ModMenuIntegration implements ModMenuApi {
	
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new KksChatSettingsScreen(parent);
	}
}

