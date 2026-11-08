package com.example.booking;
import java.time.Instant;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
public final class BookingDtos {
 private BookingDtos(){}
 public record Credentials(@NotBlank String username,@NotBlank String password) {}
 public record Register(@NotBlank String username,@Size(min=6) String password) {}
 public record UserInput(@NotBlank String username,@Size(min=6) String password,@NotNull AppUser.Role role) {}
 public record UserUpdate(String username,String password,AppUser.Role role) {}
 public record UserView(Long id,String username,AppUser.Role role){
  public static UserView from(AppUser user){return new UserView(user.getId(),user.getUsername(),user.getRole());}
 }
 public record TokenResponse(String accessToken,String tokenType,int expiresInSeconds){}
 public record CreateBooking(@NotNull LocalDate startDate,@NotNull LocalDate endDate,boolean autoSelect,Long roomId){}
 public record BookingView(Long id,Long userId,Long roomId,LocalDate startDate,LocalDate endDate,Booking.Status status,Instant createdAt,String requestId){
  public static BookingView from(Booking booking){return new BookingView(booking.getId(),booking.getUser().getId(),booking.getRoomId(),booking.getStartDate(),booking.getEndDate(),booking.getStatus(),booking.getCreatedAt(),booking.getRequestId());}
 }
 public record RoomView(Long id,Long hotelId,String hotelName,String number,boolean available,long timesBooked){}
 public record SlotRequest(Long bookingId,LocalDate startDate,LocalDate endDate){}
 public record ReleaseRequest(Long bookingId,boolean compensate){}
}
