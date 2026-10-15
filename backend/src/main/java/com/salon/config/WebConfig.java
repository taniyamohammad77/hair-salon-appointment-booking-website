package com.salon.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Development CORS: the static frontend (opened from any localhost port)
 * may call the API. The frontend never touches the DB — it only fetches
 * JSON from /api/**.
 */
@Configuration
@EnableConfigurationProperties({ SalonProperties.class, AuthProperties.class })
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
				.allowedMethods("GET", "POST", "PUT")
				.allowedHeaders("*");
	}
}
