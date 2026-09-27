package com.betterreads.features.shelves;

import jakarta.validation.constraints.NotNull;

record SetFavoriteRequest(@NotNull Boolean favorite) {
}
