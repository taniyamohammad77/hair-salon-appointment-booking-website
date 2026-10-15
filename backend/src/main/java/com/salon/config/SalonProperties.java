package com.salon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable settings from application.yml ({@code salon.*}).
 *
 * @param slotStepMinutes granularity of the availability slot grid in minutes
 */
@ConfigurationProperties(prefix = "salon")
public record SalonProperties(int slotStepMinutes) {

	public SalonProperties {
		if (slotStepMinutes <= 0) {
			slotStepMinutes = 15;
		}
	}
}
