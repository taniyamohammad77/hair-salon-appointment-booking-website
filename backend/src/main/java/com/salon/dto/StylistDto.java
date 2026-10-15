package com.salon.dto;

import com.salon.entity.StylistEntity;

public record StylistDto(
		Long id,
		String name,
		String role,
		String bio,
		String avatarUrl) {

	public static StylistDto from(StylistEntity e) {
		return new StylistDto(e.getId(), e.getName(), e.getRole(), e.getBio(), e.getAvatarUrl());
	}
}
