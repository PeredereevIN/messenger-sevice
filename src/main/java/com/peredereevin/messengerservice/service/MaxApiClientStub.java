package com.peredereevin.messengerservice.service;

import com.peredereevin.messengerservice.dto.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service("max")
@Slf4j
public class MaxApiClientStub implements IMessengerClient {

    @Override
    public List<MessageDto> getConversations(String platformUserId, int count, String accessToken) {
        log.info("Max stub: returning fake conversations for user {}", platformUserId);
        return List.of(
                MessageDto.builder()
                        .platformMessageId("max_msg_1")
                        .conversationId("max_conv_1")
                        .senderId("max_sender_1")
                        .senderName("Charlie (Max)")
                        .text("Hello from Max!")
                        .timestamp(Instant.now().minusSeconds(3600))
                        .build(),
                MessageDto.builder()
                        .platformMessageId("max_msg_2")
                        .conversationId("max_conv_2")
                        .senderId("max_sender_2")
                        .senderName("Diana (Max)")
                        .text("Any updates on the project?")
                        .timestamp(Instant.now().minusSeconds(1800))
                        .build()
        );
    }

    @Override
    public List<MessageDto> getMessages(String platformUserId, String conversationId, int count, String accessToken) {
        log.info("Max stub: returning fake messages for conversation {}", conversationId);
        return List.of(
                MessageDto.builder()
                        .platformMessageId("max_msg_3")
                        .conversationId(conversationId)
                        .senderId("max_sender_1")
                        .senderName("Charlie (Max)")
                        .text("Almost done, just final testing left.")
                        .timestamp(Instant.now().minusSeconds(900))
                        .build(),
                MessageDto.builder()
                        .platformMessageId("max_msg_4")
                        .conversationId(conversationId)
                        .senderId("max_sender_2")
                        .senderName("Diana (Max)")
                        .text("Great, keep me posted!")
                        .timestamp(Instant.now().minusSeconds(600))
                        .build()
        );
    }

    @Override
    public String sendMessage(String platformUserId, String recipientId, String text, String accessToken) {
        log.info("Max stub: pretending to send message to {}: {}", recipientId, text);
        return "max_fake_msg_" + System.currentTimeMillis();
    }
}