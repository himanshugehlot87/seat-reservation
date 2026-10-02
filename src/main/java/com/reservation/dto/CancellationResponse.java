package com.reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CancellationResponse {

    @JsonProperty("reservation_id")
    private Long reservationId;

    private String status;

    private String message;
}