package com.peredereevin.messengerservice.dto;

import com.peredereevin.messengerservice.dto.attachment.Attachment;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@ Data
@ Builder
public class MessageDto {
    private String platformMessageId;
    private String conversationId;
    private String senderId;
    private String senderName;
    private String text;
    private Instant timestamp;
    private List<Attachment> attachments;
    private Boolean isOutgoing;
}