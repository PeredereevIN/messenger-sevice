package com.peredereevin.aggregator.io;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class MessageResponse {
    private Long id;
    private String platformMessageId;
    private String platform;
    private String senderName;
    private String text;
    private Instant timestamp;
}