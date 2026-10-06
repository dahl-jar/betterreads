package com.betterreads.book;

import org.springframework.data.jpa.repository.JpaRepository;

interface BookMetadataChangeRepository extends JpaRepository<BookMetadataChange, Long> {
}
