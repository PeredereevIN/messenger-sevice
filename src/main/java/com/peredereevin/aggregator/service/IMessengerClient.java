package com.peredereevin.aggregator.service;

import com.peredereevin.aggregator.dto.MessageDto;
import java.util.List;

public interface IMessengerClient {
    List<MessageDto> getConversations(String platformUserId, int count, String accessToken);
    List<MessageDto> getMessages(String platformUserId, String conversationId, int count, String accessToken);
}