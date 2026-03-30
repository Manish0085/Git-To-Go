package com.git2go.platform.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Consistent API Response Wrapper — saare responses same format me jaayenge.
 *
 * Success: { "success": true, "message": "...", "data": {...}, "timestamp": "..." }
 * Error:   { "success": false, "message": "...", "data": null, "timestamp": "..." }
 *
 * @JsonInclude(NON_NULL) — agar data null hai toh JSON me dikhega hi nahi (clean response)
 *
 * Interview: "Consistent API response wrapper use kiya hai — frontend ko hamesha
 * same structure milta hai. success flag se pata chalta hai request pass hui ya fail.
 * Generic type <T> se kisi bhi data type ke saath kaam karta hai."
 */
@Getter
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("Success", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
