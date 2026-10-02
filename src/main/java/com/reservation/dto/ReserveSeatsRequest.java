package com.reservation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReserveSeatsRequest {

    @NotEmpty
    private List<@NotBlank String> seats;
}