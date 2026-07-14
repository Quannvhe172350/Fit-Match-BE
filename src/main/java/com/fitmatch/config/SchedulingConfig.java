package com.fitmatch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật scheduler cho các job nền: hết hạn thanh toán (UC-054), giải ngân (UC-059). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
