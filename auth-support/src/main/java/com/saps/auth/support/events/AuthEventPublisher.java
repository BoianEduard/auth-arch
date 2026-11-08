package com.saps.auth.support.events;

@FunctionalInterface
public interface AuthEventPublisher {

    void publish(AuthEvent event);
}