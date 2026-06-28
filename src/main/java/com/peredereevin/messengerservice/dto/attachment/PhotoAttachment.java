package com.peredereevin.messengerservice.dto.attachment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhotoAttachment extends Attachment {
    private Long ownerId;
    private Long id;
    private String accessKey;
    private String text;
    private Integer width;
    private Integer height;
    private String url;

    @ Override
    public String toAttachmentString() {
        StringBuilder sb = new StringBuilder("photo");
        sb.append(ownerId).append("_").append(id);
        if (accessKey != null && !accessKey.isEmpty()) {
            sb.append("_").append(accessKey);
        }
        return sb.toString();
    }
}