package com.example.booking;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity @Table(name="users",uniqueConstraints=@UniqueConstraint(columnNames="username")) @Getter @Setter @NoArgsConstructor
public class AppUser {
 public enum Role {USER,ADMIN}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=100) private String username;
 @Column(nullable=false) private String password;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role;
 public AppUser(String name,String password,Role role){this.username=name;this.password=password;this.role=role;}
}
