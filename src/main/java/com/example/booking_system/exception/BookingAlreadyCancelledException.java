package com.example.booking_system.exception;

public class BookingAlreadyCancelledException extends RuntimeException {

    private final long bookingId;

    public BookingAlreadyCancelledException(long bookingId) {
        super("Booking " + bookingId + " is already cancelled.");
        this.bookingId = bookingId;
    }

    public long getBookingId() {
        return bookingId;
    }
}
