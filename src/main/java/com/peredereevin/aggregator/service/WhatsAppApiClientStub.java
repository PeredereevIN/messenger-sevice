package com.peredereevin.aggregator.service;

import com.peredereevin.aggregator.dto.MessageDto;
import com.peredereevin.aggregator.service.IMessengerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service("whatsapp")
@Slf4j
public class WhatsAppApiClientStub implements IMessengerClient {

    @Override
    public List<MessageDto> getConversations(String platformUserId, int count, String accessToken) {
        log.info("WhatsApp stub: returning fake conversations for user {}", platformUserId);
        return List.of(
                MessageDto.builder()
                        .platformMessageId("wa_msg_1")
                        .conversationId("wa_conv_1")
                        .senderId("wa_sender_1")
                        .senderName("John Doe (WhatsApp)")
                        .text("Hello from WhatsApp!")
                        .timestamp(Instant.now().minusSeconds(3600))
                        .build()
        );
    }

    @Override
    public List<MessageDto> getMessages(String platformUserId, String conversationId, int count, String accessToken) {
        log.info("WhatsApp stub: returning fake messages for conversation {}", conversationId);
        return List.of(
                MessageDto.builder()
                        .platformMessageId("wa_msg_2")
                        .conversationId(conversationId)
                        .senderId("wa_sender_2")
                        .senderName("Jane Doe (WhatsApp)")
                        .text("Hey there!")
                        .timestamp(Instant.now().minusSeconds(1800))
                        .build()
        );
    }

}
