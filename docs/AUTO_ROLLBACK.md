# Auto-Rollback Feature

## Overview

The Jenkins Shared Library supports automatic rollback when deployments fail. This feature provides automatic recovery from deployment failures by executing rollback playbooks when deployment steps fail.

## How It Works

1. Each deployment step executes its Ansible playbook
2. After execution, the pipeline checks for a success file (default: `build_success.txt`)
3. If the success file exists → deployment successful
4. If the success file is missing → deployment failed
5. If ANY step fails → ALL rollback steps execute
6. Rollback order follows the `rollbackPlan` definition

## Configuration

Enable auto-rollback in your Jenkinsfile:

```groovy
library 'devops-pipeline-library'

variables {
    // Auto-rollback configuration
    enableAutoRollback true
    successFileName 'build_success.txt'  // optional, default: 'build_success.txt'
    
    // Global agent configuration
    agent 'region-as'
    
    // Deployment plan
    deployPlan {
        step name: 'Deploy Web', playbook: 'web.yml'
        step name: 'Deploy API', playbook: 'api.yml'
        parallel {
            step name: 'App-1', playbook: 'app1.yml', agent: 'dc-a'
            step name: 'App-2', playbook: 'app2.yml', agent: 'dc-b'
        }
    }
    
    // Rollback plan (executed when any deployment fails)
    rollbackPlan {
        step name: 'Rollback App-2', playbook: 'rollback_app2.yml', agent: 'dc-b'
        step name: 'Rollback App-1', playbook: 'rollback_app1.yml', agent: 'dc-a'
        step name: 'Rollback API', playbook: 'rollback_api.yml'
        step name: 'Rollback Web', playbook: 'rollback_web.yml'
    }
}

myDeploy()
```

## Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enableAutoRollback` | boolean | `false` | Enable/disable auto-rollback feature |
| `successFileName` | string | `'build_success.txt'` | Name of the success file to check for |

## Success File Convention

Ansible playbooks must create a success file upon successful deployment:

```yaml
# Example: web.yml
- name: Deploy web application
  # ... deployment tasks ...

- name: Health check
  uri:
    url: http://localhost:8080/health
    method: GET
  register: health_check

- name: Fail if health check fails
  fail:
    msg: "Health check failed: {{ health_check.status }}"
  when: health_check.status != 200

- name: Create success file
  copy:
    dest: build_success.txt
    content: "Deployment successful at {{ ansible_date_time.iso8601 }}"
    mode: '0644'
```

### Success File Details

- **Location:** Created in the playbook's current directory (workspace)
- **Persistence:** The file is NOT deleted after detection (kept for debugging)
- **Content:** Can contain any text (file existence is what matters)
- **Permissions:** Recommended `0644` or `0660`

## Build Statuses

| Scenario | Build Status | Meaning |
|----------|--------------|---------|
| All deployments succeed | ✅ SUCCESS | Normal deployment |
| Deployment fails + Rollback succeeds | ⚠️ UNSTABLE | System recovered via rollback, needs investigation |
| Deployment fails + Rollback fails | ❌ FAILURE | System broken, immediate attention needed |

## Important Notes

### Success File Persistence
Success files are **NOT deleted** after detection. This allows for debugging and troubleshooting. The pipeline uses `cleanWs()` before each step, which prevents false positives from previous runs.

### Workspace Cleanup
Each deployment step runs `cleanWs()` before execution, ensuring a fresh workspace. This prevents old success files from interfering with new deployments.

### Parallel Execution
When `failFast: false` (default):
- All parallel steps run to completion
- Failures are tracked across all steps
- Rollback triggers after ALL steps complete
- All rollback steps execute (full rollback)

When `failFast: true`:
- Parallel execution stops on first failure
- Remaining steps are skipped
- Rollback triggers immediately

### Manual Rollback
The manual rollback feature (`ENABLE_ROLLBACK` parameter) still works alongside auto-rollback:
- Manual rollback runs in the "Rollback Plan" stage
- Auto-rollback runs in the "Auto Rollback" stage
- Only one executes per pipeline run (determined by `ENABLE_ROLLBACK` parameter)

### Thread Safety
The deployment state tracker uses synchronized methods to handle concurrent updates from parallel steps, ensuring accurate failure tracking.

## Examples

### Example 1: Simple Sequential Deployment

```groovy
variables {
    enableAutoRollback true
    agent 'region-as'
    
    deployPlan {
        step name: 'Deploy Web', playbook: 'web.yml'
        step name: 'Deploy API', playbook: 'api.yml'
        step name: 'Deploy Database', playbook: 'database.yml'
    }
    
    rollbackPlan {
        step name: 'Rollback Database', playbook: 'rollback_database.yml'
        step name: 'Rollback API', playbook: 'rollback_api.yml'
        step name: 'Rollback Web', playbook: 'rollback_web.yml'
    }
}
```

**Rollback Order:** Database → API → Web (reverse of deployment)

### Example 2: Parallel Deployment with Multiple Agents

```groovy
variables {
    enableAutoRollback true
    agent 'region-as'
    
    deployPlan {
        step name: 'Pre-checks', playbook: 'pre.yml'
        parallel {
            step name: 'DC-A', playbook: 'app1.yml', agent: 'dc-a'
            step name: 'DC-B', playbook: 'app2.yml', agent: 'dc-b'
            step name: 'DC-C', playbook: 'app3.yml', agent: 'dc-c'
        }
    }
    
    rollbackPlan {
        step name: 'Rollback DC-C', playbook: 'rollback_app3.yml', agent: 'dc-c'
        step name: 'Rollback DC-B', playbook: 'rollback_app2.yml', agent: 'dc-b'
        step name: 'Rollback DC-A', playbook: 'rollback_app1.yml', agent: 'dc-a'
    }
}
```

**Rollback Order:** DC-C → DC-B → DC-A (all agents in specified order)

### Example 3: Custom Success File Name

```groovy
variables {
    enableAutoRollback true
    successFileName 'deployment_complete.txt'  // Custom filename
    
    deployPlan {
        step name: 'Deploy', playbook: 'deploy.yml'
    }
    
    rollbackPlan {
        step name: 'Rollback', playbook: 'rollback.yml'
    }
}
```

**Playbook:**
```yaml
- name: Deploy application
  # ... deployment tasks ...

- name: Create success file
  copy:
    dest: deployment_complete.txt  # Must match configuration
    content: "Success"
```

## Troubleshooting

### Deployment Always Fails
If deployments always fail even when playbooks complete successfully:
- Check that playbooks create the success file in the current directory
- Verify the success file name matches `successFileName` configuration
- Check playbook output for errors before success file creation

### Rollback Not Triggering
If rollback doesn't trigger when it should:
- Verify `enableAutoRollback` is set to `true`
- Check that `params.ENABLE_ROLLBACK` is not set (manual rollback takes precedence)
- Review pipeline logs for error messages
- Ensure `deploymentState.hasFailure()` returns true

### False Positive Failures
If deployments fail incorrectly:
- Verify `cleanWs()` is called before each step (should be automatic)
- Check for old success files from previous runs
- Ensure workspace is properly isolated between steps

### Rollback Fails
If rollback itself fails:
- Check rollback playbook for errors
- Verify inventory and connection settings
- Review rollback playbook output
- Build will be marked as FAILURE (not UNSTABLE)

## Testing Checklist

Before deploying to production, verify:

- [ ] Deployment succeeds when all playbooks create success file
- [ ] Auto-rollback triggers when playbook fails to create success file
- [ ] Auto-rollback triggers when playbook throws exception
- [ ] Build marked as UNSTABLE when rollback succeeds
- [ ] Build marked as FAILURE when rollback fails
- [ ] Manual rollback (`ENABLE_ROLLBACK=true`) still works
- [ ] Parallel deployments handle failures correctly
- [ ] Thread-safe behavior with multiple parallel failures
- [ ] Success files persist after detection (for debugging)
- [ ] `cleanWs()` prevents false positives from old success files
- [ ] Rollback order matches `rollbackPlan` definition

## Related Documentation

- [AGENTS.md](AGENTS.md) - Agent configuration for multi-environment deployments
- [PROMPT.md](PROMPT.md) - Overall pipeline architecture and design
