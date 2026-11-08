package com.example.hotel;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static com.example.hotel.HotelDtos.*;
@RestController @RequestMapping("/api/hotels")
public class HotelController {
 private final HotelCatalogService catalog;
 public HotelController(HotelCatalogService catalog){this.catalog=catalog;}
 @GetMapping @PreAuthorize("hasAnyRole('USER','ADMIN')") public List<Hotel> list(){return catalog.hotels();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public Hotel create(@Valid @RequestBody HotelInput dto){return catalog.createHotel(dto);}
 @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public Hotel update(@PathVariable Long id,@Valid @RequestBody HotelInput dto){return catalog.updateHotel(id,dto);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void delete(@PathVariable Long id){catalog.deleteHotel(id);}
}
