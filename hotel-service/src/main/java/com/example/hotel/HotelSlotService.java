package com.example.hotel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.example.hotel.HotelDtos.*;
@Service
public class HotelSlotService {
 private static final Logger log=LoggerFactory.getLogger(HotelSlotService.class);
 private final RoomRepository rooms;
 private final RoomSlotRepository slots;
 public HotelSlotService(RoomRepository rooms,RoomSlotRepository slots){this.rooms=rooms;this.slots=slots;}
 private String key(Long roomId,Long bookingId){return roomId+":"+bookingId;}
 @Transactional public void hold(Long roomId,AvailabilityInput dto){
  HotelCatalogService.checkDates(dto.startDate(),dto.endDate());
  Room room=rooms.lockById(roomId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Room not found"));
  String key=key(roomId,dto.bookingId());
  var existing=slots.findByReservationKey(key);
  if(existing.isPresent()){
   var slot=existing.get();
   if(slot.getState()==RoomSlot.State.RELEASED || !slot.getStartDate().equals(dto.startDate()) || !slot.getEndDate().equals(dto.endDate()))
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Reservation key already used");
   log.info("bookingId={} hotel hold replay",dto.bookingId());
   return;
  }
  if(!room.isAvailable() || slots.countOverlap(roomId,dto.startDate(),dto.endDate(),java.util.List.of(RoomSlot.State.HELD,RoomSlot.State.CONFIRMED))>0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Room occupied or unavailable");
  slots.save(new RoomSlot(key,dto.bookingId(),room,dto.startDate(),dto.endDate(),RoomSlot.State.HELD));
  log.info("bookingId={} hotel HELD roomId={}",dto.bookingId(),roomId);
 }
 @Transactional public void finalizeSlot(Long roomId,ReleaseInput dto){
  Room room=rooms.lockById(roomId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Room not found"));
  var slot=slots.findByReservationKey(key(roomId,dto.bookingId())).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Hold not found"));
  if(slot.getState()==RoomSlot.State.RELEASED) throw new ResponseStatusException(HttpStatus.CONFLICT,"Reservation released");
  if(slot.getState()==RoomSlot.State.HELD){
   slot.setState(RoomSlot.State.CONFIRMED);room.setTimesBooked(room.getTimesBooked()+1);
   log.info("bookingId={} hotel CONFIRMED roomId={}",dto.bookingId(),roomId);
  }
 }
 @Transactional public void release(Long roomId,ReleaseInput dto){
  Room room=rooms.lockById(roomId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Room not found"));
  String key=key(roomId,dto.bookingId());
  var found=slots.findByReservationKey(key);
  if(found.isEmpty()){
   // Tombstone prevents a late retried hold from recreating an already released reservation.
   slots.save(new RoomSlot(key,dto.bookingId(),room,LocalDateHolder.TODAY,LocalDateHolder.TOMORROW,RoomSlot.State.RELEASED));
  }else if(found.get().getState()!=RoomSlot.State.RELEASED){
   if(dto.compensate() && found.get().getState()==RoomSlot.State.CONFIRMED)
    room.setTimesBooked(Math.max(0,room.getTimesBooked()-1));
   found.get().setState(RoomSlot.State.RELEASED);
  }
  log.info("bookingId={} hotel RELEASED roomId={} compensate={}",dto.bookingId(),roomId,dto.compensate());
 }
 private static final class LocalDateHolder {
  static final java.time.LocalDate TODAY=java.time.LocalDate.of(2000,1,1);
  static final java.time.LocalDate TOMORROW=java.time.LocalDate.of(2000,1,2);
 }
}
