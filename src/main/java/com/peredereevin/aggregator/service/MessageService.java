package com.peredereevin.aggregator.service;

import com.peredereevin.aggregator.domain.InboundMessage;
import com.peredereevin.aggregator.domain.Platform;
import com.peredereevin.aggregator.io.VkMessage;
import com.peredereevin.aggregator.repository.InboundMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {
    private final VkApiClient vkApiClient;   // здесь заглушка
    private final InboundMessageRepository repository;

    @Transactional
    public List<InboundMessage> fetchAndSaveVkMessages(String userId, int count) {
        List<VkMessage> vkMessages = vkApiClient.getConversations(userId, count);
        List<InboundMessage> entities = vkMessages.stream()
                .map(vk -> InboundMessage.builder()
                        .platformMessageId(vk.getId())
                        .platform(Platform.VK)
                        .userId(userId)
                        .senderId(vk.getFromId())
                        .senderName(vk.getFromName())
                        .text(vk.getText())
                        .timestamp(vk.getDate())
                        .fetchedAt(Instant.now())
                        .build())
                .collect(Collectors.toList());
        return repository.saveAll(entities);
    }
}