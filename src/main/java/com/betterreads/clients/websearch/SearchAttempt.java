package com.betterreads.clients.websearch;

import tools.jackson.databind.JsonNode;

sealed interface SearchAttempt permits SearchAttempt.Answered, SearchAttempt.Failed, SearchAttempt.Halted {

    record Answered(JsonNode answer, SearchUsage usage) implements SearchAttempt {
    }

    record Failed(String reason) implements SearchAttempt {
    }

    record Halted(String reason) implements SearchAttempt {
    }
}
