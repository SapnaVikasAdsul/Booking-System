package com.example.booking_system.dto;

import java.time.Instant;

import com.example.booking_system.entity.Booking;

public class BookingResponse {

    private Long id;
    private Long slotId;
    private Long userId;
    private Booking.Status status;
    private Instant createdAt;

    public BookingResponse() {
    }

    public BookingResponse(Long id, Long slotId, Long userId, Booking.Status status, Instant createdAt) {
        this.id = id;
        this.slotId = slotId;
        this.userId = userId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getSlot().getId(),
                booking.getUserId(),
                booking.getStatus(),
                booking.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Booking.Status getStatus() {
        return status;
    }

    public void setStatus(Booking.Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
