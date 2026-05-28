package com.peredereevin.aggregator.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class MessageDto {
    private String platformMessageId;
    private String conversationId;      // ID беседы (для getMessages)
    private String senderId;
    private String senderName;
    private String text;
    private Instant timestamp;
}