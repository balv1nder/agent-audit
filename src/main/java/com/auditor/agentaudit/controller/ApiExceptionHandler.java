package com.auditor.agentaudit.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * One place that catches exceptions thrown by any controller and turns them
 * into a structured 400 response.
 *
 * <p>{@code FinancialService} throws {@link IllegalArgumentException} when a
 * portfolio or date does not exist. Left alone, Spring would report that as a
 * 500 -- a server fault. It isn't: the client asked for data we don't have,
 * which is a bad request.
 */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> onBadArgument(IllegalArgumentException ex) {
        log.error("[ApiExceptionHandler::onBadArgument]::rejecting request as a bad request with exception: {}",
                ex.getMessage());

        ApiError body = new ApiError(Instant.now(), 400, "Bad Request", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
