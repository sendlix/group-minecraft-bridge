package com.sendlix.group.mc.api;

import com.sendlix.api.v1.EmailDataProto;
import com.sendlix.api.v1.EmailGrpc;
import com.sendlix.api.v1.EmailProto;
import com.sendlix.group.mc.config.EmailTemplateRepository;
import com.sendlix.group.mc.core.SendlixPlugin;
import com.sendlix.group.mc.utils.MessageSender;
import com.sendlix.group.mc.utils.Status;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.concurrent.ExecutorService;

public class EmailService {

    private final
    EmailProto.AdditionalInfos additionalInfos = EmailProto.AdditionalInfos.newBuilder()
            .setCategory("MC Email Verification")
            .build();

    private final EmailDataProto.EmailData form;
    private final ExecutorService executorService ;
    private final EmailGrpc.EmailFutureStub  stub;

    public EmailService(SendlixPlugin plugin, ExecutorService executorService) {
        this.form = EmailDataProto.EmailData.newBuilder()
                .setEmail(plugin.getConfig().getEmailFrom())
                .build();
        this.executorService = executorService;
        stub =  EmailGrpc.newFutureStub(Channel.getChannel()).withInterceptors(plugin.getAccessToken().getInterceptor());

    }

    public void sendVerificationEmail(ProxiedPlayer player, String email, String code, MessageSender sender) {

        EmailDataProto.EmailData emailData = EmailDataProto.EmailData.newBuilder()
                .setEmail(email)
                .build();

        EmailTemplateRepository.EmailPair emailPair = EmailTemplateRepository.getInstance().getVerificationEmail();

        EmailProto.MailContent mailContent = EmailProto.MailContent.newBuilder()
                .setText(emailPair.getText().replace("{{username}}", player.getName()).replace("{{code}}", code))
                .setHtml(emailPair.getHtml().replace("{{username}}", player.getName()).replace("{{code}}", code))
                .setTracking(true)
                .build();

        EmailProto.SendMailRequest request = EmailProto.SendMailRequest.newBuilder()
                .addTo(emailData)
                .setSubject("Newsletter Verification")
                .setTextContent(mailContent)
                .setFrom(form)
                .setAdditionalInfos(additionalInfos)
                .build();

        stub.sendEmail(request).addListener(() -> {
            Status.EMAIL_VERIFICATION_SENT.sendStatusData(player);
            sender.sendMessage(new ComponentBuilder().append("A verification email has been sent to your address. Please check your inbox.").color(ChatColor.GREEN).create());
        }, executorService);
    }
}
