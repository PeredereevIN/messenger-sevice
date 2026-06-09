package com.peredereevin.messengerservice.service;

import com.peredereevin.messengerservice.dto.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service("dion")
@Slf4j
public class DionApiClientStub implements IMessengerClient {

    @ Override
    public List<MessageDto> getConversations(String platformUserId, int count, String accessToken) {
        log.info("Dion stub: returning fake conversations for user {}", platformUserId);
        return List.of(
                MessageDto.builder()
                        .platformMessageId("dion_msg_1")
                        .conversationId("dion_conv_1")
                        .senderId("dion_sender_1")
                        .senderName("Alice (Dion)")
                        .text("Hello from Dion!")
                        .timestamp(Instant.now().minusSeconds(3600))
                        .build(),
                MessageDto.builder()
                        .platformMessageId("dion_msg_2")
                        .conversationId("dion_conv_2")
                        .senderId("dion_sender_2")
                        .senderName("Bob (Dion)")
                        .text("Meeting at 3pm?")
                        .timestamp(Instant.now().minusSeconds(1800))
                        .build()
        );
    }

    @Override
    public List<MessageDto> getMessages(String platformUserId, String conversationId, int count, String accessToken) {
        log.info("Dion stub: returning fake messages for conversation {}", conversationId);
        return List.of(
                MessageDto.builder()
                        .platformMessageId("dion_msg_3")
                        .conversationId(conversationId)
                        .senderId("dion_sender_1")
                        .senderName("Alice (Dion)")
                        .text("Yes, 3pm works for me.")
                        .timestamp(Instant.now().minusSeconds(900))
                        .build(),
                MessageDto.builder()
                        .platformMessageId("dion_msg_4")
                        .conversationId(conversationId)
                        .senderId("dion_sender_2")
                        .senderName("Bob (Dion)")
                        .text("Great, see you then!")
                        .timestamp(Instant.now().minusSeconds(600))
                        .build()
        );
    }

    @Override
    public String sendMessage(String platformUserId, String recipientId, String text, String accessToken) {
        log.info("Dion stub: pretending to send message to {}: {}", recipientId, text);
        return "dion_fake_msg_" + System.currentTimeMillis();
    }
}