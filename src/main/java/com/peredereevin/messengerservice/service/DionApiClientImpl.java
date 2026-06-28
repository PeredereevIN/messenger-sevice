package com.peredereevin.messengerservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peredereevin.messengerservice.dto.MessageDto;
import com.peredereevin.messengerservice.exception.InvalidTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.*;
import java.net.Proxy;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
//@Service("dion")
public class DionApiClientImpl implements IMessengerClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String email;
    private final String password;

    private String cachedToken;
    private Instant tokenExpiry = Instant.MIN;
    private final Map<String, Long> lastUpdateIds = new ConcurrentHashMap<>();

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

    public DionApiClientImpl(@ Value("${dion.api.base-url}") String baseUrl,
                             @ Value("${dion.api.email}") String email,
                             @ Value("${dion.api.password}") String password) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(Proxy.NO_PROXY);
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.email = email;
        this.password = password;
    }

    private synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("email", email, "password", password);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/platform/v1/token", request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            cachedToken = root.get("access_token").asText();
            tokenExpiry = Instant.now().plusSeconds(12 * 3600);
            log.info("Dion token obtained, valid until {}", tokenExpiry);
            return cachedToken;
        } catch (Exception e) {
            log.error("Failed to obtain Dion token", e);
            throw new RuntimeException("Dion authentication failed", e);
        }
    }

    @ Override
    public List<MessageDto> getConversations(String platformUserId, int count, String accessToken) {
        String token = getToken();
        long lastUpdateId = lastUpdateIds.getOrDefault(platformUserId, 0L);
        List<JsonNode> updates = fetchUpdates(token, lastUpdateId, 100, 0);

        List<MessageDto> result = new ArrayList<>();
        Set<String> seenChats = new HashSet<>();
        long maxUpdateId = lastUpdateId;

        for (JsonNode update : updates) {
            long updateId = update.get("update_id").asLong();
            if (updateId > maxUpdateId) {
                maxUpdateId = updateId;
            }
            if (update.has("message")) {
                JsonNode msg = update.get("message");
                String chatId = msg.path("chat").path("id").asText("");
                if (!chatId.isEmpty() && !seenChats.contains(chatId)) {
                    seenChats.add(chatId);
                    result.add(convertToMessageDto(msg, chatId));
                }
            }
        }

        if (maxUpdateId > lastUpdateId) {
            lastUpdateIds.put(platformUserId, maxUpdateId);
        }
        if (result.size() > count) {
            result = result.subList(0, count);
        }
        return result;
    }

    @ Override
    public List<MessageDto> getMessages(String platformUserId, String conversationId, int count, String accessToken) {
        String token = getToken();
        long lastUpdateId = lastUpdateIds.getOrDefault(platformUserId, 0L);
        List<JsonNode> updates = fetchUpdates(token, lastUpdateId, 100, 0);

        List<MessageDto> result = new ArrayList<>();
        long maxUpdateId = lastUpdateId;

        for (JsonNode update : updates) {
            long updateId = update.get("update_id").asLong();
            if (updateId > maxUpdateId) {
                maxUpdateId = updateId;
            }
            if (update.has("message")) {
                JsonNode msg = update.get("message");
                String chatId = msg.path("chat").path("id").asText("");
                if (chatId.equals(conversationId)) {
                    result.add(convertToMessageDto(msg, chatId));
                }
            }
        }

        if (maxUpdateId > lastUpdateId) {
            lastUpdateIds.put(platformUserId, maxUpdateId);
        }
        if (result.size() > count) {
            result = result.subList(0, count);
        }
        return result;
    }

    @ Override
    public String sendMessage(String platformUserId, String recipientId, String text, String accessToken) {
        String token = getToken();
        try {
            String url = baseUrl + "/chats/v2/sendMessage";
            URI uri = UriComponentsBuilder.fromUriString(url)
                    .queryParam("chat_id", recipientId)
                    .queryParam("text", text)
                    .build()
                    .encode()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.POST, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error")) {
                log.error("Dion send error: {}", root.get("error"));
                throw new RuntimeException("Dion send failed: " + root.get("error").asText());
            }
            return root.path("result").path("message_id").asText("sent");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new InvalidTokenException("Dion token expired");
            }
            log.error("Dion send HTTP error: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Dion send failed", e);
        } catch (Exception e) {
            log.error("Failed to send Dion message", e);
            throw new RuntimeException("Dion send failed", e);
        }
    }

    private List<JsonNode> fetchUpdates(String token, long offset, int limit, int timeout) {
        try {
            String url = baseUrl + "/chats/v2/getUpdates";
            URI uri = UriComponentsBuilder.fromUriString(url)
                    .queryParam("offset", offset)
                    .queryParam("limit", limit)
                    .queryParam("timeout", timeout)
                    .build()
                    .encode()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.path("ok").asBoolean(false)) {
                log.error("Dion getUpdates failed: {}", root);
                return Collections.emptyList();
            }
            JsonNode result = root.path("result");
            if (result.isArray()) {
                List<JsonNode> updates = new ArrayList<>();
                result.forEach(updates::add);
                return updates;
            }
        } catch (Exception e) {
            log.error("Failed to fetch Dion updates", e);
        }
        return Collections.emptyList();
    }

    private MessageDto convertToMessageDto(JsonNode msg, String chatId) {
        long messageId = msg.path("message_id").asLong(0);
        long fromId = msg.path("from").path("id").asLong(0);
        String senderName = msg.path("from").path("name").asText("Unknown");
        String text = msg.path("text").asText("");
        long date = msg.path("date").asLong(0);

        return MessageDto.builder()
                .platformMessageId(String.valueOf(messageId))
                .conversationId(chatId)
                .senderId(String.valueOf(fromId))
                .senderName(senderName)
                .text(text)
                .timestamp(Instant.ofEpochSecond(date))
                .build();
    }
}