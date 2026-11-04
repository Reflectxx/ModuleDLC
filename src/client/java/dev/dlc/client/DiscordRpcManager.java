package dev.dlc.client;

import dev.dlc.ModuleDLC;
import dev.firstdark.rpc.DiscordRpc;
import dev.firstdark.rpc.exceptions.PipeAccessDenied;
import dev.firstdark.rpc.exceptions.UnsupportedOsType;
import dev.firstdark.rpc.handlers.RPCEventHandler;
import dev.firstdark.rpc.models.DiscordRichPresence;

public final class DiscordRpcManager {
    private static final String APPLICATION_ID = "1567466330922295306";
    private static final String SITE_URL = "https://reflectxx.github.io";
    private static final String DOCS_URL = "https://reflectxx.github.io/docs";

    private final DiscordRpc rpc = new DiscordRpc();

    public void start() {
        try {
            rpc.init(APPLICATION_ID, new RPCEventHandler() {
                @Override
                public void errored(dev.firstdark.rpc.enums.ErrorCode errorCode, String message) {
                    ModuleDLC.LOGGER.warn("Discord RPC error {}: {}", errorCode, message);
                }
            }, false);
            rpc.updatePresence(DiscordRichPresence.builder()
                    .details("The Open Source Platform for Script testing")
                    .state("In Minecraft")
                    .button(DiscordRichPresence.RPCButton.of("Site", SITE_URL))
                    .button(DiscordRichPresence.RPCButton.of("Docs", DOCS_URL))
                    .build());
        } catch (UnsupportedOsType | PipeAccessDenied e) {
            ModuleDLC.LOGGER.warn("Unable to initialize Discord Rich Presence", e);
        }
    }

    public void stop() {
        rpc.shutdown();
    }
}
