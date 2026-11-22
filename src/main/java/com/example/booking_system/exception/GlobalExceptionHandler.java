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

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handleBookingLockConflict() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Another request is updating this slot or booking. Please retry.");
        problem.setTitle("Booking update in progress");
        problem.setProperty("code", "BOOKING_UPDATE_IN_PROGRESS");
        return problem;
    }
}
