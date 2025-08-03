package com.sendlix.group.mc.api;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.sendlix.api.v1.EmailDataProto;
import com.sendlix.api.v1.EmailProto;
import com.sendlix.api.v1.GroupGrpc;
import com.sendlix.api.v1.GroupProto;

import com.sendlix.group.mc.core.SendlixPlugin;
import com.sendlix.group.mc.utils.MessageSender;
import com.sendlix.group.mc.utils.Status;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import javax.annotation.Nonnull;
import java.util.concurrent.ExecutorService;

public class GroupService {

    private static final String MC_USERNAME_SUBSTITUTION = "{{mc_username}}";
    private final SendlixPlugin plugin;
    private final ExecutorService executorService ;
    private final GroupGrpc.GroupFutureStub stub;

    public GroupService(SendlixPlugin plugin, ExecutorService executorService) {
        this.plugin = plugin;
        this.executorService = executorService;
        this.stub =  GroupGrpc
                .newFutureStub(Channel.getChannel())
                .withInterceptors(plugin.getAccessToken().getInterceptor());
    }


    /**
     * Handles the asynchronous newsletter subscription process.
     *
     * @param player        The player subscribing
     * @param email         The email address to subscribe
     * @param messageSender The message sender for user feedback
     */
    public void subscribeToNewsletter(ProxiedPlayer player, String email, MessageSender messageSender) {
        try {


            EmailDataProto.EmailData emailData = EmailDataProto.EmailData.newBuilder()
                    .setEmail(email)
                    .build();

            GroupProto.InsertEmailToGroupRequest request = GroupProto.InsertEmailToGroupRequest.newBuilder()
                    .setGroupId(plugin.getGroupId())
                    .putSubstitutions(MC_USERNAME_SUBSTITUTION, player.getName())
                    .addEmails(emailData)
                    .build();

            // Record the API call since it was actually made

            ListenableFuture<GroupProto.UpdateResponse> future = stub.insertEmailToGroup(request);

            Futures.addCallback(future, new GroupService.SubscriptionCallback(player, messageSender), executorService);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initiate newsletter subscription: " + e.getMessage());
            messageSender.sendMessage(new ComponentBuilder("An unexpected error occurred. Please try again later.").color(ChatColor.RED));
           Status.EMAIL_NOT_ADDED.  sendStatusData(player);
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
                Status.EMAIL_ADDED.sendStatusData(player );
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
                Status.EMAIL_ALREADY_EXISTS.   sendStatusData(player);
            } else {
                Status.EMAIL_NOT_ADDED. sendStatusData(player);
                messageSender.sendMessage(new ComponentBuilder("✗ ").color(ChatColor.RED).bold(true)
                        .append("Subscription Failed", ComponentBuilder.FormatRetention.NONE).color(ChatColor.RED).bold(false)
                        .append("\n\nUnable to subscribe to the newsletter.").color(ChatColor.WHITE)
                        .append("\nThis could be due to an invalid email or server issue.").color(ChatColor.GRAY)
                        .append("\nPlease try again later.").color(ChatColor.GRAY));
            }
        }
    }
}
