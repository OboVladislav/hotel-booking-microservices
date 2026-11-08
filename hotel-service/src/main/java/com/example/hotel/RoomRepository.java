package com.example.hotel;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
public interface RoomRepository extends JpaRepository<Room,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from Room r where r.id=:id") Optional<Room> lockById(Long id);
 @Query("select r from Room r where r.available=true and not exists " +
  "(select s.id from RoomSlot s where s.room=r and s.state in :active " +
  "and s.startDate < :end and s.endDate > :start)")
 List<Room> findFree(LocalDate start,LocalDate end,java.util.Collection<RoomSlot.State> active);
}
