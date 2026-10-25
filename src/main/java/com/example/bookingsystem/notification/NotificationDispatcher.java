package com.example.bookingsystem.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private final NotificationRepository repository;
    private final NotificationGateway gateway;

    public NotificationDispatcher(NotificationRepository repository, NotificationGateway gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    public boolean dispatchNext() {
        if (
            TransactionSynchronizationManager.isActualTransactionActive()
        ) throw new IllegalStateException("Notifications must run outside a database transaction");
        var next = repository.claim();
        if (next.isEmpty()) return false;
        var notification = next.get();
        if (gateway.send(notification)) {
            repository.delivered(notification.id());
            log.info(
                "notification_delivered eventId={} type={}",
                notification.id(),
                notification.type()
            );
        } else {
            repository.defer(notification.id());
            log.warn("notification_deferred eventId={} retryInSeconds=30", notification.id());
        }
        return true;
    }
}
