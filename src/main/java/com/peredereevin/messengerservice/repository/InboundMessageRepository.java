package com.peredereevin.messengerservice.repository;

import com.peredereevin.messengerservice.domain.InboundMessage;
import com.peredereevin.messengerservice.domain.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InboundMessageRepository extends JpaRepository<InboundMessage, Long> {
    List<InboundMessage> findByUserIdAndPlatform(String userId, Platform platform);
}