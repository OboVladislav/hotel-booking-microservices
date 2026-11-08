package com.example.booking;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.example.booking.BookingDtos.*;
@Service
public class BookingStateService {
 private static final Logger log=LoggerFactory.getLogger(BookingStateService.class);
 private final BookingRepository bookings;
 private final UserRepository users;
 public BookingStateService(BookingRepository bookings,UserRepository users){this.bookings=bookings;this.users=users;}
 public record Entry(BookingView booking,boolean created){}
 @Transactional public Entry createOrGet(Long userId,String requestId,CreateBooking dto){
  var old=bookings.findByRequestId(requestId);
  if(old.isPresent()){
   Booking b=old.get();
   if(!b.getUser().getId().equals(userId)||b.isAutoSelect()!=dto.autoSelect()
     ||!java.util.Objects.equals(b.getRequestedRoomId(),dto.autoSelect()?null:dto.roomId())
     ||!b.getStartDate().equals(dto.startDate())||!b.getEndDate().equals(dto.endDate()))
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Request id reused with different data");
   return new Entry(BookingView.from(b),false);
  }
  var user=users.findById(userId).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"User not found"));
  Booking b=bookings.saveAndFlush(new Booking(user,requestId,dto.autoSelect()?null:dto.roomId(),dto.autoSelect(),dto.startDate(),dto.endDate()));
  log.info("bookingId={} PENDING requestId={}",b.getId(),requestId);
  return new Entry(BookingView.from(b),true);
 }
 @Transactional public void assignRoom(Long id,Long roomId){
  var b=lock(id);if(b.getStatus()!=Booking.Status.PENDING)throw new ResponseStatusException(HttpStatus.CONFLICT,"Booking no longer pending");
  b.setRoomId(roomId);
 }
 @Transactional public BookingView confirm(Long id){
  var b=lock(id);
  if(b.getStatus()!=Booking.Status.PENDING)throw new ResponseStatusException(HttpStatus.CONFLICT,"Booking no longer pending");
  b.setStatus(Booking.Status.CONFIRMED);
  log.info("bookingId={} PENDING -> CONFIRMED",id);
  return BookingView.from(b);
 }
 @Transactional public BookingView cancel(Long id,boolean compensate){
  var b=lock(id);
  if(b.getStatus()!=Booking.Status.CANCELLED){
   log.info("bookingId={} {} -> CANCELLED",id,b.getStatus());
   b.setStatus(Booking.Status.CANCELLED);
   if(b.getRoomId()!=null){b.setReleasePending(true);b.setCompensating(compensate);}
  }
  return BookingView.from(b);
 }
 @Transactional public void released(Long id){
  var b=lock(id);b.setReleasePending(false);
  log.info("bookingId={} compensation/release completed",id);
 }
 @Transactional(readOnly=true) public BookingView get(Long id){
  return BookingView.from(bookings.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found")));
 }
 @Transactional(readOnly=true) public List<BookingView> history(Long userId){return bookings.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(BookingView::from).toList();}
 @Transactional(readOnly=true) public List<BookingView> pendingRelease(){return bookings.findByStatusAndReleasePendingTrue(Booking.Status.CANCELLED).stream().map(BookingView::from).toList();}
 @Transactional(readOnly=true) public boolean compensating(Long id){return bookings.findById(id).map(Booking::isCompensating).orElse(false);}
 private Booking lock(Long id){return bookings.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found"));}
}
