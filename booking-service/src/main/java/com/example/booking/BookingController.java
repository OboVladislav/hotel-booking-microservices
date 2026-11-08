package com.example.booking;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.example.booking.BookingDtos.*;
@RestController
public class BookingController {
 private final BookingWorkflow workflow;
 public BookingController(BookingWorkflow workflow){this.workflow=workflow;}
 private Long userId(Jwt jwt){return Long.valueOf(jwt.getClaimAsString("userId"));}
 private boolean admin(Jwt jwt){return "ADMIN".equals(jwt.getClaimAsString("role"));}
 @PostMapping({"/api/booking","/booking"}) @ResponseStatus(HttpStatus.CREATED)
 @PreAuthorize("hasAnyRole('USER','ADMIN')")
 public BookingView book(@AuthenticationPrincipal Jwt jwt,@RequestHeader("X-Request-Id") String requestId,@Valid @RequestBody CreateBooking dto){
  return workflow.create(userId(jwt),requestId,dto);
 }
 @GetMapping({"/api/bookings","/bookings"}) @PreAuthorize("hasAnyRole('USER','ADMIN')")
 public List<BookingView> history(@AuthenticationPrincipal Jwt jwt){return workflow.history(userId(jwt));}
 @GetMapping({"/api/booking/{id}","/booking/{id}"}) @PreAuthorize("hasAnyRole('USER','ADMIN')")
 public BookingView get(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){return workflow.get(id,userId(jwt),admin(jwt));}
 @DeleteMapping({"/api/booking/{id}","/booking/{id}"}) @PreAuthorize("hasAnyRole('USER','ADMIN')")
 public BookingView cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){return workflow.cancel(id,userId(jwt),admin(jwt));}
}
