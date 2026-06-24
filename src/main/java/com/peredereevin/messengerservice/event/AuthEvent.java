package com.peredereevin.messengerservice.event;

import lombok.Data;
import java.time.Instant;

@Data
public class AuthEvent {
    private String eventId;
    private String eventType;    // USER_REGISTERED, USER_LOGGED_IN, USER_LOGGED_OUT, PASSWORD_RESET, TOKEN_REFRESHED
    private String email;
    private String role;
    private Instant timestamp;
}