package com.peredereevin.messengerservice.service;

import com.peredereevin.messengerservice.domain.InboundMessage;
import com.peredereevin.messengerservice.domain.Platform;
import com.peredereevin.messengerservice.domain.UserMessengerConnection;
import com.peredereevin.messengerservice.dto.MessageDto;
import com.peredereevin.messengerservice.dto.attachment.Attachment;
import com.peredereevin.messengerservice.dto.attachment.DocAttachment;
import com.peredereevin.messengerservice.dto.attachment.PhotoAttachment;
import com.peredereevin.messengerservice.exception.InvalidTokenException;
import com.peredereevin.messengerservice.exception.TooManyRequestsException;
import com.peredereevin.messengerservice.exception.VkAttachmentUploadException;
import com.peredereevin.messengerservice.repository.InboundMessageRepository;
import com.peredereevin.messengerservice.repository.UserMessengerConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessageService {
    private final Map<String, IMessengerClient> clients;
    private final InboundMessageRepository messageRepository;
    private final UserMessengerConnectionRepository connectionRepository;
    private final EncryptionService encryptionService;
    private final RateLimiterService rateLimiter;
    private final VkApiClientImpl vkClient;

    public MessageService(Map<String, IMessengerClient> clients,
                          InboundMessageRepository messageRepository,
                          UserMessengerConnectionRepository connectionRepository,
                          EncryptionService encryptionService,
                          RateLimiterService rateLimiter) {
        this.clients = clients;
        this.messageRepository = messageRepository;
        this.connectionRepository = connectionRepository;
        this.encryptionService = encryptionService;
        this.rateLimiter = rateLimiter;
        this.vkClient = (VkApiClientImpl) clients.get("vk"); // извлекаем конкретный бин
    }

    @Transactional
    public List<MessageDto> getConversations(String userId, Platform platform, int count) {
        IMessengerClient client = getClient(platform);
        UserMessengerConnection conn = connectionRepository
                .findByUserIdAndPlatform(userId, platform)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не подключил платформу " + platform));

        String accessToken = encryptionService.decrypt(conn.getAccessToken());

        try {
            List<MessageDto> dtos = client.getConversations(conn.getPlatformUserId(), count, accessToken);
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
        } catch (InvalidTokenException e) {
            connectionRepository.delete(conn);
            throw e;
        }
    }

    @Transactional
    public List<MessageDto> getMessages(String userId, Platform platform, String conversationId, int count) {
        IMessengerClient client = getClient(platform);
        UserMessengerConnection conn = connectionRepository
                .findByUserIdAndPlatform(userId, platform)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не подключил платформу " + platform));

        String accessToken = encryptionService.decrypt(conn.getAccessToken());

        try {
            return client.getMessages(conn.getPlatformUserId(), conversationId, count, accessToken);
        } catch (InvalidTokenException e) {
            connectionRepository.delete(conn);
            throw e;
        }
    }

    public void addConnection(String userId, Platform platform, String platformUserId, String accessToken) {
        connectionRepository.deleteByUserIdAndPlatform(userId, platform);
        String encryptedToken = encryptionService.encrypt(accessToken);
        UserMessengerConnection conn = UserMessengerConnection.builder()
                .userId(userId)
                .platform(platform)
                .platformUserId(platformUserId)
                .accessToken(encryptedToken)
                .build();
        connectionRepository.save(conn);
    }

    public String sendMessage(String userId, Platform platform, String recipientId, String text) {
        String key = userId + ":" + platform.name();
        if (!rateLimiter.allow(key)) {
            throw new TooManyRequestsException("Слишком частые сообщения. Подождите немного.");
        }

        IMessengerClient client = getClient(platform);
        UserMessengerConnection conn = connectionRepository
                .findByUserIdAndPlatform(userId, platform)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не подключил платформу " + platform));

        String accessToken = encryptionService.decrypt(conn.getAccessToken());

        try {
            return client.sendMessage(conn.getPlatformUserId(), recipientId, text, accessToken);
        } catch (InvalidTokenException e) {
            connectionRepository.delete(conn);
            throw e;
        }
    }

    private IMessengerClient getClient(Platform platform) {
        String beanName = platform.name().toLowerCase();
        IMessengerClient client = clients.get(beanName);
        if (client == null) {
            throw new IllegalArgumentException("Нет клиента для платформы: " + platform);
        }
        return client;
    }

    // Приватный метод для получения токена (вынесем, чтобы не дублировать)
    private UserMessengerConnection getConnection(String userId, Platform platform) {
        return connectionRepository
                .findByUserIdAndPlatform(userId, platform)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не подключил платформу " + platform));
    }

    private String getAccessToken(String userId, Platform platform) {
        UserMessengerConnection conn = getConnection(userId, platform);
        return encryptionService.decrypt(conn.getAccessToken());
    }

    // Загрузка фото
    public PhotoAttachment uploadPhoto(String userId, Platform platform, File file) throws VkAttachmentUploadException {
        String token = getAccessToken(userId, platform);
        return vkClient.uploadPhoto(file, token);
    }

    // Загрузка документа
    public DocAttachment uploadDoc(String userId, Platform platform, File file) throws VkAttachmentUploadException {
        String token = getAccessToken(userId, platform);
        return vkClient.uploadDoc(file, token);
    }

    // Отправка сообщения с уже загруженными вложениями
    public String sendMessageWithAttachments(String userId, Platform platform,
                                             String recipientId, String text,
                                             List<Attachment> attachments) {
        // Проверка rate limiter (как в оригинальном sendMessage)
        String key = userId + ":" + platform.name();
        if (!rateLimiter.allow(key)) {
            throw new TooManyRequestsException("Слишком частые сообщения. Подождите немного.");
        }

        String token = getAccessToken(userId, platform);
        // platformUserId не используется в клиенте, передаём null
        return vkClient.sendMessageWithAttachments(null, recipientId, text, attachments, token);
    }

    // Удобный метод: загружает файл и отправляет сообщение с ним
    public String sendMessageWithFile(String userId, Platform platform,
                                      String recipientId, String text,
                                      MultipartFile file) throws IOException, VkAttachmentUploadException {
        // Сохраняем во временный файл
        File tempFile = File.createTempFile("vk_upload_", "_" + file.getOriginalFilename());
        file.transferTo(tempFile);
        try {
            List<Attachment> attachments = new ArrayList<>();
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                PhotoAttachment photo = uploadPhoto(userId, platform, tempFile);
                attachments.add(photo);
            } else {
                DocAttachment doc = uploadDoc(userId, platform, tempFile);
                attachments.add(doc);
            }
            return sendMessageWithAttachments(userId, platform, recipientId, text, attachments);
        } finally {
            tempFile.delete();
        }
    }

    public String getDecryptedToken(String userId, Platform platform) {
        UserMessengerConnection conn = connectionRepository
                .findByUserIdAndPlatform(userId, platform)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Пользователь не подключил платформу " + platform));
        return encryptionService.decrypt(conn.getAccessToken());
    }
}