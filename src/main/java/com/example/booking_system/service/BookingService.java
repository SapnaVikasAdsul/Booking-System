package com.example.booking_system.service;

import com.example.booking_system.dto.BookingResponse;
import com.example.booking_system.entity.Booking;
import com.example.booking_system.entity.Slot;
import com.example.booking_system.exception.BookingNotFoundException;
import com.example.booking_system.exception.SlotAlreadyBookedException;
import com.example.booking_system.repository.BookingRepository;
import com.example.booking_system.repository.SlotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final SlotRepository slots;

    public BookingService(BookingRepository bookings, SlotRepository slots) {
        this.bookings = bookings;
        this.slots = slots;
    }

    @Transactional
    public BookingResponse book(long slotId, long userId) {
        Slot slot = lockSlot(slotId);
        if (slot.getStatus() == Slot.Status.BOOKED) {
            throw new SlotAlreadyBookedException(slotId);
        }

        slot.setStatus(Slot.Status.BOOKED);
        return BookingResponse.from(bookings.save(new Booking(slot, userId)));
    }

    @Transactional
    public void cancel(long bookingId, long userId, boolean admin) {
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!admin && booking.getUserId() != userId) {
            throw new AccessDeniedException("You can only cancel your own bookings");
        }
        if (booking.getStatus() == Booking.Status.CANCELLED) {
            return;
        }

        Slot slot = lockSlot(booking.getSlot().getId());
        booking.setStatus(Booking.Status.CANCELLED);
        slot.setStatus(Slot.Status.AVAILABLE);
    }

    private Slot lockSlot(long slotId) {
        return slots.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot was not found"));
    }
}
