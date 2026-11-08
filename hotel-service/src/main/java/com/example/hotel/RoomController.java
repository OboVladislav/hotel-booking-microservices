package com.example.hotel;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static com.example.hotel.HotelDtos.*;
@RestController @RequestMapping("/api/rooms")
public class RoomController {
 private final HotelCatalogService catalog;
 public RoomController(HotelCatalogService catalog){this.catalog=catalog;}
 @GetMapping @PreAuthorize("hasAnyRole('USER','ADMIN','SERVICE')") public List<RoomView> free(@RequestParam LocalDate startDate,@RequestParam LocalDate endDate){return catalog.freeRooms(startDate,endDate,false);}
 @GetMapping("/recommend") @PreAuthorize("hasAnyRole('USER','ADMIN','SERVICE')") public List<RoomView> recommended(@RequestParam LocalDate startDate,@RequestParam LocalDate endDate){return catalog.freeRooms(startDate,endDate,true);}
 @GetMapping("/all") @PreAuthorize("hasRole('ADMIN')") public List<RoomView> all(){return catalog.allRooms();}
 @GetMapping("/stats") @PreAuthorize("hasRole('ADMIN')") public List<RoomStats> stats(){return catalog.stats();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public RoomView create(@Valid @RequestBody RoomInput dto){return catalog.createRoom(dto);}
 @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public RoomView update(@PathVariable Long id,@Valid @RequestBody RoomInput dto){return catalog.updateRoom(id,dto);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void delete(@PathVariable Long id){catalog.deleteRoom(id);}
}
