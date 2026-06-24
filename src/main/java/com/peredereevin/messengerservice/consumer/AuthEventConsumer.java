package com.peredereevin.messengerservice.consumer;

import com.peredereevin.messengerservice.event.AuthEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthEventConsumer {

    @ KafkaListener(topics = "auth-events", groupId = "messenger-service")
    public void handleAuthEvent(AuthEvent event) {
        log.info("Received auth event: type={}, email={}", event.getEventType(), event.getEmail());

        switch (event.getEventType()) {
            case "USER_LOGGED_OUT":
                // Здесь можно сбросить кеш подключений пользователя
                // connectionCache.invalidate(event.getEmail());
                log.info("User {} logged out, invalidating caches", event.getEmail());
                break;
            case "PASSWORD_RESET":
                log.info("Password reset for {}, revoking tokens", event.getEmail());
                // В будущем: отозвать все refresh-токены
                break;
            case "USER_REGISTERED":
                log.info("New user registered: {}", event.getEmail());
                // Можно отправить приветственное сообщение
                break;
            default:
                log.debug("Unhandled event type: {}", event.getEventType());
        }
    }
}