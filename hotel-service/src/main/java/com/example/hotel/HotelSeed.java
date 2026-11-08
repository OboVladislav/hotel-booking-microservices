package com.example.hotel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class HotelSeed {
 @Bean CommandLineRunner hotelsAndRooms(HotelRepository hotels,RoomRepository rooms){return args->{
  if(hotels.count()!=0)return;
  var h1=hotels.save(new Hotel("Central Hotel","Main Street, 1"));
  var h2=hotels.save(new Hotel("Park Hotel","Park Avenue, 7"));
  rooms.save(new Room(h1,"101"));rooms.save(new Room(h1,"102"));
  rooms.save(new Room(h2,"201"));rooms.save(new Room(h2,"202"));
 };}
}
