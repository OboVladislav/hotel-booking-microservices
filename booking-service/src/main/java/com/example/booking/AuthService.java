package com.example.booking;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.example.booking.BookingDtos.*;
@Service
public class AuthService {
 private final UserRepository users;
 private final JwtTokens tokens;
 private final PasswordEncoder encoder;
 public AuthService(UserRepository users,JwtTokens tokens,PasswordEncoder encoder){this.users=users;this.tokens=tokens;this.encoder=encoder;}
 @Transactional public TokenResponse register(Register dto){
  if(users.findByUsername(dto.username()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Username already used");
  var user=users.save(new AppUser(dto.username(),encoder.encode(dto.password()),AppUser.Role.USER));
  return new TokenResponse(tokens.user(user),"Bearer",3600);
 }
 @Transactional(readOnly=true) public TokenResponse authenticate(Credentials dto){
  var user=users.findByUsername(dto.username()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials"));
  if(!encoder.matches(dto.password(),user.getPassword())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
  return new TokenResponse(tokens.user(user),"Bearer",3600);
 }
}
@Configuration
class AuthSeed {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean CommandLineRunner initialAdmin(UserRepository users,PasswordEncoder encoder,
  @Value("${admin.username}") String adminName,@Value("${admin.password}") String adminPassword){
  return args->{
   if(users.findByUsername(adminName).isEmpty())users.save(new AppUser(adminName,encoder.encode(adminPassword),AppUser.Role.ADMIN));
   if(users.findByUsername("demo").isEmpty())users.save(new AppUser("demo",encoder.encode("demo123"),AppUser.Role.USER));
  };
 }
}
