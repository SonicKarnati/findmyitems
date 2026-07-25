package dev.smpb.findmyitems.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            var configDir = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
            var configPath = configDir.resolve("findmyitems.json");
            var config = ModConfig.load(configPath);
            return ConfigScreen.create(parent, config, configPath);
        };
    }
}
