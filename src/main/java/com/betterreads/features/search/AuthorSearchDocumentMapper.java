package com.betterreads.features.search;

import com.betterreads.bookindex.AuthorIndexView;
import com.betterreads.text.AuthorNames;
import org.springframework.stereotype.Component;

@Component
class AuthorSearchDocumentMapper {

    AuthorSearchDocument toDocument(final AuthorIndexView author) {
        return new AuthorSearchDocument(
            author.authorId(), author.name(), AuthorNames.surname(author.name()), author.aliases(),
            author.photoUrl(), author.bookCount(), author.popularityScore(), author.topTitles());
    }
}
