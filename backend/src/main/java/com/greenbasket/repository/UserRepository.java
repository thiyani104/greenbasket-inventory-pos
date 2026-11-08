package com.greenbasket.repository;

import com.greenbasket.domain.Role;
import com.greenbasket.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameKey(String usernameKey);
    boolean existsByUsernameKey(String usernameKey);
    Optional<User> findByEmail(String email);
    long countByRoleAndActiveTrue(Role role);
    List<User> findAllByOrderByUsernameKeyAsc();
}
