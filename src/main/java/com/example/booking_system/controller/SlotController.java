package com.example.booking_system.controller;

import java.util.List;

import com.example.booking_system.dto.SlotRequest;
import com.example.booking_system.dto.SlotResponse;
import com.example.booking_system.service.SlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/slots")
public class SlotController {

    private final SlotService slots;

    public SlotController(SlotService slots) {
        this.slots = slots;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse create(@Valid @RequestBody SlotRequest request) {
        return slots.create(request);
    }

    @GetMapping
    public List<SlotResponse> list() {
        return slots.list();
    }
}
