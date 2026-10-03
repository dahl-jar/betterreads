package com.betterreads.clients.websearch;

import java.util.List;
import java.util.Optional;

// PMD.ImplicitFunctionalInterface: a Spring service contract with a @Component impl that happens to have one method.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface MetadataCheckClient {

    Optional<CheckRun> check(List<MetadataCheckRequest> books);
}
