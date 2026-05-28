package com.peredereevin.aggregator.service;

import com.peredereevin.aggregator.domain.InboundMessage;
import com.peredereevin.aggregator.domain.Platform;
import com.peredereevin.aggregator.domain.UserMessengerConnection;
import com.peredereevin.aggregator.dto.MessageDto;
import com.peredereevin.aggregator.repository.InboundMessageRepository;
import com.peredereevin.aggregator.repository.UserMessengerConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessageService {
    private final Map<String, IMessengerClient> clients;
    private final InboundMessageRepository messageRepository;
    private final UserMessengerConnectionRepository connectionRepository;

    public MessageService(Map<String, IMessengerClient> clients,
                          InboundMessageRepository messageRepository,
                          UserMessengerConnectionRepository connectionRepository) {
        this.clients = clients;
        this.messageRepository = messageRepository;
        this.connectionRepository = connectionRepository;
    }

    @Transactional
    public List<MessageDto> getConversations(String userId, Platform platform, int count) {
        IMessengerClient client = getClient(platform);
        UserMessengerConnection conn = connectionRepository
                .findByUserIdAndPlatform(userId, platform)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не подключил платформу " + platform));

        // Теперь передаём токен из подключения
        List<MessageDto> dtos = client.getConversations(conn.getPlatformUserId(), count, conn.getAccessToken());

        List<InboundMessage> entities = dtos.stream()
                .map(dto -> InboundMessage.builder()
                        .platformMessageId(dto.getPlatformMessageId())
                        .platform(platform)
                        .userId(userId)
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

    @Transactional
    public List<MessageDto> getMessages(String userId, Platform platform, String conversationId, int count) {
        IMessengerClient client = getClient(platform);
        UserMessengerConnection conn = connectionRepository
                .findByUserIdAndPlatform(userId, platform)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не подключил платформу " + platform));

        return client.getMessages(conn.getPlatformUserId(), conversationId, count, conn.getAccessToken());
    }

    public void addConnection(String userId, Platform platform, String platformUserId, String accessToken) {
        UserMessengerConnection conn = UserMessengerConnection.builder()
                .userId(userId)
                .platform(platform)
                .platformUserId(platformUserId)
                .accessToken(accessToken)
                .build();
        connectionRepository.save(conn);
    }

    private IMessengerClient getClient(Platform platform) {
        String beanName = platform.name().toLowerCase();
        IMessengerClient client = clients.get(beanName);
        if (client == null) {
            throw new IllegalArgumentException("Нет клиента для платформы: " + platform);
        }
        return client;
    }

    public String sendMessage(String userId, Platform platform, String recipientId, String text) {
        IMessengerClient client = getClient(platform);
        UserMessengerConnection conn = connectionRepository
                .findByUserIdAndPlatform(userId, platform)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не подключил платформу " + platform));
        return client.sendMessage(conn.getPlatformUserId(), recipientId, text, conn.getAccessToken());
    }
}