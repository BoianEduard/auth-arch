package com.saps.auth.api;

public record RequestContext(String clientIp) {

    public static RequestContext unknown() {
        return new RequestContext("unknown");
    }
}