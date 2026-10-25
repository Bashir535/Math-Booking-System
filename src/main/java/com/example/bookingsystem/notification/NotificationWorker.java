package com.example.bookingsystem.notification;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "notification.worker.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class NotificationWorker {

    private final NotificationDispatcher dispatcher;

    public NotificationWorker(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(
        fixedDelayString = "${notification.poll-ms:2000}",
        initialDelayString = "${notification.poll-ms:2000}"
    )
    public void deliver() {
        try {
            dispatcher.dispatchNext();
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(getClass()).error(
                "notification_worker_failed reason={}; pending work will be retried",
                e.getClass().getSimpleName()
            );
        }
    }
}
