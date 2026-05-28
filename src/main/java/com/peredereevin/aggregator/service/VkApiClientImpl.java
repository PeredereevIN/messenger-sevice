package com.peredereevin.aggregator.service;


import com.peredereevin.aggregator.dto.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service("vk")
public class VkApiClientImpl implements IMessengerClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String defaultAccessToken;
    private final String version;
    private final String baseUrl;

    static {
        disableSslVerification();
    }

    private static void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            log.error("Failed to disable SSL verification", e);
        }
    }

    public VkApiClientImpl(@Value("${vk.api.base-url}") String baseUrl,
                           @Value("${vk.api.access-token}") String defaultAccessToken,
                           @Value("${vk.api.version}") String version) {
        this.restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.defaultAccessToken = defaultAccessToken;
        this.version = version;
    }


    @Override
    public List<MessageDto> getConversations(String platformUserId, int count, String accessToken) {
        String token = resolveToken(accessToken);
        String url = String.format(
                "%s/messages.getConversations?access_token=%s&v=%s&count=%d",
                baseUrl, token, version, count);
        log.info("Requesting VK conversations: {}", url);
        return executeVkRequest(url, null);
    }

    @Override
    public List<MessageDto> getMessages(String platformUserId, String conversationId, int count, String accessToken) {
        String token = resolveToken(accessToken);
        String url = String.format(
                "%s/messages.getHistory?access_token=%s&v=%s&peer_id=%s&count=%d",
                baseUrl, token, version, conversationId, count);
        log.info("Requesting VK messages: {}", url);
        return executeVkRequest(url, conversationId);
    }

    private String resolveToken(String providedToken) {
        // Если передан непустой токен, используем его, иначе пробуем default из конфига
        return (providedToken != null && !providedToken.isBlank()) ? providedToken : defaultAccessToken;
    }

    private List<MessageDto> executeVkRequest(String url, String conversationId) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("VK API returned status: {}", response.getStatusCode());
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error")) {
                JsonNode error = root.get("error");
                log.error("VK API error: code={}, message={}",
                        error.get("error_code").asText(),
                        error.get("error_msg").asText());
                return Collections.emptyList();
            }

            List<MessageDto> messages = new ArrayList<>();
            JsonNode items = root.path("response").path("items");
            for (JsonNode item : items) {
                MessageDto msg = parseMessage(item);
                if (conversationId != null) {
                    msg.setConversationId(conversationId);
                }
                messages.add(msg);
            }
            return messages;

        } catch (HttpClientErrorException e) {
            log.error("VK API client error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unexpected error when calling VK API", e);
            return Collections.emptyList();
        }
    }

    private MessageDto parseMessage(JsonNode messageNode) {
        long fromId = messageNode.get("from_id").asLong();
        return MessageDto.builder()
                .platformMessageId(String.valueOf(messageNode.get("id").asLong()))
                .conversationId(messageNode.has("peer_id") ? String.valueOf(messageNode.get("peer_id").asLong()) : "0")
                .senderId(String.valueOf(fromId))
                .senderName(getSenderName(fromId))
                .text(messageNode.path("text").asText(""))
                .timestamp(Instant.ofEpochSecond(messageNode.get("date").asLong()))
                .build();
    }

    private String getSenderName(long senderId) {
        if (senderId > 0) {
            return getUserName(senderId);
        } else {
            return getGroupName(-senderId);
        }
    }

    private String getUserName(long userId) {
        String url = String.format(
                "%s/users.get?user_ids=%d&access_token=%s&v=%s",
                baseUrl, defaultAccessToken, version, userId);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode userNode = root.path("response").get(0);
            if (userNode != null && !userNode.isMissingNode()) {
                return userNode.get("first_name").asText() + " " + userNode.get("last_name").asText();
            }
        } catch (Exception e) {
            log.error("Error fetching user name for id={}", userId, e);
        }
        return "Unknown User";
    }

    private String getGroupName(long groupId) {
        String url = String.format(
                "%s/groups.getById?group_id=%d&access_token=%s&v=%s",
                baseUrl, defaultAccessToken, version, groupId);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode groupNode = root.path("response").get(0);
            if (groupNode != null && !groupNode.isMissingNode()) {
                return groupNode.get("name").asText();
            }
        } catch (Exception e) {
            log.error("Error fetching group name for id={}", groupId, e);
        }
        return "Unknown Group";
    }
}