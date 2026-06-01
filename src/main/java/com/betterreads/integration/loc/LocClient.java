package com.betterreads.integration.loc;

import com.betterreads.catalog.service.BookSourceClient;
import com.betterreads.catalog.service.SourceBook;
import java.util.Optional;

/** Library of Congress catalog boundary over the SRU endpoint ({@code lx2.loc.gov:210/lcdb}). */
public interface LocClient extends BookSourceClient {

    /**
     * Returns the book for the given Library of Congress Control Number, or empty if none matches.
     *
     * @param lccn Library of Congress Control Number (e.g. {@code 2003065165})
     */
    Optional<SourceBook> fetchByLccn(String lccn);
}
