package com.example.bookingsystem.notification;

import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class NotificationQueue {

    private final NotificationRepository repository;
    private final ApplicationEventPublisher events;

    public record Committed(long appointmentId, NotificationRequest.Type type) {}

    public NotificationQueue(NotificationRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(long appointment, NotificationRequest.Type type) {
        repository.enqueue(appointment, type);
        events.publishEvent(new Committed(appointment, type));
    }

    @TransactionalEventListener
    public void logCommitted(Committed event) {
        LoggerFactory.getLogger(getClass()).info(
            "appointment_committed appointmentId={} type={}",
            event.appointmentId(),
            event.type()
        );
    }
}
