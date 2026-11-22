package com.example.booking_system.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public class SlotRequest {

    @NotNull
    private Instant startTime;

    @NotNull
    private Instant endTime;

    public SlotRequest() {
    }

    public SlotRequest(Instant startTime, Instant endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
}
