package com.peredereevin.messengerservice.service;

import com.peredereevin.messengerservice.dto.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service("vk")
public class VkApiClientImpl implements IMessengerClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String defaultAccessToken;
    private final String version;
    private final String baseUrl;

    private final Map<Long, String> nameCache = new ConcurrentHashMap<>();

    static {
        disableSslVerification();
        disableSystemProxies();
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

    private static void disableSystemProxies() {
        // Принудительно отключаем использование системных прокси
        System.setProperty("java.net.useSystemProxies", "false");

        // Удаляем системные свойства прокси, если они заданы
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("http.nonProxyHosts");
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");

        // Устанавливаем дефолтный ProxySelector, который всегда возвращает NO_PROXY
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return Collections.singletonList(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                // ignore
            }
        });
    }

    public VkApiClientImpl(@Value("${vk.api.base-url}") String baseUrl,
                           @Value("${vk.api.access-token}") String defaultAccessToken,
                           @Value("${vk.api.version}") String version) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(java.net.Proxy.NO_PROXY);   // отключаем системный прокси
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));

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
        return executeConversationsRequest(url, token);
    }

    @Override
    public List<MessageDto> getMessages(String platformUserId, String conversationId, int count, String accessToken) {
        String token = resolveToken(accessToken);
        String url = String.format(
                "%s/messages.getHistory?access_token=%s&v=%s&peer_id=%s&count=%d",
                baseUrl, token, version, conversationId, count);
        log.info("Requesting VK messages: {}", url);
        return executeMessagesRequest(url, conversationId, token);
    }

    @Override
    public String sendMessage(String platformUserId, String recipientId, String text, String accessToken) {
        String token = resolveToken(accessToken);
        // Проверяем, что recipientId передан
        if (recipientId == null || recipientId.isBlank()) {
            log.error("recipientId is null or empty");
            return "Error: recipientId is required";
        }
        try {
            String url = baseUrl + "/messages.send";
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url)
                    .queryParam("access_token", token)
                    .queryParam("v", version)
                    .queryParam("message", text)
                    .queryParam("random_id", System.currentTimeMillis());

            // Определяем, какой параметр использовать: user_id или peer_id
            // Если ID начинается с минуса или содержит более 9 цифр — это peer_id
            if (recipientId.startsWith("-") || recipientId.length() > 9) {
                builder.queryParam("peer_id", recipientId);
            } else {
                builder.queryParam("user_id", recipientId);
            }

            URI uri = builder.build().encode().toUri();
            log.info("Sending VK message: {}", uri);

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error")) {
                JsonNode error = root.get("error");
                log.error("VK send error: {}", error);
                return "Error: " + error.get("error_msg").asText();
            }
            return root.path("response").asText(); // ID сообщения
        } catch (Exception e) {
            log.error("Failed to send VK message", e);
            return "Error: " + e.getMessage();
        }
    }

    private String resolveToken(String providedToken) {
        return (providedToken != null && !providedToken.isBlank()) ? providedToken : defaultAccessToken;
    }

    private List<MessageDto> executeConversationsRequest(String url, String accessToken) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {log.error("VK API returned status: {}", response.getStatusCode());
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
                JsonNode lastMessage = item.path("last_message");
                if (!lastMessage.isMissingNode()) {
                    MessageDto msg = parseMessage(lastMessage, accessToken);
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

    private List<MessageDto> executeMessagesRequest(String url, String conversationId, String accessToken) {
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
                MessageDto msg = parseMessage(item, accessToken);
                msg.setConversationId(conversationId);
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

    private MessageDto parseMessage(JsonNode messageNode, String accessToken) {
        // Безопасное извлечение id
        JsonNode idNode = messageNode.get("id");
        String messageId = idNode != null ? String.valueOf(idNode.asLong()) : "0";

        // Безопасное извлечение from_id
        JsonNode fromIdNode = messageNode.get("from_id");
        long fromId = fromIdNode != null ? fromIdNode.asLong() : 0L;

        // Безопасное извлечение peer_id
        JsonNode peerNode = messageNode.get("peer_id");
        String conversationId = peerNode != null ? String.valueOf(peerNode.asLong()) : "0";

        // Текст может отсутствовать
        String text = messageNode.has("text") ? messageNode.get("text").asText("") : "";

        // Дата
        long timestamp = messageNode.has("date") ? messageNode.get("date").asLong() : Instant.now().getEpochSecond();

        // Имя отправителя
        String senderName = getSenderName(fromId, accessToken);

        return MessageDto.builder()
                .platformMessageId(messageId)
                .conversationId(conversationId).senderId(String.valueOf(fromId))
                .senderName(senderName)
                .text(text)
                .timestamp(Instant.ofEpochSecond(timestamp))
                .build();
    }

    private String getSenderName(long senderId, String accessToken) {
        if (senderId > 0) {
            return getUserName(senderId, accessToken);
        } else if (senderId < 0) {
            return getGroupName(-senderId, accessToken);
        }
        return "Unknown";
    }

    private String getUserName(long userId, String accessToken) {
        if (userId == 0) return "Unknown User";
        return nameCache.computeIfAbsent(userId, id -> {
            String url = String.format(
                    "%s/users.get?user_ids=%d&access_token=%s&v=%s",
                    baseUrl, id, accessToken, version);
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode userNode = root.path("response").get(0);
                if (userNode != null && !userNode.isMissingNode()) {
                    String firstName = userNode.path("first_name").asText("");
                    String lastName = userNode.path("last_name").asText("");
                    return (firstName + " " + lastName).trim();
                }
            } catch (Exception e) {
                log.error("Error fetching user name for id={}", id, e);
            }
            return "Unknown User";
        });
    }

    private String getGroupName(long groupId, String accessToken) {
        if (groupId == 0) return "Unknown Group";
        return nameCache.computeIfAbsent(-groupId, id -> {
            String url = String.format(
                    "%s/groups.getById?group_id=%d&access_token=%s&v=%s",
                    baseUrl, -id, accessToken, version);
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode groupNode = root.path("response").get(0);
                if (groupNode != null && !groupNode.isMissingNode()) {
                    return groupNode.path("name").asText("Unknown Group");
                }
            } catch (Exception e) {
                log.error("Error fetching group name for id={}", -id, e);
            }
            return "Unknown Group";
        });
    }
}