package com.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ShowResponse {

    private Long id;
    private String name;
    private Integer pricePaise;
    private Integer perUserLimit;

    private Integer totalSeats;
    private Integer availableSeats;
    private Integer confirmedSeats;

    private List<SeatResponse> seats;

    private Integer heldSeats;
}