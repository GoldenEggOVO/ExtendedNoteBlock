package com.atemukesu.extendednoteblock.bridgeclient;

import java.util.function.Function;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

/**
 * Minecraft 26.2-safe entry button for ExtendedNoteBlock sound-pack settings.
 *
 * Uses Fabric ScreenEvents instead of injecting into OptionsScreen's internal
 * layout. This avoids copying the 26.2 "Done" button width/position, which is
 * what caused the oversized button seen in the old GUI.
 */
public final class SoundPackOptionsButton {
    private static boolean registered;

    private SoundPackOptionsButton() {
    }

    public static void register(Function<Screen, Screen> screenFactory) {
        if (registered) {
            return;
        }
        registered = true;

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof OptionsScreen)) {
                return;
            }

            // Keep the button compact and independent of vanilla's footer layout.
            int buttonWidth = Math.min(190, Math.max(120, scaledWidth - 16));
            int buttonHeight = 20;
            int x = Math.max(8, scaledWidth - buttonWidth - 8);
            int y = 8;

            Button button = Button.builder(
                    Component.translatable("gui.extendednoteblock.options.sound_packs_button"),
                    ignored -> client.gui.setScreen(screenFactory.apply(screen)))
                    .bounds(x, y, buttonWidth, buttonHeight)
                    .build();

            Screens.getWidgets(screen).add(button);
        });
    }
}
