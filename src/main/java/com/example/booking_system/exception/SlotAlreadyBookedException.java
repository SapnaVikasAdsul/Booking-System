package com.example.booking_system.exception;

public class SlotAlreadyBookedException extends RuntimeException {

    private final long slotId;

    public SlotAlreadyBookedException(long slotId) {
        super("Slot " + slotId + " is already booked.");
        this.slotId = slotId;
    }

    public long getSlotId() {
        return slotId;
    }
}
