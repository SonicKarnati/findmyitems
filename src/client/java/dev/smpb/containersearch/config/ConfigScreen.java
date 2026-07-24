package dev.smpb.containersearch.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public final class ConfigScreen {
    private ConfigScreen() {}

    public static Screen create(Screen parent, ModConfig config, Path configPath) {
        var builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("screen.container-search.config.title"))
                .setSavingRunnable(() -> config.save(configPath));

        var general = builder.getOrCreateCategory(Component.translatable("screen.container-search.config.category.general"));

        general.addEntry(builder.entryBuilder()
                .startIntSlider(
                        Component.translatable("screen.container-search.config.rescan_interval"),
                        config.rescanIntervalSeconds,
                        0,
                        30)
                .setDefaultValue(5)
                .setTextGetter(value -> Component.literal(value == 0
                        ? "Disabled"
                        : value + "s"))
                .setSaveConsumer(value -> config.rescanIntervalSeconds = value)
                .setTooltip(Component.translatable("screen.container-search.config.rescan_interval.tooltip"))
                .build());

        general.addEntry(builder.entryBuilder()
                .startIntSlider(
                        Component.translatable("screen.container-search.config.search_distance"),
                        config.searchDistanceBlocks,
                        0,
                        512)
                .setDefaultValue(64)
                .setTextGetter(value -> Component.literal(value == 0
                        ? "Unlimited"
                        : value + " blocks"))
                .setSaveConsumer(value -> config.searchDistanceBlocks = value)
                .setTooltip(Component.translatable("screen.container-search.config.search_distance.tooltip"))
                .build());

        return builder.build();
    }
}
