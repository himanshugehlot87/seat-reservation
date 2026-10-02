package com.reservation.dto;

import com.reservation.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SeatResponse {

    private String seatNumber;
    private SeatStatus status;
}