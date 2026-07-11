package dev.aitmore;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.command.ServerCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;

public class AitMoreCommands {

    private static final Logger AITMORE_LOGGER = LoggerFactory.getLogger("ait-more");

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CommandDispatcher<ServerCommandSource> dispatcher = server.getCommandManager().getDispatcher();
            CommandNode<ServerCommandSource> vanishNode = dispatcher.getRoot().getChild("vanish");

            if (vanishNode == null) {
                AITMORE_LOGGER.warn("[ait-more] Could not find a '/vanish' command to alias - " +
                        "skipping..");
                return;
            }

            dispatcher.register(literal("rosetyler")
                    .requires(source -> source.hasPermissionLevel(2))
                    .redirect(vanishNode));
            AITMORE_LOGGER.info("[ait-more] Registered /rosetyler as an alias for /vanish");
        });
    }
}