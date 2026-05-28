package com.peredereevin.aggregator.service;

import com.peredereevin.aggregator.dto.MessageDto;
import java.util.List;

public interface IMessengerClient {
    /**
     * @param platformUserId ID пользователя в мессенджере
     * @param count количество бесед
     * @return список бесед (каждая представлена последним сообщением)
     */
    List<MessageDto> getConversations(String platformUserId, int count);

    /**
     * @param platformUserId ID пользователя в мессенджере
     * @param conversationId ID беседы
     * @param count количество сообщений
     * @return список сообщений в беседе
     */
    List<MessageDto> getMessages(String platformUserId, String conversationId, int count);
}