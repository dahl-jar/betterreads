package com.betterreads.clients.hardcoverauthor;

import com.betterreads.booksource.SourceAuthorWorks;
import java.util.Optional;

/** Resolves an author and their books from Hardcover. */
// PMD.ImplicitFunctionalInterface: a Spring service contract with a @Component impl that happens to have one method.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface HardcoverAuthorClient {

    /**
     * Returns the author's English books ordered by readers, one per canonical work and no boxed sets,
     * or empty if no author matches.
     */
    Optional<SourceAuthorWorks> fetchAuthorWorks(String query);
}
