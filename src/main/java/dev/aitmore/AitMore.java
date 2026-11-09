package dev.aitmore;

import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import dev.aitmore.advancedmode.AdvancedModeManager;
import dev.aitmore.advancedmode.AdvancedModeState;
import dev.aitmore.config.AitMoreConfigLoader;
import dev.aitmore.light.AitMoreLightBlocks;
import dev.aitmore.particlefx.ParticleKeyframeLoader;

public class AitMore implements ModInitializer {

    public static final String MOD_ID = "ait-more";

    public static final SoundEvent JELLY_BABIES_DROP_SOUND = registerSound("jelly_babies_drop");

    private static SoundEvent registerSound(String path) {
        Identifier id = Identifier.of(MOD_ID, path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    @Override
    public void onInitialize() {
        AitMoreConfigLoader.load();
        AitMoreCommands.register();
        ParticleKeyframeLoader.register();
        AitMoreLightBlocks.register();
        dev.aitmore.light.LampFlashFx.init();
        registerAdvancedMode();
    }

    private static void registerAdvancedMode() {
        AdvancedModeState.load();

        ServerPlayNetworking.registerGlobalReceiver(AitMoreNetworking.ADVANCED_MODE_SET_PACKET, (server, player, handler, buf, responseSender) -> {
            UUID tardisId = buf.readUuid();
            boolean enabled = buf.readBoolean();
            server.execute(() -> {
                AdvancedModeState.setEnabled(tardisId, enabled);
                AitMoreNetworking.sendAdvancedModeState(player, tardisId);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AitMoreNetworking.ADVANCED_MODE_REQUEST_STATE_PACKET, (server, player, handler, buf, responseSender) -> {
            UUID tardisId = buf.readUuid();
            server.execute(() -> AitMoreNetworking.sendAdvancedModeState(player, tardisId));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> AdvancedModeManager.onPlayerDisconnect(handler.getPlayer().getUuid()));
    }
}