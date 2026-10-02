package com.reservation.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class ReservationMetrics {

    private final Counter reservationSuccess;
    private final Counter reservationConflict;
    private final Counter reservationCancelled;

    public ReservationMetrics(MeterRegistry meterRegistry) {
        this.reservationSuccess = Counter.builder("reservation_success_total")
                .description("Total successful reservations")
                .register(meterRegistry);

        this.reservationConflict = Counter.builder("reservation_conflict_total")
                .description("Total reservation conflicts")
                .register(meterRegistry);

        this.reservationCancelled = Counter.builder("reservation_cancelled_total")
                .description("Total cancelled reservations")
                .register(meterRegistry);
    }

    public void incrementSuccess() {
        reservationSuccess.increment();
    }

    public void incrementConflict() {
        reservationConflict.increment();
    }

    public void incrementCancelled() {
        reservationCancelled.increment();
    }
}