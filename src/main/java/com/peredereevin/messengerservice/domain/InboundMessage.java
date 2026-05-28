package com.peredereevin.messengerservice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "inbound_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String platformMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Column(nullable = false, length = 255)
    private String userId;          // email (sub) из JWT

    @Column(length = 50)
    private String senderId;

    @Column(length = 500)
    private String senderName;

    @Column(columnDefinition = "TEXT")
    private String text;

    private Instant timestamp;

    @CreationTimestamp
    private Instant fetchedAt;
}
