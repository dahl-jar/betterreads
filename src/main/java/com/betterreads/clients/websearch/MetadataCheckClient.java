package com.betterreads.clients.websearch;

import java.util.List;

// PMD.ImplicitFunctionalInterface: a Spring service contract with a @Component impl that happens to have one method.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface MetadataCheckClient {

    CheckOutcome check(List<MetadataCheckRequest> books, SourceGroup group);
}
