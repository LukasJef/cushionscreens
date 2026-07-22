package dev.atrixx.cushionscreens;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CushionScreens implements ModInitializer {

    public static final Logger LOG = LoggerFactory.getLogger("CushionScreens");

    @Override
    public void onInitialize() {
        CushionAudioNetworkServer.registerPayloadTypes();

        // Vlastni ArgumentType musi byt zaregistrovany, jinak server neumi
        // popis prikazu poslat klientovi pri pripojeni (ClientboundCommandsPacket)
        // a spojeni spadne s "Invalid player data" hned pri prvnim joinu.
        ArgumentTypeRegistry.registerArgumentType(
            Identifier.fromNamespaceAndPath("cushionscreens", "media_file"),
            CushionMediaFileArgument.class,
            SingletonArgumentInfo.contextFree(CushionMediaFileArgument::file)
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            CushionScreenCommand.register((CommandDispatcher<CommandSourceStack>) dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(CushionScreenCommand::serverTick);

        // Zalozi scoreboard objective pro /execute if score ... - pokud uz
        // existuje, prikaz jen selze (vystup je potlaceny), takze je to
        // bezpecne volat pri kazdem startu serveru.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                server.getCommands().getDispatcher().execute(
                    "scoreboard objectives add " + CushionScreenCommand.SCORE_OBJECTIVE + " dummy",
                    server.createCommandSourceStack().withSuppressedOutput()
                );
            } catch (Throwable ignored) {
                // objective uz nejspis existuje z minula - v poradku
            }
            CushionScreenCommand.onServerStarted(server);
        });
    }
}
