package com.example.booking_system.controller;

import com.example.booking_system.config.SecurityConfig.BookingPrincipal;
import com.example.booking_system.dto.BookingRequest;
import com.example.booking_system.dto.BookingResponse;
import com.example.booking_system.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(@Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal BookingPrincipal principal) {
        return bookings.book(request.getSlotId(), principal.id());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable("id") long id, @AuthenticationPrincipal BookingPrincipal principal) {
        bookings.cancel(id, principal.id(), principal.isAdmin());
    }
}
