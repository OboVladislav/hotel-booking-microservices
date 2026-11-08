package com.example.hotel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity @Table(name="rooms",uniqueConstraints=@UniqueConstraint(columnNames={"hotel_id","number"}),indexes=@Index(name="idx_room_hotel",columnList="hotel_id"))
@Getter @Setter @NoArgsConstructor
public class Room {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="hotel_id") private Hotel hotel;
 @Column(nullable=false) private String number;
 @Column(nullable=false) private boolean available=true;
 @Column(name="times_booked",nullable=false) private long timesBooked=0;
 public Room(Hotel hotel,String number){this.hotel=hotel;this.number=number;}
}
