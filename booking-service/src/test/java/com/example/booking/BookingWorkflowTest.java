package com.example.booking;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.example.booking.BookingDtos.*;
@SpringBootTest(properties={"eureka.client.enabled=false","security.jwt.secret=hotel-booking-test-secret-key-32-bytes-long"}) @AutoConfigureMockMvc
class BookingWorkflowTest {
 @Autowired BookingWorkflow workflow;
 @Autowired BookingRepository bookings;
 @Autowired UserRepository users;
 @Autowired MockMvc mvc;
 @MockitoBean HotelClient hotels;
 private Long uid(){return users.findByUsername("demo").orElseThrow().getId();}
 private CreateBooking request(){return new CreateBooking(LocalDate.now().plusDays(10),LocalDate.now().plusDays(12),false,1L);}
 @Test void successAndDuplicateRequestHaveSameBookingId() {
  String key=UUID.randomUUID().toString();
  var first=workflow.create(uid(),key,request());
  var repeated=workflow.create(uid(),key,request());
  assertThat(first.status()).isEqualTo(Booking.Status.CONFIRMED);
  assertThat(repeated.id()).isEqualTo(first.id());
  verify(hotels,times(1)).hold(eq(1L),eq(first.id()),any(),any());
 }
 @Test void remoteErrorCancelsAndCompensates() {
  String key=UUID.randomUUID().toString();
  doThrow(new ResourceAccessException("timeout")).when(hotels).hold(eq(1L),anyLong(),any(),any());
  assertThatThrownBy(()->workflow.create(uid(),key,request()))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  var b=bookings.findByRequestId(key).orElseThrow();
  assertThat(b.getStatus()).isEqualTo(Booking.Status.CANCELLED);
  verify(hotels).release(eq(1L),eq(b.getId()),eq(true));
  assertThat(workflow.create(uid(),key,request()).id()).isEqualTo(b.getId());
 }
 @Test void remoteConflictReturns409AndCancels() {
  String key=UUID.randomUUID().toString();
  doThrow(new HotelClient.SlotConflict()).when(hotels).hold(eq(1L),anyLong(),any(),any());
  assertThatThrownBy(()->workflow.create(uid(),key,request()))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
   .hasMessageContaining("409");
  assertThat(bookings.findByRequestId(key).orElseThrow().getStatus()).isEqualTo(Booking.Status.CANCELLED);
 }
 @Test void unauthorizedCreationReturns401() throws Exception {
  mvc.perform(post("/api/booking").header("X-Request-Id",UUID.randomUUID())
   .contentType(MediaType.APPLICATION_JSON).content("{\"autoSelect\":false,\"roomId\":1}"))
   .andExpect(status().isUnauthorized());
 }
 @Test void ordinaryUserCannotPerformAdminAction() throws Exception {
  mvc.perform(get("/api/user").with(jwt().jwt(j->j.claim("userId",uid().toString()).claim("role","USER"))
   .authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
 }
 @Test void invalidDatesReturn400() throws Exception {
  String body="{\"startDate\":\"2020-01-01\",\"endDate\":\"2020-01-02\",\"autoSelect\":false,\"roomId\":1}";
  mvc.perform(post("/api/booking").with(jwt().jwt(j->j.claim("userId",uid().toString()).claim("role","USER"))
   .authorities(new SimpleGrantedAuthority("ROLE_USER")))
   .header("X-Request-Id",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body))
   .andExpect(status().isBadRequest());
 }
 @Test void hotelServerFailureCancelsAndCompensates() {
  String key=UUID.randomUUID().toString();
  doThrow(new HotelClient.RemoteFailure(new IllegalStateException("HTTP 503"))).when(hotels).finalizeSlot(eq(1L),anyLong());
  assertThatThrownBy(()->workflow.create(uid(),key,request()))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  var b=bookings.findByRequestId(key).orElseThrow();
  assertThat(b.getStatus()).isEqualTo(Booking.Status.CANCELLED);
  verify(hotels).release(eq(1L),eq(b.getId()),eq(true));
 }
 @Test void foreignBookingCannotBeReadOrCancelled() {
  var created=workflow.create(uid(),UUID.randomUUID().toString(),request());
  assertThatThrownBy(()->workflow.get(created.id(),-999L,false))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("403");
  assertThatThrownBy(()->workflow.cancel(created.id(),-999L,false))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("403");
 }

}
