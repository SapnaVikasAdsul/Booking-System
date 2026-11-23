package com.example.booking_system.repository;

import java.time.Instant;
import java.util.Optional;

import com.example.booking_system.entity.Slot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    @Query(value = "select id from slot_creation_lock where id = 1 for update", nativeQuery = true)
    int lockSlotCreation();

    @Query(value = """
            select exists (
                select 1 from slots
                where start_time = cast(:startTime as timestamp(6) with time zone)
                  and end_time = cast(:endTime as timestamp(6) with time zone)
            )
            """, nativeQuery = true)
    boolean existsForTimeRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from Slot slot where slot.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") long id);
}
