package com.example.hotel;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.example.hotel.HotelDtos.*;
@Service
public class HotelCatalogService {
 private final HotelRepository hotels;
 private final RoomRepository rooms;
 private final RoomSlotRepository slots;
 public HotelCatalogService(HotelRepository hotels,RoomRepository rooms,RoomSlotRepository slots){this.hotels=hotels;this.rooms=rooms;this.slots=slots;}
 @Transactional public Hotel createHotel(HotelInput dto){return hotels.save(new Hotel(dto.name(),dto.address()));}
 @Transactional(readOnly=true) public List<Hotel> hotels(){return hotels.findAll();}
 @Transactional public Hotel updateHotel(Long id,HotelInput dto){
  var hotel=hotels.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Hotel not found"));
  hotel.setName(dto.name());hotel.setAddress(dto.address());return hotel;
 }
 @Transactional public void deleteHotel(Long id){
  if(!hotels.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Hotel not found");
  if(rooms.findAll().stream().anyMatch(r->r.getHotel().getId().equals(id))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Delete hotel rooms first");
  hotels.deleteById(id);
 }
 @Transactional public RoomView createRoom(RoomInput dto){
  var hotel=hotels.findById(dto.hotelId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Hotel not found"));
  var room=new Room(hotel,dto.number());if(dto.available()!=null)room.setAvailable(dto.available());
  return RoomView.from(rooms.save(room));
 }
 @Transactional public RoomView updateRoom(Long id,RoomInput dto){
  var room=rooms.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Room not found"));
  var hotel=hotels.findById(dto.hotelId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Hotel not found"));
  room.setHotel(hotel);room.setNumber(dto.number());if(dto.available()!=null)room.setAvailable(dto.available());
  return RoomView.from(room);
 }
 @Transactional public void deleteRoom(Long id){
  if(!rooms.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Room not found");
  if(!slots.findActive(id,RoomSlot.State.RELEASED).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Room has active reservations");
  // Also remove released history before deleting room (demo in-memory schema).
  slots.deleteAll(slots.findAll().stream().filter(s->s.getRoom().getId().equals(id)).toList());
  rooms.deleteById(id);
 }
 @Transactional(readOnly=true) public List<RoomView> allRooms(){return rooms.findAll().stream().map(RoomView::from).toList();}
 @Transactional(readOnly=true) public List<RoomView> freeRooms(LocalDate start,LocalDate end,boolean recommended){
  checkDates(start,end);
  var free=rooms.findFree(start,end,java.util.List.of(RoomSlot.State.HELD,RoomSlot.State.CONFIRMED));
  if(recommended) free.sort(Comparator.comparingLong(Room::getTimesBooked).thenComparing(Room::getId));
  else free.sort(Comparator.comparing(Room::getId));
  return free.stream().map(RoomView::from).toList();
 }
 @Transactional(readOnly=true) public List<RoomStats> stats(){
  LocalDate today=LocalDate.now();
  return rooms.findAll().stream().map(r->new RoomStats(r.getId(),r.getTimesBooked(),slots.countOverlap(r.getId(),today,today.plusDays(1),java.util.List.of(RoomSlot.State.HELD,RoomSlot.State.CONFIRMED)),r.isAvailable())).toList();
 }
 public static void checkDates(LocalDate start,LocalDate end){
  if(start==null||end==null||start.isBefore(LocalDate.now())||!start.isBefore(end)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dates must satisfy today <= startDate < endDate");
 }
}
