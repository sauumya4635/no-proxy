package com.noproxy.noproxy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.noproxy.noproxy.model.Role;
import com.noproxy.noproxy.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // ✅ Fetch users by Role enum (works with @Enumerated in entity)
    List<User> findByRole(Role role);
}
