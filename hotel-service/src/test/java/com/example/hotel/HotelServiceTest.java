package com.example.hotel;
import java.time.LocalDate;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.example.hotel.HotelDtos.*;
@SpringBootTest(properties={"eureka.client.enabled=false","security.jwt.secret=hotel-booking-test-secret-key-32-bytes-long"}) @AutoConfigureMockMvc
class HotelServiceTest {
 @Autowired HotelSlotService slots;
 @Autowired HotelCatalogService catalog;
 @Autowired RoomRepository rooms;
 @Autowired MockMvc mvc;
 @Test void concurrentOverlappingReservationsOnlyOneSucceeds() throws Exception {
  Long roomId=rooms.findAll().get(0).getId();
  LocalDate start=LocalDate.now().plusDays(70);
  ExecutorService pool=Executors.newFixedThreadPool(2);
  var gate=new CountDownLatch(1);
  Callable<Boolean> first=()->attempt(gate,roomId,9001L,start);
  Callable<Boolean> second=()->attempt(gate,roomId,9002L,start);
  try{
   Future<Boolean> a=pool.submit(first),b=pool.submit(second);
   gate.countDown();
   assertThat((a.get(10,TimeUnit.SECONDS)?1:0)+(b.get(10,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
  }finally{pool.shutdownNow();}
 }
 private boolean attempt(CountDownLatch gate,Long roomId,Long bookingId,LocalDate start) throws Exception {
  gate.await();
  try{slots.hold(roomId,new AvailabilityInput(bookingId,start,start.plusDays(2)));return true;}
  catch(org.springframework.web.server.ResponseStatusException | org.springframework.dao.DataAccessException ex){return false;}
 }
 @Test void repeatedHoldAndFinalizationAreIdempotent() {
  Long roomId=rooms.findAll().get(1).getId();
  LocalDate start=LocalDate.now().plusDays(110);
  long before=rooms.findById(roomId).orElseThrow().getTimesBooked();
  AvailabilityInput req=new AvailabilityInput(9003L,start,start.plusDays(2));
  slots.hold(roomId,req);slots.hold(roomId,req);
  slots.finalizeSlot(roomId,new ReleaseInput(9003L,false));
  slots.finalizeSlot(roomId,new ReleaseInput(9003L,false));
  assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(before+1);
  slots.release(roomId,new ReleaseInput(9003L,false));
  slots.release(roomId,new ReleaseInput(9003L,false));
  assertThat(catalog.freeRooms(start,start.plusDays(2),true)).extracting(RoomView::id).contains(roomId);
 }
 @Test void internalEndpointRejectsUserToken() throws Exception {
  mvc.perform(post("/api/rooms/1/release").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
   .contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":123,\"compensate\":true}"))
   .andExpect(status().isForbidden());
 }
 @Test void recommendationSortsByTimesBookedThenId() {
  var all=catalog.freeRooms(LocalDate.now().plusDays(200),LocalDate.now().plusDays(202),true);
  assertThat(all).isSortedAccordingTo(java.util.Comparator.comparingLong(RoomView::timesBooked).thenComparing(RoomView::id));
 }
}
