package com.example.booking_system.exception;

public class SlotAlreadyExistsException extends RuntimeException {

    public SlotAlreadyExistsException() {
        super("A slot already exists with this start and end time.");
    }
}
