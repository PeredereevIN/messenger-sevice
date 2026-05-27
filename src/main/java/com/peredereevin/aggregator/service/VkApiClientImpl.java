package com.peredereevin.aggregator.service;


import com.peredereevin.aggregator.io.VkMessage;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class VkApiClientImpl implements VkApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String accessToken;
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
                           @Value("${vk.api.access-token}") String accessToken,
                           @Value("${vk.api.version}") String version) {
        this.restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.accessToken = accessToken;
        this.version = version;
    }


    @Override
    public List<VkMessage> getConversations(String userId, int count) {
        String url = String.format(
                "%s/messages.getConversations?access_token=%s&v=%s&count=%d",
                baseUrl, accessToken, version, count);
        log.info("Requesting VK: {}", url);

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

            List<VkMessage> messages = new ArrayList<>();
            JsonNode items = root.path("response").path("items");
            for (JsonNode item : items) {
                JsonNode lastMessage = item.path("last_message");
                if (!lastMessage.isMissingNode()) {
                    VkMessage msg = parseMessage(lastMessage);
                    messages.add(msg);
                }
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

    private VkMessage parseMessage(JsonNode messageNode) {
        long fromId = messageNode.get("from_id").asLong();
        return VkMessage.builder()
                .id(String.valueOf(messageNode.get("id").asLong()))
                .fromId(String.valueOf(fromId))
                .fromName(getSenderName(fromId)) // Используем новый универсальный метод
                .text(messageNode.path("text").asText(""))
                .date(Instant.ofEpochSecond(messageNode.get("date").asLong()))
                .build();
    }

    // Новый универсальный метод для получения имени отправителя
    private String getSenderName(long senderId) {
        if (senderId > 0) {
            return getUserName(senderId); // Для обычных пользователей
        } else {
            return getGroupName(-senderId); // Для сообществ
        }
    }

    // Получение имени пользователя (без изменений, только добавили проверку)
    private String getUserName(long userId) {
        String url = String.format(
                "%s/users.get?user_ids=%d&access_token=%s&v=%s",
                baseUrl, userId, accessToken, version);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode userNode = root.path("response").get(0);
            // Проверяем, найден ли пользователь
            if (userNode != null && !userNode.isMissingNode()) {
                return userNode.get("first_name").asText() + " " + userNode.get("last_name").asText();
            }
        } catch (Exception e) {
            log.error("Error fetching user name for id={}", userId, e);
        }
        return "Unknown User";
    }

    // Новый метод для получения названия сообщества
    private String getGroupName(long groupId) {
        String url = String.format(
                "%s/groups.getById?group_id=%d&access_token=%s&v=%s",
                baseUrl, groupId, accessToken, version);
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