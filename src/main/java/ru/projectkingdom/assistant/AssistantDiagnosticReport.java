package ru.projectkingdom.assistant;

import java.util.List;

public record AssistantDiagnosticReport(
        List<String> executedIds,
        List<String> failedIds,
        List<AssistantFinding> findings,
        boolean truncated
) {
    public AssistantDiagnosticReport {
        executedIds = List.copyOf(executedIds);
        failedIds = List.copyOf(failedIds);
        findings = List.copyOf(findings);
    }
}
