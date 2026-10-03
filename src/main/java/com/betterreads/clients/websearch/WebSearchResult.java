package com.betterreads.clients.websearch;

import tools.jackson.databind.JsonNode;

record WebSearchResult(JsonNode answer, SearchUsage usage) {
}
