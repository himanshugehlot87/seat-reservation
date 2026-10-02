package com.reservation.service;

import com.reservation.dto.CreateShowRequest;
import com.reservation.dto.SeatResponse;
import com.reservation.dto.ShowResponse;
import com.reservation.entity.Seat;
import com.reservation.entity.SeatStatus;
import com.reservation.entity.Show;
import com.reservation.exception.ShowNotFoundException;
import com.reservation.repository.SeatRepository;
import com.reservation.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public Show createShow(CreateShowRequest request) {

        Show show = new Show();
        show.setName(request.getName());
        show.setPricePaise(request.getPricePaise());
        show.setPerUserLimit(request.getPerUserLimit());

        show = showRepository.save(show);

        for (String seatNumber : request.getSeats()) {

            Seat seat = new Seat();
            seat.setShow(show);
            seat.setSeatNumber(seatNumber);

            seatRepository.save(seat);
        }

        return show;
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(Long showId) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException("Show not found"));

        List<Seat> seats = seatRepository.findByShowId(showId);

        ShowResponse response = new ShowResponse();

        response.setId(show.getId());
        response.setName(show.getName());
        response.setPricePaise(show.getPricePaise());
        response.setPerUserLimit(show.getPerUserLimit());

        response.setTotalSeats(seats.size());

        long available = seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE)
                .count();

        long confirmed = seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatus.CONFIRMED)
                .count();

        response.setAvailableSeats((int) available);
        response.setHeldSeats(0);
        response.setConfirmedSeats((int) confirmed);

        response.setSeats(
                seats.stream()
                        .map(seat -> new SeatResponse(
                                seat.getSeatNumber(),
                                seat.getStatus()))
                        .toList()
        );

        return response;
    }
}