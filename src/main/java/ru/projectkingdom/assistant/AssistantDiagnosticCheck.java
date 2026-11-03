package ru.projectkingdom.assistant;

import java.util.List;

/** One isolated, read-only Kingdom subsystem diagnostic. */
public interface AssistantDiagnosticCheck {
    String id();
    String titleRu();
    List<AssistantFinding> run(AssistantDiagnosticContext context);
}
