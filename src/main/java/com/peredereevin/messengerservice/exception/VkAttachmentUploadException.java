package com.peredereevin.messengerservice.exception;

public class VkAttachmentUploadException extends Exception {
    public VkAttachmentUploadException(String message) {
        super(message);
    }
    public VkAttachmentUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}