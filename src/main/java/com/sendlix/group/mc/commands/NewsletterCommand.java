package com.sendlix.group.mc.commands;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.sendlix.api.v1.EmailProto;
import com.sendlix.api.v1.GroupGrpc;
import com.sendlix.api.v1.GroupProto;
import com.sendlix.group.mc.api.Channel;
import com.sendlix.group.mc.core.SendlixPlugin;

import com.sendlix.group.mc.utils.MessageSender;
import com.sendlix.group.mc.utils.Status;
import com.sendlix.group.mc.utils.RateLimiter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import javax.annotation.Nonnull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Command handler for newsletter subscription functionality.
 * Allows players to subscribe to newsletters with email validation and async processing.
 *
 * <h3>Command Usage:</h3>
 * <pre>
 * /newsletter &lt;email&gt; [--agree-privacy] [--silent]
 * </pre>
 *
 * <h4>Command Arguments:</h4>
 * <ul>
 *   <li><b>&lt;email&gt;</b> - Required. Valid email address (e.g., user@example.com)</li>
 *   <li><b>--agree-privacy</b> - Optional. Agrees to privacy policy (required if privacy policy URL is configured)</li>
 *   <li><b>--silent</b> - Optional. Suppresses success/info messages, only shows errors</li>
 * </ul>
 *
 * <h4>Command Examples:</h4>
 * <pre>
 * /newsletter john@example.com
 * /newsletter jane@domain.com --agree-privacy
 * /newsletter user@test.com --silent --agree-privacy
 * /newsletter admin@server.com --silent
 * </pre>
 *
 * <h3>Plugin Message Communication System:</h3>
 * This plugin communicates with backend servers using plugin messages on channel "sendlix:newsletter".
 *
 * <h4>Outgoing Messages (BungeeCord → Backend Server):</h4>
 * Messages are sent via {@link #sendStatusData(ProxiedPlayer, Status)} to inform backend servers
 * about newsletter subscription status changes.
 *
 * <h5>Message Format:</h5>
 * <pre>
 * Channel: "sendlix:newsletter"
 * Data: Status enum byte array (Status.getBytes())
 * Target: Player's current backend server
 * </pre>
 *
 * <h5>Status Values Sent to Backend Servers:</h5>
 * <ul>
 *   <li><b>EMAIL_ADDED</b> - Email successfully added to newsletter</li>
 *   <li><b>EMAIL_NOT_ADDED</b> - Email could not be added (validation failed, API error, etc.)</li>
 *   <li><b>EMAIL_ALREADY_EXISTS</b> - Email is already subscribed to newsletter</li>
 * </ul>
 *
 * <h4>Incoming Messages (Backend Server → BungeeCord):</h4>
 * Backend servers can trigger newsletter commands by sending plugin messages.
 * Handled by {@link #onServerConnected(PluginMessageEvent)}.
 *
 * <h5>Message Format:</h5>
 * <pre>
 * Channel: "sendlix:newsletter"
 * Data: Command arguments as space-separated string
 * Example: "user@example.com --agree-privacy --silent"
 * </pre>
 *
 *
 * <h3>Permissions:</h3>
 * <ul>
 *   <li><b>sendlix.newsletter.add</b> - Required to use the newsletter command</li>
 * </ul>
 *
 * <h3>Rate Limiting:</h3>
 * Commands are rate-limited per player to prevent spam. The rate limit is configurable
 * in the plugin configuration (default: 5 seconds between API calls).
 *
 * @see Status For available status values and their byte representations
 * @see MessageSender For handling silent mode messaging
 * @see RateLimiter For rate limiting implementation
 */
public class NewsletterCommand extends Command implements Listener {

    private static final String COMMAND_NAME = "newsletter";
    private static final String PERMISSION = "sendlix.newsletter.add";
    private static final String SILENT_FLAG = "--silent";
    private static final String PRIVACY_FLAG = "--agree-privacy";
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);
    private static final String MC_USERNAME_SUBSTITUTION = "{{mc_username}}";

    private final SendlixPlugin plugin;
    private static final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "Newsletter-Async-" + System.currentTimeMillis());
        thread.setDaemon(true);
        return thread;
    });

    private static RateLimiter rateLimiter;

    /**
     * Creates a new NewsletterCommand instance.
     *
     * @param plugin The SendlixPlugin instance
     * @throws IllegalArgumentException if plugin is null
     */
    public NewsletterCommand(@Nonnull SendlixPlugin plugin) {
        super(COMMAND_NAME);
        this.plugin = plugin;

        // Initialize rate limiter with configured rate limit
        if (rateLimiter == null) {
            rateLimiter = new RateLimiter(plugin.getRateLimitSeconds());
            plugin.getLogger().info("Rate limiter initialized with " + plugin.getRateLimitSeconds() + " seconds cooldown");
        }
    }

    /**
     * Executes the newsletter subscription command.
     *
     * <h4>Supported Arguments:</h4>
     * <ul>
     *   <li><b>Position 0 (required):</b> Email address to subscribe</li>
     *   <li><b>--silent:</b> Suppresses success messages, only shows errors</li>
     *   <li><b>--agree-privacy:</b> Agrees to privacy policy (required if configured)</li>
     * </ul>
     *
     * <h4>Argument Examples:</h4>
     * <pre>
     * args = ["user@example.com"]                           // Basic subscription
     * args = ["user@example.com", "--silent"]               // Silent mode
     * args = ["user@example.com", "--agree-privacy"]        // With privacy agreement
     * args = ["user@example.com", "--silent", "--agree-privacy"] // Both flags
     * </pre>
     *
     * @param commandSender The sender of the command, must be a ProxiedPlayer
     * @param args          The command arguments: email address and optional flags
     */
    @Override
    public void execute(CommandSender commandSender, String[] args) {
        if(!commandSender.hasPermission(PERMISSION)) {
            commandSender.sendMessage(new ComponentBuilder("✗ ").color(ChatColor.RED).bold(true)
                    .append("Access Denied", ComponentBuilder.FormatRetention.NONE).color(ChatColor.RED).bold(false)
                    .append(" - You don't have permission to use this command.").color(ChatColor.GRAY).create());
            return;
        }

        if (!(commandSender instanceof ProxiedPlayer)) {
            sendErrorMessage(commandSender, "✗ This command can only be used by players in-game.");
            return;
        }


        ProxiedPlayer player = (ProxiedPlayer) commandSender;
        String userId = player.getUniqueId().toString();

        if (args.length == 0) {
            sendErrorMessage(player, "✗ Invalid usage. Please use: /newsletter <email> [--agree-privacy]");
            return;
        }

        boolean silentMode = false;
        boolean privacyAgreed = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase(SILENT_FLAG)) {
                silentMode = true;
            } else if (arg.equalsIgnoreCase(PRIVACY_FLAG)) {
                privacyAgreed = true;
            }
        }

        if(!privacyAgreed && plugin.getConfig().getPrivacyPolicyUrl() != null && !plugin.getConfig().getPrivacyPolicyUrl().isEmpty()) {
            ComponentBuilder builder = new ComponentBuilder()
                    .append("📄 ").color(ChatColor.GOLD).bold(true)
                    .append("Privacy Agreement Required", ComponentBuilder.FormatRetention.NONE).color(ChatColor.GOLD).bold(false)
                    .append("\n\nTo subscribe to our newsletter, you must agree to our ")
                    .color(ChatColor.WHITE)
                    .append("Privacy Policy", ComponentBuilder.FormatRetention.NONE)
                    .underlined(true)
                    .color(ChatColor.AQUA)
                    .event(new ClickEvent(ClickEvent.Action.OPEN_URL, plugin.getConfig().getPrivacyPolicyUrl()))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to view our Privacy Policy")))
                    .append(".\n\n").underlined(false).color(ChatColor.WHITE)
                    .append("[ AGREE & SUBSCRIBE ]", ComponentBuilder.FormatRetention.NONE)
                    .bold(true)
                    .color(ChatColor.GREEN)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/newsletter " + String.join(" " , args) + " --agree-privacy"))
                    .append("\n\nClick the button above to agree and subscribe.").bold(false)
                    .color(ChatColor.GRAY);

            commandSender.sendMessage(builder.create());
            return;
        }


        MessageSender messageSender = new MessageSender(silentMode, commandSender);
        String email = args[0];

        // Check email validity first (no rate limit for invalid emails)
        if (!isValidEmail(email)) {
            messageSender.sendMessage(new ComponentBuilder("✗ ").color(ChatColor.RED).bold(true)
                    .append("Invalid Email", ComponentBuilder.FormatRetention.NONE).color(ChatColor.RED).bold(false)
                    .append(" - Please provide a valid email address (e.g., user@example.com).").color(ChatColor.GRAY));
            sendStatusData(player, Status.EMAIL_NOT_ADDED);
            return;
        }

        // Check rate limit before making API call
        if (!rateLimiter.canMakeApiCall(userId)) {
            long remainingSeconds = rateLimiter.getRemainingCooldownSeconds(userId);
            messageSender.sendMessage(new ComponentBuilder("⏰ ").color(ChatColor.YELLOW).bold(true)
                    .append("Please Wait", ComponentBuilder.FormatRetention.NONE).color(ChatColor.YELLOW).bold(false)
                    .append(" - You're subscribing too quickly. Please wait ").color(ChatColor.GRAY)
                    .append(remainingSeconds + " seconds", ComponentBuilder.FormatRetention.NONE).color(ChatColor.WHITE).bold(true)
                    .append(" and try again.").color(ChatColor.GRAY).bold(false));
            return;
        }

        messageSender.sendMessage(new ComponentBuilder("📧 ").color(ChatColor.AQUA).bold(true)
                .append("Newsletter Subscription", ComponentBuilder.FormatRetention.NONE).color(ChatColor.AQUA).bold(false)
                .append(" - Processing your subscription...").color(ChatColor.GRAY));
        subscribeToNewsletter(player, email, messageSender);
    }

    /**
     * Handles the asynchronous newsletter subscription process.
     *
     * @param player        The player subscribing
     * @param email         The email address to subscribe
     * @param messageSender The message sender for user feedback
     */
    private void subscribeToNewsletter(ProxiedPlayer player, String email, MessageSender messageSender) {
        try {
            GroupGrpc.GroupFutureStub stub = GroupGrpc
                    .newFutureStub(Channel.getChannel())
                    .withInterceptors(plugin.getAccessToken().getInterceptor());

            EmailProto.EmailData emailData = EmailProto.EmailData.newBuilder()
                    .setEmail(email)
                    .build();

            GroupProto.InsertEmailToGroupRequest request = GroupProto.InsertEmailToGroupRequest.newBuilder()
                    .setGroupId(plugin.getGroupId())
                    .putSubstitutions(MC_USERNAME_SUBSTITUTION, player.getName())
                    .addEmails(emailData)
                    .build();

            // Record the API call since it was actually made
            rateLimiter.recordApiCall(player.getUniqueId().toString());
            ListenableFuture<GroupProto.UpdateResponse> future = stub.insertEmailToGroup(request);

            Futures.addCallback(future, new SubscriptionCallback(player, messageSender), executorService);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initiate newsletter subscription: " + e.getMessage());
            messageSender.sendMessage(new ComponentBuilder("An unexpected error occurred. Please try again later.").color(ChatColor.RED));
            sendStatusData(player, Status.EMAIL_NOT_ADDED);
        }
    }

    /**
     * Validates email address format.
     *
     * @param email The email address to validate
     * @return true if email is valid, false otherwise
     */
    private static boolean isValidEmail(String email) {
        return email != null && !email.trim().isEmpty() && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Sends an error message to the command sender.
     *
     * @param sender  The command sender
     * @param message The error message
     */
    private static void sendErrorMessage(CommandSender sender, String message) {
        sender.sendMessage(new ComponentBuilder(message).color(ChatColor.RED).create());
    }

    /**
     * Sends status data to the player's current backend server via plugin messaging.
     * This allows backend servers to be notified of newsletter subscription status changes
     * and react accordingly (e.g., update player data, trigger events, show custom messages).
     *
     * <p>The status is sent as a byte array over the "sendlix:newsletter" plugin message channel.
     * Backend servers should register a plugin message listener for this channel to receive updates.</p>
     *
     * <h4>Status Types Sent:</h4>
     * <ul>
     *   <li><b>EMAIL_ADDED</b> - Newsletter subscription was successful</li>
     *   <li><b>EMAIL_NOT_ADDED</b> - Newsletter subscription failed (invalid email, API error, etc.)</li>
     *   <li><b>EMAIL_ALREADY_EXISTS</b> - Player is already subscribed to the newsletter</li>
     * </ul>
     *
     * <h4>Message Flow:</h4>
     * <pre>
     * 1. Player executes /newsletter command on BungeeCord
     * 2. BungeeCord processes subscription request
     * 3. sendStatusData() sends result to player's backend server
     * 4. Backend server receives plugin message with status
     * 5. Backend server can react to the status (show messages, update data, etc.)
     * </pre>
     *
     *
     * @param player The player whose subscription status changed
     * @param status The newsletter subscription status to send to the backend server
     *
     * @see Status#getBytes() For status byte array format
     * @see #onServerConnected(PluginMessageEvent) For handling incoming plugin messages
     */
    private static void sendStatusData(ProxiedPlayer player, Status status) {
        if (player.getServer() != null && player.getServer().getInfo() != null) {
            player.getServer().getInfo().sendData("sendlix:newsletter", status.getBytes());
        }
    }

    /**
     * Callback handler for subscription API responses.
     */
    private static class SubscriptionCallback implements FutureCallback<GroupProto.UpdateResponse> {
        private final ProxiedPlayer player;
        private final MessageSender messageSender;

        public SubscriptionCallback(ProxiedPlayer player, MessageSender messageSender) {
            this.player = player;
            this.messageSender = messageSender;
        }

        @Override
        public void onSuccess(GroupProto.UpdateResponse result) {
            if (result.getSuccess()) {
                sendStatusData(player, Status.EMAIL_ADDED);
                messageSender.sendMessage(new ComponentBuilder("✓ ").color(ChatColor.GREEN).bold(true)
                        .append("Subscription Successful!", ComponentBuilder.FormatRetention.NONE).color(ChatColor.GREEN).bold(false)
                        .append("\n\nYou have successfully subscribed to our newsletter.").color(ChatColor.WHITE));
            } else {
                onFailure(new RuntimeException("Subscription failed: " + result.getMessage()));
            }
        }

        @Override
        public void onFailure(@Nonnull Throwable throwable) {
            io.grpc.Status status = io.grpc.Status.fromThrowable(throwable);

            if (status.getCode() == io.grpc.Status.Code.ALREADY_EXISTS) {
                messageSender.sendMessage(new ComponentBuilder("ℹ ").color(ChatColor.YELLOW).bold(true)
                        .append("Already Subscribed", ComponentBuilder.FormatRetention.NONE).color(ChatColor.YELLOW).bold(false)
                        .append(" - You're already subscribed to our newsletter!").color(ChatColor.GRAY));
                sendStatusData(player, Status.EMAIL_ALREADY_EXISTS);
            } else {
                sendStatusData(player, Status.EMAIL_NOT_ADDED);
                messageSender.sendMessage(new ComponentBuilder("✗ ").color(ChatColor.RED).bold(true)
                        .append("Subscription Failed", ComponentBuilder.FormatRetention.NONE).color(ChatColor.RED).bold(false)
                        .append("\n\nUnable to subscribe to the newsletter.").color(ChatColor.WHITE)
                        .append("\nThis could be due to an invalid email or server issue.").color(ChatColor.GRAY)
                        .append("\nPlease try again later.").color(ChatColor.GRAY));
            }
        }

    }


    @EventHandler
    public void onServerConnected(PluginMessageEvent messageEvent) {
        plugin.getLogger().info("Received plugin message: " + messageEvent.getTag() + " from " + messageEvent.getReceiver());
       if( messageEvent.getTag().equals("sendlix:newsletter") && messageEvent.getReceiver() instanceof ProxiedPlayer) {
            ProxiedPlayer player = (ProxiedPlayer) messageEvent.getReceiver();
            byte[] data = messageEvent.getData();
            String message = new String(data);
            String[] parts = message.split(" ");
            execute(player, parts);
        }
    }
    /**
     * Shuts down the executor service and rate limiter.
     * Should be called during plugin shutdown.
     */
    public static void shutdown() {
        executorService.shutdown();
        if(rateLimiter != null) {
            rateLimiter.shutdown();
        }
    }





}
