package com.betterreads.features.account;

import com.betterreads.errors.RateLimitedResponse;

import org.springframework.http.ProblemDetail;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "400", description = "Validation failed",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@RateLimitedResponse
@interface ValidationAndRateLimitResponses {
}
