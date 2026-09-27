package com.betterreads.clients.hardcoverbook;

import com.betterreads.booksource.BookSourceClient;
import com.betterreads.booksource.SourceBook;
import java.util.Optional;

public interface HardcoverClient extends BookSourceClient {

    Optional<SourceBook> fetchByHardcoverId(String hardcoverId);
}
