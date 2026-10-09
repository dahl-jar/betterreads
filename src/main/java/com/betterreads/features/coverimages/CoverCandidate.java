package com.betterreads.features.coverimages;

import com.betterreads.booksource.CoverSource;
import org.jspecify.annotations.Nullable;

record CoverCandidate(CoverSource source, String url, @Nullable String storeUrl) {
}
