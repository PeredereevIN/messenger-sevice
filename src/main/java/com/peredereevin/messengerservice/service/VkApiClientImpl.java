package com.peredereevin.messengerservice.service;

import com.peredereevin.messengerservice.dto.MessageDto;
import com.peredereevin.messengerservice.dto.attachment.Attachment;
import com.peredereevin.messengerservice.dto.attachment.DocAttachment;
import com.peredereevin.messengerservice.dto.attachment.PhotoAttachment;
import com.peredereevin.messengerservice.dto.attachment.VideoAttachment;
import com.peredereevin.messengerservice.exception.InvalidTokenException;
import com.peredereevin.messengerservice.exception.VkAttachmentUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service("vk")
public class VkApiClientImpl implements IMessengerClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String defaultAccessToken;
    private final String version;
    private final String baseUrl;
    private final Map<Long, String> nameCache = new ConcurrentHashMap<>();
    private final Map<String, String> uploadUrlCache = new ConcurrentHashMap<>();

    static {
        disableSslVerification();
        disableSystemProxies();
    }

    private static void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
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
        System.setProperty("java.net.useSystemProxies", "false");
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("http.nonProxyHosts");
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return Collections.singletonList(Proxy.NO_PROXY);
            }
            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}
        });
    }

    public VkApiClientImpl(
            @Value("${vk.api.base-url}") String baseUrl,
            @Value("${vk.api.access-token}") String defaultAccessToken,
            @Value("${vk.api.version}") String version
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(java.net.Proxy.NO_PROXY);
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.defaultAccessToken = defaultAccessToken;
        this.version = version;
    }

    // ==================== Основные методы интерфейса ====================

    @Override
    public List<MessageDto> getConversations(String platformUserId, int count, String accessToken) {
        String token = resolveToken(accessToken);
        String url = String.format("%s/messages.getConversations?access_token=%s&v=%s&count=%d",
                baseUrl, token, version, Math.min(count, 200));
        return executeConversationsRequest(url, token);
    }

    @Override
    public List<MessageDto> getMessages(String platformUserId, String conversationId, int count, String accessToken) {
        String token = resolveToken(accessToken);
        String url = String.format("%s/messages.getHistory?access_token=%s&v=%s&peer_id=%s&count=%d&extended=1",
                baseUrl, token, version, conversationId, Math.min(count, 200));
        return executeMessagesRequest(url, conversationId, token);
    }

    @Override
    public String sendMessage(String platformUserId, String recipientId, String text, String accessToken) {
        return sendMessageWithAttachments(platformUserId, recipientId, text, null, accessToken);
    }

    // ==================== Метод с вложениями ====================

    public String sendMessageWithAttachments(String platformUserId, String recipientId, String text,
                                             List<Attachment> attachments, String accessToken) {
        String token = resolveToken(accessToken);
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/messages.send")
                    .queryParam("access_token", token)
                    .queryParam("v", version)
                    .queryParam("random_id", System.currentTimeMillis());

            if (text != null && !text.isEmpty()) {
                builder.queryParam("message", text);
            }

            if (recipientId.startsWith("-") || recipientId.length() > 9) {
                builder.queryParam("peer_id", recipientId);
            } else {
                builder.queryParam("user_id", recipientId);
            }

            if (attachments != null && !attachments.isEmpty()) {
                String attachmentStr = attachments.stream()
                        .map(Attachment::toAttachmentString)
                        .collect(Collectors.joining(","));
                builder.queryParam("attachment", attachmentStr);
            }

            URI uri = builder.build().toUri();  // параметры уже закодированы
            log.info("Sending VK message with attachments: {}", uri);

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            checkVkError(root);
            return root.path("response").asString();

        } catch (HttpClientErrorException e) {
            log.error("VK send HTTP error: {}", e.getResponseBodyAsString());
            return "Error: " + e.getStatusCode();
        } catch (Exception e) {
            log.error("Failed to send VK message", e);
            return "Error: " + e.getMessage();
        }
    }

    // ==================== Загрузка файлов ====================

    public PhotoAttachment uploadPhoto(File file, String accessToken) throws VkAttachmentUploadException {
        String token = resolveToken(accessToken);
        try {
            String uploadUrl = getUploadUrl("photos.getMessagesUploadServer", token);
            String responseJson = uploadFile(uploadUrl, file);
            JsonNode root = objectMapper.readTree(responseJson);
            String photoParam = root.path("photo").asText();
            String server = root.path("server").asText();
            String hash = root.path("hash").asText();

            // Кодируем photoParam вручную
            String photoEncoded = URLEncoder.encode(photoParam, StandardCharsets.UTF_8);
            String saveUrl = String.format("%s/photos.saveMessagesPhoto?access_token=%s&v=%s&photo=%s&server=%s&hash=%s",
                    baseUrl, token, version, photoEncoded, server, hash);

            ResponseEntity<String> saveResponse = restTemplate.getForEntity(saveUrl, String.class);
            JsonNode saveRoot = objectMapper.readTree(saveResponse.getBody());
            checkVkError(saveRoot);

            JsonNode savedPhoto = saveRoot.path("response").get(0);
            PhotoAttachment attachment = new PhotoAttachment();
            attachment.setOwnerId(savedPhoto.path("owner_id").asLong());
            attachment.setId(savedPhoto.path("id").asLong());
            attachment.setAccessKey(savedPhoto.path("access_key").asText());
            attachment.setWidth(savedPhoto.path("width").asInt());
            attachment.setHeight(savedPhoto.path("height").asInt());
            attachment.setUrl(savedPhoto.path("url").asText());
            attachment.setType("photo");
            return attachment;

        } catch (Exception e) {
            throw new VkAttachmentUploadException("Failed to upload photo", e);
        }
    }

    public DocAttachment uploadDoc(File file, String accessToken) throws VkAttachmentUploadException {
        String token = resolveToken(accessToken);
        try {
            String uploadUrl = getUploadUrl("docs.getMessagesUploadServer", token);
            String responseJson = uploadFile(uploadUrl, file);
            JsonNode root = objectMapper.readTree(responseJson);
            String fileParam = root.path("file").asText();

            String fileEncoded = URLEncoder.encode(fileParam, StandardCharsets.UTF_8);
            String saveUrl = String.format("%s/docs.save?access_token=%s&v=%s&file=%s",
                    baseUrl, token, version, fileEncoded);

            ResponseEntity<String> saveResponse = restTemplate.getForEntity(saveUrl, String.class);
            JsonNode saveRoot = objectMapper.readTree(saveResponse.getBody());
            checkVkError(saveRoot);

            JsonNode savedDoc = saveRoot.path("response").get(0);
            DocAttachment attachment = new DocAttachment();
            attachment.setOwnerId(savedDoc.path("owner_id").asLong());
            attachment.setId(savedDoc.path("id").asLong());
            attachment.setAccessKey(savedDoc.path("access_key").asText());
            attachment.setTitle(savedDoc.path("title").asText());
            attachment.setExt(savedDoc.path("ext").asText());
            attachment.setSize(savedDoc.path("size").asInt());
            attachment.setUrl(savedDoc.path("url").asText());
            attachment.setType("doc");
            return attachment;

        } catch (Exception e) {
            throw new VkAttachmentUploadException("Failed to upload document", e);
        }
    }

    private String getUploadUrl(String method, String accessToken) {
        return uploadUrlCache.computeIfAbsent(method, key -> {
            String url = String.format("%s/%s?access_token=%s&v=%s", baseUrl, method, accessToken, version);
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                JsonNode root = objectMapper.readTree(response.getBody());
                checkVkError(root);
                return root.path("response").path("upload_url").asText();
            } catch (Exception e) {
                log.error("Failed to get upload URL for {}", method, e);
                return null;
            }
        });
    }

    private String uploadFile(String uploadUrl, File file) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, requestEntity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IOException("Upload failed with status: " + response.getStatusCode());
        }
        return response.getBody();
    }

    // ==================== Вспомогательные методы ====================

    private String resolveToken(String providedToken) {
        return (providedToken != null && !providedToken.isBlank()) ? providedToken : defaultAccessToken;
    }

    private List<MessageDto> executeConversationsRequest(String url, String accessToken) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("VK API returned status: {}", response.getStatusCode());
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            checkVkError(root);
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
        } catch (InvalidTokenException e) {
            throw e;
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
            checkVkError(root);
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
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error when calling VK API", e);
            return Collections.emptyList();
        }
    }

    private void checkVkError(JsonNode root) {
        if (root.has("error")) {
            JsonNode error = root.get("error");
            int errorCode = error.path("error_code").asInt();
            String errorMsg = error.path("error_msg").asText();
            log.error("VK API error: code={}, message={}", errorCode, errorMsg);
            if (errorCode == 5) {
                throw new InvalidTokenException("VK token expired or invalid");
            }
        }
    }

    private MessageDto parseMessage(JsonNode messageNode, String accessToken) {
        JsonNode idNode = messageNode.get("id");
        String messageId = idNode != null ? String.valueOf(idNode.asLong()) : "0";

        JsonNode fromIdNode = messageNode.get("from_id");
        long fromId = fromIdNode != null ? fromIdNode.asLong() : 0L;

        JsonNode peerNode = messageNode.get("peer_id");
        String conversationId = peerNode != null ? String.valueOf(peerNode.asLong()) : "0";

        String text = messageNode.has("text") ? messageNode.get("text").asText("") : "";
        long timestamp = messageNode.has("date") ? messageNode.get("date").asLong() : Instant.now().getEpochSecond();

        String senderName = getSenderName(fromId, accessToken);

        // Парсим только фото, документы, видео
        List<Attachment> attachments = parseAttachments(messageNode.path("attachments"));

        boolean isOutgoing = false;
        JsonNode outNode = messageNode.get("out");
        if (outNode != null) {
            isOutgoing = outNode.asInt() == 1;
        }

        // Используем полное имя класса для ясности (но импорт уже есть)
        return MessageDto.builder()
                .platformMessageId(messageId)
                .conversationId(conversationId)
                .senderId(String.valueOf(fromId))
                .senderName(senderName)
                .text(text)
                .timestamp(Instant.ofEpochSecond(timestamp))
                .attachments(attachments)
                .isOutgoing(isOutgoing)
                .build();
    }

    private List<Attachment> parseAttachments(JsonNode attachmentsNode) {
        if (attachmentsNode.isMissingNode() || !attachmentsNode.isArray()) {
            return Collections.emptyList();
        }
        List<Attachment> result = new ArrayList<>();
        for (JsonNode node : attachmentsNode) {
            String type = node.path("type").asText();
            JsonNode data = node.path(type);
            if (data.isMissingNode()) continue;

            Attachment attachment = null;
            switch (type) {
                case "photo":
                    attachment = parsePhoto(data);
                    break;
                case "doc":
                    attachment = parseDoc(data);
                    break;
                case "video":
                    attachment = parseVideo(data);
                    break;
                default:
                    break;
            }
            if (attachment != null) {
                result.add(attachment);
            }
        }
        return result;
    }

    private PhotoAttachment parsePhoto(JsonNode data) {
        PhotoAttachment photo = new PhotoAttachment();
        photo.setType("photo");
        photo.setOwnerId(data.path("owner_id").asLong());
        photo.setId(data.path("id").asLong());
        photo.setAccessKey(data.path("access_key").asText());
        photo.setText(data.path("text").asText());
        JsonNode sizes = data.path("sizes");
        if (sizes.isArray() && sizes.size() > 0) {
            JsonNode largest = sizes.get(sizes.size() - 1);
            photo.setUrl(largest.path("url").asText());
            photo.setWidth(largest.path("width").asInt());
            photo.setHeight(largest.path("height").asInt());
        } else {
            photo.setUrl(data.path("url").asText());
        }
        return photo;
    }

    private DocAttachment parseDoc(JsonNode data) {
        DocAttachment doc = new DocAttachment();
        doc.setType("doc");
        doc.setOwnerId(data.path("owner_id").asLong());
        doc.setId(data.path("id").asLong());
        doc.setAccessKey(data.path("access_key").asText());
        doc.setTitle(data.path("title").asText());
        doc.setExt(data.path("ext").asText());
        doc.setSize(data.path("size").asInt());
        doc.setUrl(data.path("url").asText());
        return doc;
    }

    private VideoAttachment parseVideo(JsonNode data) {
        VideoAttachment video = new VideoAttachment();
        video.setType("video");
        video.setOwnerId(data.path("owner_id").asLong());
        video.setId(data.path("id").asLong());
        video.setAccessKey(data.path("access_key").asText());
        video.setTitle(data.path("title").asText());
        video.setDuration(data.path("duration").asInt());
        video.setPlayerUrl(data.path("player").asText());
        return video;
    }

    // ==================== Получение имён ====================

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
            String url = String.format("%s/users.get?user_ids=%d&access_token=%s&v=%s",
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
            String url = String.format("%s/groups.getById?group_id=%d&access_token=%s&v=%s",
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