package com.example.hotel;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface RoomSlotRepository extends JpaRepository<RoomSlot,Long> {
 Optional<RoomSlot> findByReservationKey(String key);
 @Query("select count(s) from RoomSlot s where s.room.id=:roomId and s.state in :active and s.startDate < :end and s.endDate > :start")
 long countOverlap(Long roomId,LocalDate start,LocalDate end,java.util.Collection<RoomSlot.State> active);
 @Query("select s from RoomSlot s where s.room.id=:roomId and s.state <> :released")
 List<RoomSlot> findActive(Long roomId,RoomSlot.State released);
}
