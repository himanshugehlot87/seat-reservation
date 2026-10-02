package com.reservation.repository;

import com.reservation.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByUserIdAndShowIdAndIdempotencyKey(
            String userId,
            Long showId,
            String idempotencyKey
    );
}