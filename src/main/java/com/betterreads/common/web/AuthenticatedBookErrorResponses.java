package com.betterreads.common.web;

import org.springframework.http.ProblemDetail;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The 401 and 404 Swagger responses shared by authenticated endpoints that address a book by key.
 * Applied alongside the endpoint's own 200 and any 400 response.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "401", description = "Missing or invalid access token",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "No book with that key",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
public @interface AuthenticatedBookErrorResponses {
}
