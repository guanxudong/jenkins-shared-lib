package com.example.deployment

class DeploymentState implements Serializable {
    private boolean anyFailure = false
    private List<String> failedSteps = []
    
    synchronized void markFailed(String stepName) {
        anyFailure = true
        if (!failedSteps.contains(stepName)) {
            failedSteps << stepName
        }
    }
    
    synchronized boolean hasFailure() {
        return anyFailure
    }
    
    synchronized List<String> getFailedSteps() {
        return failedSteps
    }
    
    synchronized String getFirstFailedStep() {
        return failedSteps.isEmpty() ? null : failedSteps[0]
    }
}
