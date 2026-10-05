package com.betterreads.booksource;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CreditRoleTest {

    @ParameterizedTest
    @CsvSource({
        "Illustrator, ILLUSTRATOR",
        "Cover Artist, ILLUSTRATOR",
        "letterer, ILLUSTRATOR",
        "Editor / Contributor, EDITOR",
        "Translator, TRANSLATOR",
        "Narrator, NARRATOR",
        "writer of introduction, INTRODUCTION",
        "Foreword, INTRODUCTION",
        "Author, AUTHOR",
        "screenwriter, AUTHOR"
    })
    void shouldMapRoleText(final String text, final CreditRole expected) {
        assertThat(CreditRole.fromSourceText(text)).isEqualTo(expected);
    }

    @Test
    void shouldMapNullToAuthor() {
        assertThat(CreditRole.fromSourceText(null)).isEqualTo(CreditRole.AUTHOR);
    }

    @Test
    void shouldMapUnknownToOther() {
        assertThat(CreditRole.fromSourceText("Contributor")).isEqualTo(CreditRole.OTHER);
    }

    @Test
    void shouldListPrimaryRolesInSql() {
        final String expected = Arrays.stream(CreditRole.values())
            .filter(CreditRole::isPrimary)
            .map(role -> "'" + role.name() + "'")
            .collect(Collectors.joining(", ", "(", ")"));

        assertThat(CreditRole.PRIMARY_SQL).isEqualTo(expected);
    }
}
