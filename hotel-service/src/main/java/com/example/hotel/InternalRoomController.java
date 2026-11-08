package com.example.hotel;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static com.example.hotel.HotelDtos.*;
@RestController @RequestMapping("/api/rooms/{id}") @PreAuthorize("hasRole('SERVICE')")
public class InternalRoomController {
 private final HotelSlotService slots;
 public InternalRoomController(HotelSlotService slots){this.slots=slots;}
 @PostMapping("/confirm-availability") public void hold(@PathVariable Long id,@Valid @RequestBody AvailabilityInput dto){slots.hold(id,dto);}
 @PostMapping("/finalize") public void finalizeSlot(@PathVariable Long id,@Valid @RequestBody ReleaseInput dto){slots.finalizeSlot(id,dto);}
 @PostMapping("/release") public void release(@PathVariable Long id,@Valid @RequestBody ReleaseInput dto){slots.release(id,dto);}
}
