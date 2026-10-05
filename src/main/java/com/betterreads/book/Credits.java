package com.betterreads.book;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.betterreads.booksource.SourceAuthor;
import com.betterreads.text.AuthorNames;

final class Credits {

    private Credits() {
    }

    static List<Author> primary(final List<BookAuthor> ordered) {
        return ordered.stream()
            .filter(credit -> credit.getRole().isPrimary())
            .map(BookAuthor::getAuthor)
            .toList();
    }

    static boolean replace(final Book book, final Set<BookAuthor> credits, final List<ResolvedCredit> resolved) {
        final List<ResolvedCredit> distinct = IntStream.range(0, resolved.size())
            .filter(index -> IntStream.range(0, index)
                .noneMatch(earlier -> sameAuthor(resolved.get(earlier).author(), resolved.get(index).author())))
            .mapToObj(resolved::get)
            .toList();
        final boolean removed = credits.removeIf(credit -> distinct.stream()
            .noneMatch(next -> sameAuthor(next.author(), credit.getAuthor())));
        final boolean placed = IntStream.range(0, distinct.size())
            .mapToObj(position -> place(book, credits, distinct.get(position), position))
            .reduce(false, Boolean::logicalOr);
        return removed || placed;
    }

    static List<ResolvedCredit> reordered(final List<BookAuthor> stored, final List<SourceAuthor> credits) {
        final List<ResolvedCredit> matched = credits.stream()
            .flatMap(credit -> stored.stream()
                .filter(existing -> existing.getAuthor().getNameKey().equals(AuthorNames.key(credit.name())))
                .findFirst()
                .map(existing -> new ResolvedCredit(existing.getAuthor(), credit.role()))
                .stream())
            .toList();
        return Stream.concat(
                matched.stream(),
                stored.stream()
                    .filter(existing -> matched.stream().noneMatch(next -> next.author().equals(existing.getAuthor())))
                    .map(existing -> new ResolvedCredit(existing.getAuthor(), existing.getRole())))
            .toList();
    }

    private static boolean place(
        final Book book, final Set<BookAuthor> credits, final ResolvedCredit next, final int position) {
        final Optional<BookAuthor> existing = credits.stream()
            .filter(credit -> sameAuthor(credit.getAuthor(), next.author()))
            .findFirst();
        if (existing.isPresent()) {
            return existing.get().place(next.role(), position);
        }
        credits.add(new BookAuthor(book, next.author(), next.role(), position));
        return true;
    }

    private static boolean sameAuthor(final Author first, final Author second) {
        if (first.equals(second)) {
            return true;
        }
        final Long authorId = first.getAuthorId();
        return authorId != null && authorId.equals(second.getAuthorId());
    }
}
