package com.reservation.controller;

import com.reservation.dto.CreateShowRequest;
import com.reservation.dto.ShowResponse;
import com.reservation.entity.Show;
import com.reservation.service.ShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Show createShow(
            @Valid @RequestBody CreateShowRequest request) {

        return showService.createShow(request);
    }

    @GetMapping("/{showId}")
    public ShowResponse getShow(@PathVariable Long showId) {
        return showService.getShow(showId);
    }
}