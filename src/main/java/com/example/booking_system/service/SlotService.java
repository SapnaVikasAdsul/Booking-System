package com.example.booking_system.service;

import java.util.List;

import com.example.booking_system.dto.SlotRequest;
import com.example.booking_system.dto.SlotResponse;
import com.example.booking_system.entity.Slot;
import com.example.booking_system.repository.SlotRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SlotService {

    private final SlotRepository slots;

    public SlotService(SlotRepository slots) {
        this.slots = slots;
    }

    @Transactional
    public SlotResponse create(SlotRequest request) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must precede endTime");
        }
        return SlotResponse.from(slots.save(new Slot(request.getStartTime(), request.getEndTime())));
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> list() {
        return slots.findAll(Sort.by("startTime", "id")).stream()
                .map(SlotResponse::from)
                .toList();
    }
}
