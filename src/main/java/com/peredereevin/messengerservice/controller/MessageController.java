package com.peredereevin.messengerservice.controller;

import com.peredereevin.messengerservice.domain.Platform;
import com.peredereevin.messengerservice.dto.MessageDto;
import com.peredereevin.messengerservice.dto.attachment.Attachment;
import com.peredereevin.messengerservice.dto.attachment.DocAttachment;
import com.peredereevin.messengerservice.dto.attachment.PhotoAttachment;
import com.peredereevin.messengerservice.exception.InvalidTokenException;
import com.peredereevin.messengerservice.exception.TooManyRequestsException;
import com.peredereevin.messengerservice.exception.VkAttachmentUploadException;
import com.peredereevin.messengerservice.service.MessageService;
import com.peredereevin.messengerservice.service.VkApiClientImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final VkApiClientImpl vkApiClient;

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

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@AuthenticationPrincipal Jwt jwt,
                                         @RequestBody Map<String, String> body) {
        String userId = jwt.getSubject();
        Platform platform = Platform.valueOf(body.get("platform").toUpperCase());
        String recipientId = body.get("recipientId");
        String text = body.get("text");

        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Текст сообщения не может быть пустым"));
        }

        try {
            String result = messageService.sendMessage(userId, platform, recipientId, text);
            return ResponseEntity.ok(Map.of("messageId", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Загружает фото на сервер VK и возвращает объект PhotoAttachment.
     * POST /upload/photo?platform=VK
     * Body: multipart/form-data с полем file
     */
    @PostMapping("/upload/photo")
    public ResponseEntity<?> uploadPhoto(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam Platform platform) {
        String userId = jwt.getSubject();
        try {
            File tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
            file.transferTo(tempFile);
            PhotoAttachment photo = messageService.uploadPhoto(userId, platform, tempFile);
            tempFile.delete();
            return ResponseEntity.ok(photo);
        } catch (VkAttachmentUploadException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    /**
     * Загружает документ на сервер VK и возвращает DocAttachment.
     * POST /upload/doc?platform=VK
     * Body: multipart/form-data с полем file
     */
    @PostMapping("/upload/doc")
    public ResponseEntity<?> uploadDoc(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam Platform platform) {
        String userId = jwt.getSubject();
        try {
            // Тот же токен, что и в sendMessage
            String token = messageService.getDecryptedToken(userId, platform);
            File tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
            file.transferTo(tempFile);
            DocAttachment doc = vkApiClient.uploadDoc(tempFile, token);
            tempFile.delete();
            return ResponseEntity.ok(doc);
        } catch (VkAttachmentUploadException | IOException e) {
            //log.error("Doc upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Отправляет сообщение с уже загруженными вложениями.
     * POST /send/with-attachments?platform=VK&recipientId=123&text=Hello
     * Body: JSON-массив Attachment (photo/doc/video)
     */
    @PostMapping("/send/with-attachments")
    public ResponseEntity<?> sendWithAttachments(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestParam Platform platform,
                                                 @RequestParam String recipientId,
                                                 @RequestParam(required = false) String text,
                                                 @RequestBody List<Attachment> attachments) {
        String userId = jwt.getSubject();
        try {
            String result = messageService.sendMessageWithAttachments(userId, platform, recipientId, text, attachments);
            return ResponseEntity.ok(Map.of("messageId", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (TooManyRequestsException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Send failed: " + e.getMessage()));
        }
    }

    /**
     * Удобный метод: загружает файл и отправляет сообщение с ним за один вызов.
     * POST /send/with-file?platform=VK&recipientId=123&text=Hello
     * Body: multipart/form-data с полем file
     */
    @PostMapping("/send/with-file")
    public ResponseEntity<?> sendWithFile(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam Platform platform,
                                          @RequestParam String recipientId,
                                          @RequestParam(required = false) String text,
                                          @RequestParam("file") MultipartFile file) {
        String userId = jwt.getSubject();
        try {
            String result = messageService.sendMessageWithFile(userId, platform, recipientId, text, file);
            return ResponseEntity.ok(Map.of("messageId", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (TooManyRequestsException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Send failed: " + e.getMessage()));
        }
    }
}