package com.intellimove.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    public EmailNotificationChannel(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(String recipientId, String title, String message, Map<String, String> data) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(recipientId);
            mailMessage.setSubject(title);
            mailMessage.setText(message);
            mailMessage.setFrom("noreply@intellimove.com");
            mailSender.send(mailMessage);
            log.info("Email sent to {}: {}", recipientId, title);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", recipientId, e.getMessage());
            throw new RuntimeException("Email send failed", e);
        }
    }

    @Override
    public String getChannelType() {
        return "EMAIL";
    }
}
