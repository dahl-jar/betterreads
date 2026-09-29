package com.betterreads.clients.websearch;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.betterreads.book.VerifiedMetadata;

// PMD.ImplicitFunctionalInterface: a Spring service contract with a @Component impl that happens to have one method.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface MetadataCheckClient {

    Optional<Map<Long, VerifiedMetadata>> check(List<MetadataCheckRequest> books);
}
