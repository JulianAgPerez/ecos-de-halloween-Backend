package com.halloween.entities;

import com.halloween.repository.Token;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;
    @Column(unique = true)
    private String email;
    private String password;

    @OneToMany(mappedBy = "user")
    private List<Token> tokens;
}
