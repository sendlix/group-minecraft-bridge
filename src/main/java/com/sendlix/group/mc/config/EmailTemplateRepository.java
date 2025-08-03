package com.sendlix.group.mc.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

public class EmailTemplateRepository {

    private static EmailTemplateRepository instance;

    private final EmailPair verificationEmail;

    private EmailTemplateRepository(EmailPair verificationEmail) {
        this.verificationEmail = verificationEmail;
    }


    public static void loadEmails(String basePath) throws IOException {
       //Check if email folder exists, if not create it
        File emailFolder = new File(basePath);
        if (!emailFolder.exists()) {
             emailFolder.mkdir();
        }

            String html = LoadAndSaveFile("verification.html", basePath);
            String text = LoadAndSaveFile("verification.txt", basePath);

            EmailPair verificationEmail = new EmailPair(html, text);

            instance = new EmailTemplateRepository(verificationEmail);

    }


    public EmailPair getVerificationEmail() {
        return verificationEmail;
    }


    public static EmailTemplateRepository getInstance() {

        if (instance == null) {
            throw new IllegalStateException("EmailStore is not initialized. Call loadEmails() first.");
        }

        return instance;
    }


    private static String LoadAndSaveFile(String fileName, String basePath) throws IOException {
        File file = new File(basePath + fileName);
        if (!file.exists()) {
                Files.copy(
                        Objects.requireNonNull(EmailTemplateRepository.class.getClassLoader().getResourceAsStream(fileName)),
                        file.toPath()
                );

        }

            return Files.readString(file.toPath());
    }

    public static class EmailPair {
        private final String html;
        private final String text;
        public EmailPair(String html, String text) {
            this.html = html;
            this.text = text;
        }
        public String getHtml() {
            return html;
        }
        public String getText() {
            return text;
        }

    }

}
