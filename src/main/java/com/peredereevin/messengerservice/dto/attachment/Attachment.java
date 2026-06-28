package com.peredereevin.messengerservice.dto.attachment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PhotoAttachment.class, name = "photo"),
        @JsonSubTypes.Type(value = DocAttachment.class, name = "doc"),
        @JsonSubTypes.Type(value = VideoAttachment.class, name = "video")
})
public abstract class Attachment {
    private String type;
    public abstract String toAttachmentString();
}