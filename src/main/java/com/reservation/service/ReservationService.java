package com.reservation.service;

import com.reservation.dto.CancellationResponse;
import com.reservation.dto.ReservationResponse;
import com.reservation.dto.ReserveSeatsRequest;
import com.reservation.entity.*;
import com.reservation.exception.*;
import com.reservation.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final RequestHashService requestHashService;
    private final ReservationMetrics reservationMetrics;

    @Transactional
    public ReservationResponse reserveSeats(
            Long showId,
            String userId,
            String idempotencyKey,
            ReserveSeatsRequest request) {

        // 1. Find show
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException("Show not found"));

        // 2. Remove duplicates and sort seats
        List<String> seatNumbers = request.getSeats()
                .stream()
                .distinct()
                .sorted()
                .toList();

        // 3. User/show advisory lock
        reservationRepository.lockUserForShow(userId, showId);

        // 4. Idempotency check
        Optional<IdempotencyKey> existingKey =
                idempotencyKeyRepository
                        .findByUserIdAndShowIdAndIdempotencyKey(
                                userId,
                                showId,
                                idempotencyKey
                        );

        String requestHash = requestHashService.createHash(
                showId,
                seatNumbers
        );

        if (existingKey.isPresent()) {

            IdempotencyKey savedKey = existingKey.get();

            if (!savedKey.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                        "Idempotency key was already used with a different request"
                );
            }

            Reservation existingReservation =
                    savedKey.getReservation();

            // Return the original reservation
            List<Seat> existingSeats =
                    reservationSeatRepository
                            .findByReservationId(existingReservation.getId())
                            .stream()
                            .map(ReservationSeat::getSeat)
                            .toList();

            return toResponse(
                    existingReservation,
                    existingSeats
            );
        }

        // 5. Per-user limit
        long existingSeatCount =
                reservationRepository.countConfirmedSeats(
                        showId,
                        userId
                );

        if (existingSeatCount + seatNumbers.size()
                > show.getPerUserLimit()) {

            reservationMetrics.incrementConflict();

            throw new PerUserLimitExceededException(
                    "Per-user seat limit exceeded"
            );
        }


        // 6. Lock requested seats
        List<Seat> seats = seatRepository.findSeatsForUpdate(
                showId,
                seatNumbers
        );

        // 7. Check that all requested seats exist
        if (seats.size() != seatNumbers.size()) {
            throw new SeatNotFoundException(
                    "One or more requested seats do not exist"
            );
        }

        // 8. Check availability
        boolean anySeatUnavailable = seats.stream()
                .anyMatch(seat ->
                        seat.getStatus() != SeatStatus.AVAILABLE);

        if (anySeatUnavailable) {

            reservationMetrics.incrementConflict();

            throw new SeatUnavailableException(
                    "One or more seats are already reserved"
            );
        }

        // 9. Calculate amount
        int amountPaise = show.getPricePaise() * seats.size();

        // 10. Create reservation
        Reservation reservation = new Reservation();
        reservation.setShow(show);
        reservation.setUserId(userId);
        reservation.setAmountPaise(amountPaise);
        reservation.setStatus(ReservationStatus.CONFIRMED);

        reservation = reservationRepository.save(reservation);

        // 11. Mark seats as confirmed
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.CONFIRMED);
        }

        seatRepository.saveAll(seats);

        // 12. Create reservation-seat records
        for (Seat seat : seats) {

            ReservationSeat reservationSeat = new ReservationSeat();

            reservationSeat.setReservation(reservation);
            reservationSeat.setSeat(seat);

            reservationSeatRepository.save(reservationSeat);
        }

        IdempotencyKey idempotencyRecord = new IdempotencyKey();

        idempotencyRecord.setUserId(userId);
        idempotencyRecord.setShow(show);
        idempotencyRecord.setIdempotencyKey(idempotencyKey);
        idempotencyRecord.setRequestHash(requestHash);
        idempotencyRecord.setReservation(reservation);

        idempotencyKeyRepository.save(idempotencyRecord);

        reservationMetrics.incrementSuccess();

        return toResponse(reservation, seats);
    }

    private ReservationResponse toResponse(
            Reservation reservation,
            List<Seat> seats) {

        ReservationResponse response = new ReservationResponse();

        response.setReservationId(reservation.getId());
        response.setShowId(reservation.getShow().getId());
        response.setUserId(reservation.getUserId());

        response.setSeats(
                seats.stream()
                        .map(Seat::getSeatNumber)
                        .toList()
        );

        response.setAmountPaise(reservation.getAmountPaise());
        response.setStatus(reservation.getStatus());

        return response;
    }

    @Transactional
    public CancellationResponse cancelReservation(
            Long reservationId,
            String userId) {

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new ReservationNotFoundException("Reservation not found"));

        // Verify ownership
        if (!reservation.getUserId().equals(userId)) {
            throw new UnauthorizedCancellationException(
                    "You are not allowed to cancel this reservation"
            );
        }

        // Already cancelled
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return new CancellationResponse(
                    reservation.getId(),
                    reservation.getStatus().name(),
                    "Reservation is already cancelled"
            );
        }

        // Get seats belonging to this reservation
        List<ReservationSeat> reservationSeats =
                reservationSeatRepository.findByReservationId(reservationId);

        // Release seats
        List<Long> seatIds = reservationSeats.stream()
                .map(rs -> rs.getSeat().getId())
                .toList();

        List<Seat> seatsToRelease =
                seatRepository.findSeatsByIdsForUpdate(seatIds);

        for (Seat seat : seatsToRelease) {
            seat.setStatus(SeatStatus.AVAILABLE);
        }

        // Mark reservation cancelled
        reservation.setStatus(ReservationStatus.CANCELLED);

        seatRepository.saveAll(seatsToRelease);

        reservationRepository.save(reservation);

        reservationMetrics.incrementCancelled();

        return new CancellationResponse(
                reservation.getId(),
                reservation.getStatus().name(),
                "Reservation cancelled successfully"
        );
    }
}