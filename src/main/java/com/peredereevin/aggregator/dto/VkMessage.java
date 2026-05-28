package com.peredereevin.aggregator.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class VkMessage {
    private String id;
    private String fromId;
    private String fromName;
    private String text;
    private Instant date;
}