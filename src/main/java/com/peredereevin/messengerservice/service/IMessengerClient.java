package com.peredereevin.messengerservice.service;

import com.peredereevin.messengerservice.dto.MessageDto;
import java.util.List;

public interface IMessengerClient {
    List<MessageDto> getConversations(String platformUserId, int count, String accessToken);
    List<MessageDto> getMessages(String platformUserId, String conversationId, int count, String accessToken);
    String sendMessage(String platformUserId, String recipientId, String text, String accessToken);
}