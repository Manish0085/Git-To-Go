package com.git2go.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Generic paged response — kisi bhi entity ke liye reuse ho sakta hai.
 *
 * Interview: "Generic PagedResponse banaya hai jo kisi bhi type ke saath kaam
 * karta hai (Generics <T>). Isse har entity ke liye alag paged response
 * banana nahi padta — DRY principle follow ho raha hai."
 */
@Getter
@AllArgsConstructor
@Builder
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last; // kya ye last page hai?
}
