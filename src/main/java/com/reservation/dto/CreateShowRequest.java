package com.reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateShowRequest {

    @NotBlank
    private String name;

    @NotEmpty
    private List<@NotBlank String> seats;

    @Positive
    @JsonProperty("price_paise")
    private Integer pricePaise;

    private Integer perUserLimit = 4;
}