// ... inside runStep closure ...

try {
    // ... run ansible playbook ...

    // CHECK: Does the file exist AND did the user enable the feature?
    if (fileExists(ROLLBACK_SIGNAL_FILE)) {
        
        // Retrieve the value passed in variables { ... }
        def autoRollbackEnabled = config.variablesMap.enableAutoRollback ?: false
        
        if (autoRollbackEnabled) {
            echo "⚠️ Auto-rollback signal detected & Feature is ENABLED."
            
            buildConfig.isAutoRollbackTriggered = true
            currentBuild.result = 'UNSTABLE'
        } else {
            echo "⚠️ Signal detected, but 'enableAutoRollback' is false or missing."
            // We treat this as a standard failure since we aren't allowed to rollback
            error "Deployment failed (Signal received) and Auto-Rollback is disabled."
        }
    }
} catch (Exception e) {
    // ... existing catch logic ...
}
