package com.betterreads.features.authorpage;

import java.net.URI;

import com.betterreads.errors.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/authors")
@Tag(name = "Catalog", description = "Public book detail and lists")
@SecurityRequirements
class AuthorPageController {

    private static final String AUTHOR_PATH = "/api/v1/authors/";

    private final AuthorPageService service;

    AuthorPageController(final AuthorPageService service) {
        this.service = service;
    }

    @GetMapping("/{authorId}")
    @Operation(summary = "Get an author and their books")
    @ApiResponse(responseCode = "200", description = "The author")
    @ApiResponse(responseCode = "308", description = "The author was merged into another, see Location",
        content = @Content)
    @ApiResponse(responseCode = "404", description = "No author with that id",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthorPageResponse> getAuthor(@PathVariable final long authorId) {
        return service.findAuthor(authorId)
            .map(ResponseEntity::ok)
            .or(() -> service.survivorOf(authorId).map(survivor -> ResponseEntity
                .status(HttpStatus.PERMANENT_REDIRECT)
                .location(URI.create(AUTHOR_PATH + survivor))
                .<AuthorPageResponse>build()))
            .orElseThrow(() -> new ResourceNotFoundException("No author with id " + authorId));
    }
}
