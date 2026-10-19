package com.saasresearcher.learning.classrooms.api;

/**
 * Hook for modules that freeze per-class state when a class starts (e.g. required exam assignments).
 * Runs inside the activation transaction; failures roll the activation back.
 */
public interface ClassLifecycleListener {

    void classActivated(ClassInfo classInfo);
}
