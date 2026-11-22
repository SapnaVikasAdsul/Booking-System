package com.example.booking_system.dto;

import java.time.Instant;

import com.example.booking_system.entity.Slot;

public class SlotResponse {

    private Long id;
    private Instant startTime;
    private Instant endTime;
    private Slot.Status status;

    public SlotResponse() {
    }

    public SlotResponse(Long id, Instant startTime, Instant endTime, Slot.Status status) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public static SlotResponse from(Slot slot) {
        return new SlotResponse(slot.getId(), slot.getStartTime(), slot.getEndTime(), slot.getStatus());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Slot.Status getStatus() {
        return status;
    }

    public void setStatus(Slot.Status status) {
        this.status = status;
    }
}
