package com.example.hotel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity @Table(name="hotels") @Getter @Setter @NoArgsConstructor
public class Hotel {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String name;
 @Column(nullable=false) private String address;
 public Hotel(String name,String address) {this.name=name;this.address=address;}
}
