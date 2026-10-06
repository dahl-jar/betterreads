package com.betterreads.clients.websearch;

import tools.jackson.databind.JsonNode;

record FieldVerdict(CheckedField field, VerdictStatus status, JsonNode value, String quote, String source) {
}
