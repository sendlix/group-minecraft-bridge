package com.sendlix.group.mc.utils;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;

/**
 * Utility class for conditional message sending based on silent mode.
 * Allows commands to respect a --silent flag for suppressing user feedback.
 */
public class MessageSender {

    private final boolean silentMode;
    private final CommandSender sender;

    /**
     * Creates a new MessageSender instance.
     *
     * @param silentMode Whether messages should be suppressed
     * @param sender     The command sender to send messages to
     */
    public MessageSender(boolean silentMode, CommandSender sender) {
        this.silentMode = silentMode;
        this.sender = sender;
    }

    /**
     * Sends a message to the command sender if not in silent mode.
     *
     * @param components The message components to send
     */
    public void sendMessage(BaseComponent... components) {
        if (!silentMode && sender != null) {
            sender.sendMessage(components);
        }
    }

    /**
     * Sends a message to the command sender if not in silent mode.
     *
     * @param componentBuilder The message component builder to send
     */
    public void sendMessage(net.md_5.bungee.api.chat.ComponentBuilder componentBuilder) {
        if (!silentMode && sender != null && componentBuilder != null) {
            sender.sendMessage(componentBuilder.create());
        }
    }

    /**
     * Checks if the sender is in silent mode.
     *
     * @return true if in silent mode, false otherwise
     */
    public boolean isSilent() {
        return silentMode;
    }
}
