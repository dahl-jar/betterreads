package com.betterreads.integration.hardcover;

import com.betterreads.catalog.service.SourceAuthorWorks;
import java.util.Optional;

/** Resolves an author and their books from Hardcover. */
// PMD.ImplicitFunctionalInterface: a Spring service contract with a @Component impl that happens to have one method.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface HardcoverAuthorClient {

    /**
     * Returns the author matching the query and their books, or empty if none matches.
     *
     * <p>Resolution is two calls: an Author search picks the candidate with the most books, then an
     * enumeration lists their contributions ordered by readers. The books come back English editions
     * only, one canonical work each, with boxed sets removed.
     */
    Optional<SourceAuthorWorks> fetchAuthorWorks(String query);
}
