package com.example.booking_system.service;

import java.time.Instant;
import java.util.List;

import com.example.booking_system.dto.SlotRequest;
import com.example.booking_system.dto.SlotResponse;
import com.example.booking_system.entity.Slot;
import com.example.booking_system.exception.SlotAlreadyExistsException;
import com.example.booking_system.repository.SlotRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SlotService {

    private final SlotRepository slots;

    public SlotService(SlotRepository slots) {
        this.slots = slots;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SlotResponse create(SlotRequest request) {
        Instant startTime = request.getStartTime();
        Instant endTime = request.getEndTime();
        if (!startTime.isBefore(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must precede endTime");
        }

        slots.lockSlotCreation();
        if (slots.existsForTimeRange(startTime, endTime)) {
            throw new SlotAlreadyExistsException();
        }

        return SlotResponse.from(slots.save(new Slot(startTime, endTime)));
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> list() {
        return slots.findAll(Sort.by("startTime", "id")).stream()
                .map(SlotResponse::from)
                .toList();
    }
}
