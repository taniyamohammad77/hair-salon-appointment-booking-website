package com.salon.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A customer account (AUTH PHASE 2). Maps to dbo.users created by
 * scripts/schema.sql (auth Phase 1). Bookings still link to accounts BY
 * EMAIL only — no FK, and nothing in the booking flow reads this table
 * yet (Phase 3 wires ownership/authorization; this phase is auth-only).
 *
 * SECURITY: {@code passwordHash} holds a BCrypt hash only — the plaintext
 * password never reaches this entity, the DB, or any response.
 */
@Entity
@Table(name = "users")
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "email", nullable = false, length = 160)
	private String email;

	/** BCrypt hash (60 chars); column has headroom for future formats. */
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	/**
	 * PHASE 5A legacy gate: false (default) = bookings created BEFORE this
	 * account are hidden until the salon verifies the address (phone /
	 * in person) and flips this flag. The account's own bookings (created
	 * at/after account creation) are always visible.
	 */
	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(boolean emailVerified) {
		this.emailVerified = emailVerified;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
