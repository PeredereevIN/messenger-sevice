package com.peredereevin.aggregator.repository;

import com.peredereevin.aggregator.domain.Platform;
import com.peredereevin.aggregator.domain.UserMessengerConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMessengerConnectionRepository extends JpaRepository<UserMessengerConnection, Long> {
    Optional<UserMessengerConnection> findByUserIdAndPlatform(String userId, Platform platform);
    boolean existsByUserIdAndPlatform(String userId, Platform platform);
    void deleteByUserIdAndPlatform(String userId, Platform platform);
}