package com.betterreads.clients.websearch;

public sealed interface CheckOutcome permits CheckOutcome.Checked, CheckOutcome.BatchFailed, CheckOutcome.RunHalted {

    record Checked(CheckRun run) implements CheckOutcome {
    }

    record BatchFailed(String reason) implements CheckOutcome {
    }

    record RunHalted(String reason) implements CheckOutcome {
    }
}
