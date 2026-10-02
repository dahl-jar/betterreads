package com.betterreads.book;

import org.springframework.data.jpa.repository.JpaRepository;

interface BookSeriesChangeRepository extends JpaRepository<BookSeriesChange, Long> {
}
