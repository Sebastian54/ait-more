package dev.aitmore;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class AitMore implements ModInitializer {

    public static final String MOD_ID = "ait-more";

    public static final SoundEvent JELLY_BABIES_DROP_SOUND = registerSound("jelly_babies_drop");

    private static SoundEvent registerSound(String path) {
        Identifier id = Identifier.of(MOD_ID, path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    @Override
    public void onInitialize() {
        AitMoreCommands.register();
    }
}