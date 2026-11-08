package com.saps.auth.support.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SimpleEventBus implements AuthEventPublisher {

    private final List<AuthEventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(AuthEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void publish(AuthEvent event) {
        for (AuthEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ignored) {
                // a broken listener must never break login or registration
            }
        }
    }
}