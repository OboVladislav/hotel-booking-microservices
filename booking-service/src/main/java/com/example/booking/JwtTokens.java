package com.example.booking;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
@Component
public class JwtTokens {
 private final JwtEncoder encoder;
 public JwtTokens(@Value("${security.jwt.secret}") String secret){
  byte[] key = secret.getBytes(StandardCharsets.UTF_8);
  if (key.length < 32) {
   throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes for HS256");
  }
  encoder=new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
 }
 private String create(String subject,String role,Long userId){
  Instant now=Instant.now();
  JwtClaimsSet.Builder claims=JwtClaimsSet.builder().issuer("hotel-booking-demo").subject(subject)
    .issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("role",role);
  if(userId!=null)claims.claim("userId",userId.toString());
  return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims.build())).getTokenValue();
 }
 public String user(AppUser user){return create(user.getUsername(),user.getRole().name(),user.getId());}
 public String service(){return create("booking-service","SERVICE",null);}
}
