package com.sendlix.group.mc.commands;

import com.sendlix.group.mc.api.EmailService;
import com.sendlix.group.mc.api.GroupService;
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
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Command for subscribing players to the Sendlix newsletter.
 * Handles email validation, rate limiting, and user feedback.
 * <p>
 * Usage: /newsletter <email> [--silent] [--agree-privacy]
 * </p>
 */

public class NewsletterCommand extends Command implements Listener {

    private static final String COMMAND_NAME = "newsletter";
    private static final String PERMISSION = "sendlix.newsletter.add";
    private static final String SILENT_FLAG = "--silent";
    private static final String PRIVACY_FLAG = "--agree-privacy";
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);


    private static final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "Newsletter-Async-" + System.currentTimeMillis());
        thread.setDaemon(true);
        return thread;
    });


    private final SendlixPlugin plugin;
    private static RateLimiter rateLimiter;

    private final GroupService groupService;
    private final EmailService emailService ;
    // Store verification codes for players
    private final HashMap<String, VerificationCode> verificationCodes = new HashMap<>();

    /**
     * Creates a new NewsletterCommand instance.
     *
     * @param plugin The SendlixPlugin instance
     * @throws IllegalArgumentException if plugin is null
     */
    public NewsletterCommand(@Nonnull SendlixPlugin plugin) {
        super(COMMAND_NAME);
        this.plugin = plugin;

        this.groupService = new GroupService(plugin, executorService);
        if(plugin.getConfig().isEmailValidationEnabled())
           this.emailService = new EmailService(plugin, executorService);
        else
            this.emailService = null;

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
     * args=  ["-c", "{{code}}"]
     * </pre>
     *
     * @param commandSender The sender of the command, must be a ProxiedPlayer
     * @param args          The command arguments: email address and optional flags
     */
    @Override
    public void execute(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof ProxiedPlayer)) {
            sendErrorMessage(commandSender, "✗ This command can only be used by players in-game.");
            return;
        }

        if(!commandSender.hasPermission(PERMISSION)) {
            commandSender.sendMessage(new ComponentBuilder("✗ ").color(ChatColor.RED).bold(true)
                    .append("Access Denied", ComponentBuilder.FormatRetention.NONE).color(ChatColor.RED).bold(false)
                    .append(" - You don't have permission to use this command.").color(ChatColor.GRAY).create());
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) commandSender;


        boolean hasPrivacyPolicyUrl = plugin.getConfig().getPrivacyPolicyUrl() != null && !plugin.getConfig().getPrivacyPolicyUrl().isEmpty();

        if (args.length == 0) {
            sendErrorMessage(player, "✗ Invalid usage. Please use: /newsletter <email>" + (hasPrivacyPolicyUrl ? " [--agree-privacy] " : ""));
            return;
        }

        if("-c".equalsIgnoreCase(args[0]) && args.length > 1)
            handleVerificationCode(commandSender, args[1], player);
        else handleNewsletterSubscription(commandSender, args, player, hasPrivacyPolicyUrl);
    }

    private void handleNewsletterSubscription(CommandSender commandSender, String[] args, ProxiedPlayer player, boolean hasPrivacyPolicyUrl) {
        String userId = player.getUniqueId().toString();
        boolean silentMode = false;
        boolean privacyAgreed = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase(SILENT_FLAG)) {
                silentMode = true;
            } else if (arg.equalsIgnoreCase(PRIVACY_FLAG)) {
                privacyAgreed = true;
            }
        }

        if(!privacyAgreed && hasPrivacyPolicyUrl) {
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
            Status.EMAIL_NOT_ADDED.sendStatusData(player);
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
        rateLimiter.recordApiCall(player.getUniqueId().toString());

        messageSender.sendMessage(new ComponentBuilder("📧 ").color(ChatColor.AQUA).bold(true)
                .append("Newsletter Subscription", ComponentBuilder.FormatRetention.NONE).color(ChatColor.AQUA).bold(false)
                .append(" - Processing your subscription...").color(ChatColor.GRAY));


        if(emailService != null) {
            String verificationCode;

            do {
                verificationCode = String.valueOf((int) (Math.random() * 90000) + 10000); // Generate a 5-digit code
            } while (verificationCodes.containsKey(userId) && verificationCodes.get(userId).isExpired());

            emailService.sendVerificationEmail(player, email, verificationCode, messageSender);
            verificationCodes.put(userId, new VerificationCode(verificationCode, System.currentTimeMillis(), silentMode, email));

            if(verificationCodes.size() > 1E4) {
                cleanupVerificationCodes();
            }

            return;
        }

        groupService.subscribeToNewsletter(player, email, messageSender);
    }

    private void handleVerificationCode(CommandSender commandSender, String code, ProxiedPlayer player) {
        String userId = player.getUniqueId().toString();

        VerificationCode verificationCode = verificationCodes.get(userId);
        if (verificationCode != null && verificationCode.getCode().equals(code) && verificationCode.isExpired()) {
            // Code is valid, proceed with subscription
            MessageSender messageSender = new MessageSender(verificationCode.isSilentMode(), commandSender);

            messageSender.sendMessage(new ComponentBuilder("📧 ").color(ChatColor.AQUA).bold(true)
                    .append("Newsletter Subscription", ComponentBuilder.FormatRetention.NONE).color(ChatColor.AQUA).bold(false)
                    .append(" - Processing your subscription...").color(ChatColor.GRAY));


            groupService.subscribeToNewsletter(player, verificationCode.getEmail(), messageSender);
            verificationCodes.remove(userId); // Remove code after successful use
            Status.EMAIL_ADDED.sendStatusData(player);
        } else {
            sendErrorMessage(player, "✗ Invalid or expired verification code.");
            Status.EMAIL_VERIFICATION_FAILED.sendStatusData(player);
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
    @EventHandler
    public void onServerConnected(PluginMessageEvent messageEvent) {
       if(messageEvent.getTag().equals("sendlix:newsletter") && messageEvent.getReceiver() instanceof ProxiedPlayer) {
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


    public void cleanupVerificationCodes() {
        long currentTime = System.currentTimeMillis();
        verificationCodes.entrySet().removeIf(entry -> entry.getValue().getTimestamp() + VerificationCode.EXPIRATION_TIME_MILLIS < currentTime); // Remove codes older than 1 hour
    }

    private static class VerificationCode {
        private final String code;
        private final long timestamp;
        private final boolean silentMode;
        private final String email;
        public static final long EXPIRATION_TIME_MILLIS = 60 * 60 * 1000; // 60 minutes

        public VerificationCode(String code, long timestamp, boolean silentMode, String email) {
            this.code = code;
            this.timestamp = timestamp;
            this.silentMode = silentMode;
            this.email = email;
        }
        public String getCode() {
            return code;
        }
        public long getTimestamp() {
            return timestamp;
        }
        public boolean isSilentMode() {
            return silentMode;
        }

        public String getEmail() {
            return email;
        }

        public boolean isExpired() {
            return !isExpired(EXPIRATION_TIME_MILLIS); // Default 1 hour expiration
        }

        public  boolean isExpired(long expirationTimeMillis) {
            return System.currentTimeMillis() - timestamp > expirationTimeMillis;
        }

    }
}
