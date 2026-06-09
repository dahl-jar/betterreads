package com.betterreads.integration.itunes;

/**
 * One Apple Books search result: the title to verify the match and the description to use.
 *
 * @param trackName the result's title, checked against the looked-up title on the fallback path
 * @param description the publisher blurb
 */
public record ItunesResult(String trackName, String description) {
}
