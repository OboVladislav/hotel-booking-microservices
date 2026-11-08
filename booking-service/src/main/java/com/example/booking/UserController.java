package com.example.booking;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static com.example.booking.BookingDtos.*;
@RestController @RequestMapping({"/api/user","/user"})
public class UserController {
 private final AuthService auth;
 private final UserRepository users;
 private final PasswordEncoder encoder;
 public UserController(AuthService auth,UserRepository users,PasswordEncoder encoder){this.auth=auth;this.users=users;this.encoder=encoder;}
 @PostMapping("/register") public TokenResponse register(@Valid @RequestBody Register dto){return auth.register(dto);}
 @PostMapping("/auth") public TokenResponse login(@Valid @RequestBody Credentials dto){return auth.authenticate(dto);}
 @GetMapping @PreAuthorize("hasRole('ADMIN')") public List<UserView> all(){return users.findAll().stream().map(UserView::from).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public UserView create(@Valid @RequestBody UserInput dto){
  if(users.findByUsername(dto.username()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Username already used");
  return UserView.from(users.save(new AppUser(dto.username(),encoder.encode(dto.password()),dto.role())));
 }
 @PatchMapping("/{id}") @Transactional @PreAuthorize("hasRole('ADMIN')") public UserView update(@PathVariable Long id,@RequestBody UserUpdate dto){
  var user=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));
  if(dto.username()!=null){
   if(dto.username().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Username required");
   user.setUsername(dto.username());
  }
  if(dto.password()!=null){
   if(dto.password().length()<6)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password too short");
   user.setPassword(encoder.encode(dto.password()));
  }
  if(dto.role()!=null)user.setRole(dto.role());
  return UserView.from(user);
 }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void remove(@PathVariable Long id){
  if(!users.existsById(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found");
  if(id.equals(1L))throw new ResponseStatusException(HttpStatus.CONFLICT,"Seed admin cannot be deleted");
  users.deleteById(id);
 }
}
