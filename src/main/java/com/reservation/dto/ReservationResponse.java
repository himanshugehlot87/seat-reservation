package com.reservation.dto;

import com.reservation.entity.ReservationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReservationResponse {

    @JsonProperty("reservation_id")
    private Long reservationId;

    @JsonProperty("show_id")
    private Long showId;

    @JsonProperty("user_id")
    private String userId;

    private List<String> seats;

    @JsonProperty("amount_paise")
    private Integer amountPaise;

    private ReservationStatus status;
}