package com.auditor.agentaudit.controller;

import java.time.Instant;

/** Uniform error body, so a failure is as inspectable as a success. */
public record ApiError(Instant timestamp, int status, String error, String message) {}
