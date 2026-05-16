package com.betterreads.integration.hardcover;

import com.betterreads.catalog.service.BookSourceClient;
import com.betterreads.catalog.service.SourceBook;
import java.util.Optional;

/** Hardcover.app GraphQL API boundary. */
public interface HardcoverClient extends BookSourceClient {

    /**
     * Returns the book for the given Hardcover book id, or empty if none matches.
     *
     * @param hardcoverId Hardcover internal book id
     */
    Optional<SourceBook> fetchByHardcoverId(String hardcoverId);
}
