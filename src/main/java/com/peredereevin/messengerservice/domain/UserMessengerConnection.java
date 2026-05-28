package com.peredereevin.messengerservice.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_messenger_connections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMessengerConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;              // email (sub из JWT)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;          // VK, WHATSAPP

    @Column(nullable = false)
    private String platformUserId;      // ID в мессенджере (числовой или строка)

    @Column(nullable = false, length = 512)
    private String accessToken;         // токен доступа (пока общий из конфига, но готовы к персональным)
}