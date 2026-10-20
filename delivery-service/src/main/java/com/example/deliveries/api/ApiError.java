package com.example.deliveries.api;

import java.time.Instant;

public record ApiError(String code, String message, Instant timestamp) {
}
