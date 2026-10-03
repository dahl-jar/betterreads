package com.betterreads.features.reviews;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record SetRatingRequest(@NotNull @Min(1) @Max(5) Integer rating) {
}
