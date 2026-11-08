package com.example.booking;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"eureka.client.enabled=false","security.jwt.secret=hotel-booking-test-secret-key-32-bytes-long"}) @AutoConfigureMockMvc
class AuthApiTest {
 @Autowired MockMvc mvc;
 @Test void registersUserAndReturnsJwt() throws Exception {
  String name="user"+UUID.randomUUID().toString().replace("-","").substring(0,10);
  mvc.perform(post("/api/user/register").contentType(MediaType.APPLICATION_JSON)
   .content("{\"username\":\""+name+"\",\"password\":\"secret123\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
 }
 @Test void rejectsWrongPassword() throws Exception {
  mvc.perform(post("/api/user/auth").contentType(MediaType.APPLICATION_JSON)
   .content("{\"username\":\"demo\",\"password\":\"invalid\"}"))
   .andExpect(status().isUnauthorized());
 }
}
