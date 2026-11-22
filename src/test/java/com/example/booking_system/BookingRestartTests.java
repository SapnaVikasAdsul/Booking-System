package com.example.booking_system;

import java.nio.file.Path;
import java.time.Instant;

import com.example.booking_system.dto.SlotRequest;
import com.example.booking_system.entity.Slot;
import com.example.booking_system.exception.SlotAlreadyBookedException;
import com.example.booking_system.service.BookingService;
import com.example.booking_system.service.SlotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingRestartTests {

    @Test
    void persistedBookingStillBlocksTheSlotAfterRestart(@TempDir Path directory) {
        String databaseUrl = "jdbc:h2:file:" + directory.resolve("booking").toString().replace('\\', '/');
        long slotId;
        long bookingId;
        try (var app = start(databaseUrl)) {
            slotId = app.getBean(SlotService.class).create(new SlotRequest(
                    Instant.parse("2030-01-01T10:00:00Z"),
                    Instant.parse("2030-01-01T11:00:00Z"))).getId();
            bookingId = app.getBean(BookingService.class).book(slotId, 1L).getId();
        }

        try (var restarted = start(databaseUrl)) {
            var bookings = restarted.getBean(BookingService.class);
            var slots = restarted.getBean(SlotService.class);
            assertThat(slots.list()).singleElement().satisfies(slot ->
                    assertThat(slot.getStatus()).isEqualTo(Slot.Status.BOOKED));
            assertThatThrownBy(() -> bookings.book(slotId, 3L))
                    .isInstanceOf(SlotAlreadyBookedException.class);

            bookings.cancel(bookingId, 1L, false);
            assertThat(slots.list()).singleElement().satisfies(slot ->
                    assertThat(slot.getStatus()).isEqualTo(Slot.Status.AVAILABLE));
        }
    }

    private ConfigurableApplicationContext start(String databaseUrl) {
        return new SpringApplicationBuilder(BookingSystemApplication.class).run(
                "--spring.datasource.url=" + databaseUrl,
                "--spring.jpa.hibernate.ddl-auto=update",
                "--spring.jpa.show-sql=false",
                "--server.address=127.0.0.1",
                "--server.port=0");
    }
}
