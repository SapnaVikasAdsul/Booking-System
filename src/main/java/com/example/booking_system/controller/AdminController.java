package com.example.booking_system.controller;

import com.example.booking_system.config.SecurityConfig.BookingPrincipal;
import com.example.booking_system.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/bookings")
public class AdminController {

    private final BookingService bookings;

    public AdminController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable("id") long id, @AuthenticationPrincipal BookingPrincipal principal) {
        bookings.cancel(id, principal.id(), principal.isAdmin());
    }
}
