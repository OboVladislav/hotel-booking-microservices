package com.example.hotel;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity @Table(name="room_slots",uniqueConstraints=@UniqueConstraint(columnNames="reservation_key"),indexes=@Index(name="idx_room_slot_dates",columnList="room_id,start_date,end_date"))
@Getter @Setter @NoArgsConstructor
public class RoomSlot {
 public enum State { HELD, CONFIRMED, RELEASED }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="reservation_key",nullable=false,unique=true) private String reservationKey;
 @Column(name="booking_id",nullable=false) private Long bookingId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="room_id") private Room room;
 @Column(name="start_date",nullable=false) private LocalDate startDate;
 @Column(name="end_date",nullable=false) private LocalDate endDate;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private State state;
 public RoomSlot(String key,Long bookingId,Room room,LocalDate start,LocalDate end,State state){
  this.reservationKey=key;this.bookingId=bookingId;this.room=room;this.startDate=start;this.endDate=end;this.state=state;
 }
}
