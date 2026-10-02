package com.reservation.controller;

import com.reservation.dto.CancellationResponse;
import com.reservation.dto.ReservationResponse;
import com.reservation.dto.ReserveSeatsRequest;
import com.reservation.entity.Reservation;
import com.reservation.service.AuthService;
import com.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ReservationController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ReservationController.class);

    private final AuthService authService;
    private final ReservationService reservationService;

    @PostMapping("/{showId}/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserveSeats(
            @PathVariable Long showId,
            @Valid @RequestBody ReserveSeatsRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false)
            String authorization) {

        String userId = authService.getUserId(authorization);
        log.info("Reservation request received for showId={}, userId={}",
                showId, userId);

        return reservationService.reserveSeats(
                showId,
                userId,
                idempotencyKey,
                request
        );
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public CancellationResponse cancelReservation(
            @PathVariable Long reservationId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        String userId = authService.getUserId(authorization);

        return reservationService.cancelReservation(
                reservationId,
                userId
        );
    }
}