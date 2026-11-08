package com.example.gateway;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import java.nio.charset.StandardCharsets;
import java.util.List;
@Configuration
public class GatewaySecurity {
 @Bean ReactiveJwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret) {
  return NimbusReactiveJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
 }
 @Bean SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
  var converter=new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
  converter.setJwtGrantedAuthoritiesConverter(jwt -> {
    String role=jwt.getClaimAsString("role");
    return role==null?List.of():List.of(new SimpleGrantedAuthority("ROLE_"+role));
  });
  return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
    .authorizeExchange(auth -> auth
      .pathMatchers("/api/rooms/*/confirm-availability", "/api/rooms/*/finalize", "/api/rooms/*/release").denyAll()
      .pathMatchers(HttpMethod.POST,"/api/user/register","/api/user/auth","/user/register","/user/auth").permitAll()
      .pathMatchers("/actuator/health").permitAll()
      .anyExchange().hasAnyRole("USER","ADMIN"))
    .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter))))
    .build();
 }
}
