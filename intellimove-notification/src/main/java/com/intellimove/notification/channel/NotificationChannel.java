package com.intellimove.notification.channel;

public interface NotificationChannel {
    void send(String recipientId, String title, String message, java.util.Map<String, String> data);
    String getChannelType();
}
