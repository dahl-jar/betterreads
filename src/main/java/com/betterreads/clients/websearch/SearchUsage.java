package com.betterreads.clients.websearch;

public record SearchUsage(int turns, double costUsd, long durationMs, int deniedCalls) {
}
