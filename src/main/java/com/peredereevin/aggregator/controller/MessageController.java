package com.peredereevin.aggregator.controller;

import com.peredereevin.aggregator.domain.InboundMessage;
import com.peredereevin.aggregator.io.MessageResponse;
import com.peredereevin.aggregator.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;

    @GetMapping("vk")
    public List<MessageResponse> getVkMessages(@AuthenticationPrincipal Jwt jwt,
                                               @RequestParam(defaultValue = "20") int count) {
        String userId = jwt.getSubject();   // email
        List<InboundMessage> messages = messageService.fetchAndSaveVkMessages(userId, count);
        return messages.stream()
                .map(m -> MessageResponse.builder()
                        .id(m.getId())
                        .platformMessageId(m.getPlatformMessageId())
                        .platform(m.getPlatform().name())
                        .senderName(m.getSenderName())
                        .text(m.getText())
                        .timestamp(m.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }
}