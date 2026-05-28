package com.peredereevin.aggregator.service;

import com.peredereevin.aggregator.domain.InboundMessage;
import com.peredereevin.aggregator.domain.Platform;
import com.peredereevin.aggregator.dto.MessageDto;
import com.peredereevin.aggregator.repository.InboundMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    private final IMessengerClient vkClient;   // будет внедрён VkApiClientImpl, т.к. это единственная реализация IMessengerClient
    private final InboundMessageRepository messageRepository;

    @Transactional
    public List<MessageDto> getConversations(String platformUserId, int count) {
        List<MessageDto> dtos = vkClient.getConversations(platformUserId, count);
        // сохраняем в БД
        List<InboundMessage> entities = dtos.stream()
                .map(dto -> InboundMessage.builder()
                        .platformMessageId(dto.getPlatformMessageId())
                        .platform(determinePlatform(dto))   // нужен метод определения платформы, пока захардкодим VK
                        .userId(platformUserId)             // пока platformUserId – это email из JWT, временно
                        .senderId(dto.getSenderId())
                        .senderName(dto.getSenderName())
                        .text(dto.getText())
                        .timestamp(dto.getTimestamp())
                        .fetchedAt(Instant.now())
                        .build())
                .collect(Collectors.toList());
        messageRepository.saveAll(entities);
        return dtos;
    }

    public List<MessageDto> getMessages(String platformUserId, String conversationId, int count) {
        return vkClient.getMessages(platformUserId, conversationId, count);
    }

    private Platform determinePlatform(MessageDto dto) {
        // Заглушка: всегда VK, т.к. мы знаем, что сейчас только VK
        return Platform.VK;
    }
}