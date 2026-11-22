package com.example.booking_system.repository;

import java.util.Optional;

import com.example.booking_system.entity.Slot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from Slot slot where slot.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") long id);
}
