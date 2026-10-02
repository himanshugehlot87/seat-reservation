package com.reservation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SeatUnavailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleSeatUnavailable(
            SeatUnavailableException ex) {

        return Map.of(
                "error", ex.getMessage()
        );
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleIdempotencyConflict(
            IdempotencyConflictException ex) {

        return Map.of(
                "error", ex.getMessage()
        );
    }

    @ExceptionHandler(PerUserLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handlePerUserLimit(
            PerUserLimitExceededException ex) {

        return Map.of(
                "error", ex.getMessage()
        );
    }

    @ExceptionHandler(ShowNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleShowNotFound(
            ShowNotFoundException ex) {

        return Map.of(
                "error", ex.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid request");

        return Map.of(
                "error", message
        );
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleReservationNotFound(
            ReservationNotFoundException ex) {

        return Map.of(
                "error", ex.getMessage()
        );
    }

    @ExceptionHandler(UnauthorizedCancellationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleUnauthorizedCancellation(
            UnauthorizedCancellationException ex) {

        return Map.of(
                "error", ex.getMessage()
        );
    }

    @ExceptionHandler(SeatNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleSeatNotFound(
            SeatNotFoundException ex) {

        return Map.of(
                "error", ex.getMessage()
        );
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleUnauthorized(
            UnauthorizedException ex) {

        return Map.of(
                "error", ex.getMessage()
        );
    }
}