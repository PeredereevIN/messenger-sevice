package com.peredereevin.aggregator.controller;

import com.peredereevin.aggregator.domain.Platform;
import com.peredereevin.aggregator.dto.MessageDto;
import com.peredereevin.aggregator.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * Получение списка бесед для указанной платформы.
     * Требует предварительного добавления подключения через POST /api/connections.
     */
    @GetMapping("/conversations")
    public List<MessageDto> getConversations(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam Platform platform,
                                             @RequestParam(defaultValue = "20") int count) {
        String userId = jwt.getSubject();
        return messageService.getConversations(userId, platform, count);
    }

    /**
     * Получение истории сообщений конкретной беседы.
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public List<MessageDto> getMessages(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable String conversationId,
                                        @RequestParam Platform platform,
                                        @RequestParam(defaultValue = "20") int count) {
        String userId = jwt.getSubject();
        return messageService.getMessages(userId, platform, conversationId, count);
    }

    /**
     * Добавление подключения к мессенджеру.
     * Пока токен не используется (берётся из конфига), но сохраняется для будущего использования.
     */
    @PostMapping("/connections")
    public ResponseEntity<?> addConnection(@AuthenticationPrincipal Jwt jwt,
                                           @RequestBody Map<String, String> body) {
        String userId = jwt.getSubject();
        Platform platform = Platform.valueOf(body.get("platform").toUpperCase());
        String platformUserId = body.get("platformUserId");
        String accessToken = body.get("accessToken");

        messageService.addConnection(userId, platform, platformUserId, accessToken);
        return ResponseEntity.ok(Map.of("message", "Подключение успешно добавлено"));
    }
}