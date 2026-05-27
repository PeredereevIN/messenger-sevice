package com.peredereevin.aggregator.service;

import com.peredereevin.aggregator.io.VkMessage;
import java.util.List;

public interface VkApiClient {
    List<VkMessage> getConversations(String userId, int count);
}