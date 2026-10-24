package com.salon.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salon.entity.UserEntity;

/**
 * Customer accounts (auth Phase 2). Email is stored lowercase (normalized
 * by AuthService); the IgnoreCase lookups are defense in depth, mirroring
 * BookingRepository's email handling.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	/** PHASE 5: token lookup for GET /api/auth/verify. Tokens are unique
	 *  CSPRNG values (64 hex chars), never user-chosen. */
	Optional<UserEntity> findByVerificationToken(String verificationToken);
}
