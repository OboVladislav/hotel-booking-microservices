package com.example.booking;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity @Table(name="bookings",uniqueConstraints=@UniqueConstraint(name="uk_booking_request",columnNames="request_id"),indexes={@Index(name="idx_booking_user",columnList="user_id"),@Index(name="idx_booking_room_dates",columnList="room_id,start_date,end_date")})
@Getter @Setter @NoArgsConstructor
public class Booking {
 public enum Status { PENDING,CONFIRMED,CANCELLED }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private AppUser user;
 @Column(name="room_id") private Long roomId;
 @Column(name="requested_room_id") private Long requestedRoomId;
 @Column(name="auto_select",nullable=false) private boolean autoSelect;
 @Column(name="request_id",nullable=false,unique=true) private String requestId;
 @Column(name="start_date",nullable=false) private LocalDate startDate;
 @Column(name="end_date",nullable=false) private LocalDate endDate;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="release_pending",nullable=false) private boolean releasePending;
 @Column(name="compensating",nullable=false) private boolean compensating;
 public Booking(AppUser user,String requestId,Long roomId,boolean autoSelect,LocalDate startDate,LocalDate endDate){
  this.user=user;this.requestId=requestId;this.requestedRoomId=roomId;this.autoSelect=autoSelect;
  this.startDate=startDate;this.endDate=endDate;this.status=Status.PENDING;this.createdAt=Instant.now();
 }
}
