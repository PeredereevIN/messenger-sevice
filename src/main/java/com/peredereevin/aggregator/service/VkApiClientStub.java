package com.peredereevin.aggregator.service;

import com.peredereevin.aggregator.io.VkMessage;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
public class VkApiClientStub implements VkApiClient {
    @Override
    public List<VkMessage> getConversations(String userId, int count) {
        // Возвращаем фейковые сообщения
        return List.of(
                VkMessage.builder()
                        .id("1")
                        .fromId("12345")
                        .fromName("Иван Петров")
                        .text("Привет! Это тестовое сообщение из VK.")
                        .date(Instant.now().minusSeconds(3600))
                        .build(),
                VkMessage.builder()
                        .id("2")
                        .fromId("67890")
                        .fromName("Мария Иванова")
                        .text("Как дела?")
                        .date(Instant.now().minusSeconds(1800))
                        .build()
        );
    }
}