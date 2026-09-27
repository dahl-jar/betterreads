package com.betterreads.searchmiss;

@FunctionalInterface
public interface SearchMissSink {

    void stage(String query);
}
