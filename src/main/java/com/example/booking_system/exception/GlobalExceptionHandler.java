package com.example.booking_system.exception;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SlotAlreadyBookedException.class)
    public ProblemDetail handleAlreadyBooked(SlotAlreadyBookedException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Slot already booked");
        problem.setProperty("code", "SLOT_ALREADY_BOOKED");
        problem.setProperty("slotId", exception.getSlotId());
        return problem;
    }

    @ExceptionHandler(SlotAlreadyExistsException.class)
    public ProblemDetail handleDuplicateSlot(SlotAlreadyExistsException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Slot already exists");
        problem.setProperty("code", "SLOT_ALREADY_EXISTS");
        return problem;
    }

    @ExceptionHandler(BookingAlreadyCancelledException.class)
    public ProblemDetail handleAlreadyCancelled(BookingAlreadyCancelledException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Booking already cancelled");
        problem.setProperty("code", "BOOKING_ALREADY_CANCELLED");
        problem.setProperty("bookingId", exception.getBookingId());
        return problem;
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handleBookingLockConflict() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Another request is updating this slot or booking. Please retry.");
        problem.setTitle("Booking update in progress");
        problem.setProperty("code", "BOOKING_UPDATE_IN_PROGRESS");
        return problem;
    }
}
