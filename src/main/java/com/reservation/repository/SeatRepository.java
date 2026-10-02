package com.reservation.repository;

import com.reservation.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByShowId(Long showId);
    @Query(value = """
        SELECT *
        FROM seats
        WHERE show_id = :showId
          AND seat_number IN (:seatNumbers)
        ORDER BY seat_number
        FOR UPDATE
        """, nativeQuery = true)
    List<Seat> findSeatsForUpdate(
            @Param("showId") Long showId,
            @Param("seatNumbers") List<String> seatNumbers
    );

    @Query(value = """
    SELECT *
    FROM seats
    WHERE id IN (:seatIds)
    ORDER BY id
    FOR UPDATE
    """, nativeQuery = true)
    List<Seat> findSeatsByIdsForUpdate(
            @Param("seatIds") List<Long> seatIds
    );
}