package com.intellimove.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class LogNotificationChannel implements NotificationChannel {

    @Override
    public void send(String recipientId, String title, String message, Map<String, String> data) {
        log.info("NOTIFICATION [{}] to={}: title='{}', message='{}'",
                getChannelType(), recipientId, title, message);
    }

    @Override
    public String getChannelType() {
        return "IN_APP";
    }
}
