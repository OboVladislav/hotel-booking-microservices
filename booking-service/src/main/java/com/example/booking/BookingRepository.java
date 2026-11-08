package com.example.booking;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
public interface BookingRepository extends JpaRepository<Booking,Long> {
 Optional<Booking> findByRequestId(String requestId);
 List<Booking> findAllByUserIdOrderByCreatedAtDesc(Long userId);
 List<Booking> findByStatusAndReleasePendingTrue(Booking.Status status);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from Booking b where b.id=:id") Optional<Booking> lockById(Long id);
}
