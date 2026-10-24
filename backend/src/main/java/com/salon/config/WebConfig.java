package com.salon.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Development CORS: the static frontend (opened from any localhost port)
 * may call the API. The frontend never touches the DB — it only fetches
 * JSON from /api/**.
 */
@Configuration
@EnableConfigurationProperties({ SalonProperties.class, AuthProperties.class })
public class WebConfig implements WebMvcConfigurer {

	private final RateLimitInterceptor rateLimitInterceptor;

	public WebConfig(RateLimitInterceptor rateLimitInterceptor) {
		this.rateLimitInterceptor = rateLimitInterceptor;
	}

	/** SECURITY: rate-limit the sensitive WRITE endpoints only; public
	 *  catalogue/availability reads and GET /api/bookings stay open.
	 *  PHASE 5: resend-verification dispatches email → limited too. */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitInterceptor)
				.addPathPatterns("/api/auth/login", "/api/auth/signup",
						"/api/auth/resend-verification", "/api/bookings/**");
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
				.allowedMethods("GET", "POST", "PUT")
				.allowedHeaders("*");
	}
}
