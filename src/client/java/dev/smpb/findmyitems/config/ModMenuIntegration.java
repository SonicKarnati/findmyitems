package dev.smpb.findmyitems.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.smpb.findmyitems.FindMyItemsClient;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // The live instance, not a fresh load: edits here have to reach the running collector.
        return parent -> ConfigScreen.create(parent, FindMyItemsClient.config(), FindMyItemsClient.configPath());
    }
}
