package com.betterreads.integration.itunes;

/**
 * One Apple Books search result.
 *
 * @param trackName the result's title
 * @param description the publisher blurb
 */
public record ItunesResult(String trackName, String description) {
}
