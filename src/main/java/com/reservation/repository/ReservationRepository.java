package com.reservation.repository;

import com.reservation.entity.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Query(
            value = """
SELECT pg_advisory_xact_lock(hashtext(:userId), CAST(:showId AS INTEGER))
""",
            nativeQuery = true
    )
    void lockUserForShow(
            @Param("userId") String userId,
            @Param("showId") Long showId
    );

    @Query("""
    SELECT COUNT(rs)
    FROM ReservationSeat rs
    JOIN rs.reservation r
    WHERE r.show.id = :showId
      AND r.userId = :userId
      AND r.status = com.reservation.entity.ReservationStatus.CONFIRMED
""")
    long countConfirmedSeats(
            @Param("showId") Long showId,
            @Param("userId") String userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);
}