package com.peredereevin.aggregator.domain;

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

    @Column(nullable = false)
    private String platformMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(nullable = false)
    private String userId;          // email (sub) из JWT

    private String senderId;
    private String senderName;
    private String text;
    private Instant timestamp;

    @CreationTimestamp
    private Instant fetchedAt;
}
