package com.betterreads.features.authorpage;

import java.util.Optional;

import com.betterreads.book.AuthorRepository;
import com.betterreads.images.CoverImages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthorPageService {

    private final AuthorPageRepository pages;

    private final AuthorRepository authors;

    private final CoverImages coverImages;

    AuthorPageService(final AuthorPageRepository pages, final AuthorRepository authors, final CoverImages coverImages) {
        this.pages = pages;
        this.authors = authors;
        this.coverImages = coverImages;
    }

    @Transactional(readOnly = true)
    Optional<AuthorPageResponse> findAuthor(final long authorId) {
        return pages.findAuthor(authorId).map(author -> new AuthorPageResponse(
            author.authorId(), author.name(), author.photoUrl(), author.bio(),
            pages.findBooks(authorId).stream()
                .map(book -> new AuthorBookResponse(
                    book.dedupKey(), book.title(), coverImages.servedUrl(book.dedupKey(), book.coverUrl()),
                    book.firstPublishYear(), book.seriesName(), book.seriesPosition(), book.role()))
                .toList()));
    }

    Optional<Long> survivorOf(final long mergedAuthorId) {
        return authors.findMergedInto(mergedAuthorId);
    }
}
