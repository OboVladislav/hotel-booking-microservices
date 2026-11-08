package com.example.booking;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static com.example.booking.BookingDtos.*;
@Service
public class BookingWorkflow {
 private static final Logger log=LoggerFactory.getLogger(BookingWorkflow.class);
 private final BookingStateService state;
 private final HotelClient hotels;
 public BookingWorkflow(BookingStateService state,HotelClient hotels){this.state=state;this.hotels=hotels;}
 public BookingView create(Long userId,String requestId,CreateBooking dto){
  if(requestId==null||requestId.isBlank()||requestId.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"X-Request-Id required (max 100 chars)");
  if(dto.startDate()==null||dto.endDate()==null||dto.startDate().isBefore(LocalDate.now())||!dto.startDate().isBefore(dto.endDate()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid booking dates");
  if(!dto.autoSelect()&&(dto.roomId()==null||dto.roomId()<=0))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"roomId required when autoSelect=false");
  BookingStateService.Entry entry;
  try{entry=state.createOrGet(userId,requestId,dto);}catch(DataIntegrityViolationException ex){
   // Concurrent delivery of the same requestId: winner has committed a row.
   entry=state.createOrGet(userId,requestId,dto);
  }
  BookingView initial=entry.booking();
  if(!entry.created())return initial;
  Long id=initial.id();
  List<Long> candidates;
  try{
   candidates=dto.autoSelect()?hotels.recommended(dto.startDate(),dto.endDate()).stream().map(RoomView::id).toList():List.of(dto.roomId());
  }catch(RuntimeException ex){state.cancel(id,true);throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Room lookup failed",ex);}
  for(Long roomId:candidates){
   state.assignRoom(id,roomId);
   try{
    hotels.hold(roomId,id,dto.startDate(),dto.endDate());
   }catch(HotelClient.SlotConflict ex){
    log.info("bookingId={} roomId={} occupied; trying next",id,roomId);
    if(dto.autoSelect())continue;
    state.cancel(id,true);safeRelease(id,roomId,true);
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Room already booked");
   }catch(RuntimeException ex){
    state.cancel(id,true);safeRelease(id,roomId,true);
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Hotel confirmation failed",ex);
   }
   try{
    hotels.finalizeSlot(roomId,id);
    return state.confirm(id);
   }catch(RuntimeException ex){
    state.cancel(id,true);safeRelease(id,roomId,true);
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Booking rolled back",ex);
   }
  }
  state.cancel(id,true);
  // The last candidate failed with a definitive 409 (no hold was created).
  throw new ResponseStatusException(HttpStatus.CONFLICT,"No rooms available on selected dates");
 }
 public BookingView cancel(Long id,Long userId,boolean admin){
  BookingView b=state.get(id);
  if(!admin&&!Objects.equals(b.userId(),userId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Not your booking");
  if(b.status()==Booking.Status.CANCELLED)return b;
  BookingView cancelled=state.cancel(id,false);
  if(cancelled.roomId()!=null)safeRelease(id,cancelled.roomId(),false);
  return cancelled;
 }
 public BookingView get(Long id,Long userId,boolean admin){
  var b=state.get(id);
  if(!admin&&!Objects.equals(b.userId(),userId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Not your booking");
  return b;
 }
 public List<BookingView> history(Long userId){return state.history(userId);}
 private void safeRelease(Long bookingId,Long roomId,boolean compensate){
  try{hotels.release(roomId,bookingId,compensate);state.released(bookingId);}
  catch(RuntimeException ex){log.warn("bookingId={} release failed; scheduled retry: {}",bookingId,ex.toString());}
 }
 @Scheduled(fixedDelay=10000,initialDelay=10000) public void retryReleases(){
  for(var b:state.pendingRelease())if(b.roomId()!=null)safeRelease(b.id(),b.roomId(),state.compensating(b.id()));
 }
}
