package com.peredereevin.aggregator.controller;

import com.peredereevin.aggregator.domain.InboundMessage;
import com.peredereevin.aggregator.dto.MessageDto;
import com.peredereevin.aggregator.dto.MessageResponse;
import com.peredereevin.aggregator.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;

    @GetMapping("/messages/vk")
    public List<MessageDto> getVkMessages(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(defaultValue = "20") int count) {
        String email = jwt.getSubject();   // email
        return messageService.getConversations(email, count);
    }
}