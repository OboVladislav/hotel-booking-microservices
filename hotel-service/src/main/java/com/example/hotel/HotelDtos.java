package com.example.hotel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
public final class HotelDtos {
 private HotelDtos() {}
 public record HotelInput(@NotBlank String name,@NotBlank String address) {}
 public record RoomInput(@NotNull Long hotelId,@NotBlank String number,Boolean available) {}
 public record AvailabilityInput(@NotNull Long bookingId,@NotNull LocalDate startDate,@NotNull LocalDate endDate) {}
 public record ReleaseInput(@NotNull Long bookingId,boolean compensate) {}
 public record RoomView(Long id,Long hotelId,String hotelName,String number,boolean available,long timesBooked) {
  public static RoomView from(Room room) {return new RoomView(room.getId(),room.getHotel().getId(),room.getHotel().getName(),room.getNumber(),room.isAvailable(),room.getTimesBooked());}
 }
 public record RoomStats(Long roomId,long timesBooked,long activeSlots,boolean available) {}
}
