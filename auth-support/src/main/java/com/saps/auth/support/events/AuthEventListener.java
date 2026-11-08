package com.saps.auth.support.events;

@FunctionalInterface
public interface AuthEventListener {

    void onEvent(AuthEvent event);
}