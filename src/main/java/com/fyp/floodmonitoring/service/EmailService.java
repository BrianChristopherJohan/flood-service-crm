package com.fyp.floodmonitoring.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails through Resend's HTTP API. SMTP via
 * smtp.resend.com:465 silently failed on Railway, so this class now goes
 * through {@link ResendHttpClient} which posts to https://api.resend.com/emails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailSenderResolver senders;
    private final ResendHttpClient    resend;

    @Value("${app.email.dev-recipient:}")
    private String devRecipient;

    @Value("${app.environment:development}")
    private String environment;

    @Async
    public void sendPasswordResetCode(String toEmail, String code) {
        String subject = "Your Flood Monitor password reset code";
        String body = String.format(
                "Hi,%n%n" +
                "You requested a password reset for your Flood Monitor account.%n%n" +
                "Your verification code is:%n%n" +
                "    %s%n%n" +
                "This code expires in 10 minutes.%n%n" +
                "If you did not request this, you can safely ignore this email.%n%n" +
                "— Flood Monitor Team",
                code);

        resend.sendText(
                senders.headerFor(EmailSenderResolver.PASSWORD_RESET),
                resolveRecipient(toEmail),
                subject,
                body);
    }

    @Async
    public void sendBroadcastAlert(String toEmail, String title, String body) {
        resend.sendText(
                senders.headerFor(EmailSenderResolver.BROADCAST),
                resolveRecipient(toEmail),
                "[Flood Alert] " + title,
                body + "\n\n— Flood Monitor System");
    }

    private String resolveRecipient(String originalEmail) {
        if ("development".equals(environment) && devRecipient != null && !devRecipient.isBlank()) {
            log.info("[Email DEV] Redirecting from {} → {} (dev mode)", originalEmail, devRecipient);
            return devRecipient;
        }
        return originalEmail;
    }
}
