# Agents Configuration

## Overview

This Jenkins shared library supports flexible agent configuration for multi-environment deployments. Agents can be configured at a global level or overridden per deployment step. The library also supports automatic rollback that executes on designated agents when deployments fail.

## Agent Hierarchy

### 1. Global Default Agent

The global default agent is set in the `variables {}` block:

```groovy
variables {
    agent 'region-as'
    // ...
}
```

- Default: `'any'` if not specified
- Applied to: All steps without explicit agent configuration
- Location: Set in `vars/myPipeline.groovy:24`

### 2. Step-Level Agent Override

Individual steps can override the global agent:

```groovy
deployPlan {
    step name: 'Deploy Application', playbook: 'deploy.yml'
    parallel {
        step name: 'App-1', playbook: 'app1.yml', agent: 'dc-a'
        step name: 'App-2', playbook: 'app2.yml', agent: 'dc-b'
    }
}
```

- Applied to: Specific step only
- Priority: Higher than global default agent
- Location: Evaluated in `vars/myPipeline.groovy:11`

## Agent Resolution Logic

```
Step agent specified?
    ↓ Yes
Use step-specific agent
    ↓ No
Use global default agent
    ↓ None specified
Use 'any' agent
```

Source: `vars/myPipeline.groovy:11`

```groovy
def targetNode = map.agent ?: defaultAgent
```

## Execution Flow

### Pipeline-Level Agent

```groovy
pipeline {
    agent { label defaultAgent }
    // ...
}
```

The pipeline itself runs on the global default agent for:
- Pre Checks stage (checkout, stash)
- Script orchestration
- Stage coordination

### Step-Level Execution

Each deployment step executes on its resolved agent:

```groovy
node(targetNode) {
    stage(map.name) {
        cleanWs()
        unstash 'deploy-workspace'
        // Deployment execution
    }
}
```

Source: `vars/myPipeline.groovy:13-20`

Key behaviors:
- Workspace is cleaned on each step execution
- Workspace is unstashed from the pipeline-level stash
- Enables execution across different Jenkins agents/nodes

## Parallel Execution with Different Agents

Parallel steps can execute on different agents simultaneously:

```groovy
parallel {
    step name: 'App-1', playbook: 'app1.yml', agent: 'dc-a'
    step name: 'App-2', playbook: 'app2.yml', agent: 'dc-b'
}
```

- Each parallel branch runs on its designated agent
- No agent conflicts due to isolated node blocks
- Fail-fast behavior configurable via `failFast` parameter

Source: `vars/myPipeline.groovy:41-48`

## Configuration Details

### VariablesDSL Storage

Agent configuration is stored in `VariablesDSL`:
- Instance: `com.example.deployment.VariablesDSL`
- Storage: Map keyed by `currentBuild.externalizableId`
- Access: `VariablesDSL.getForBuild(currentBuild.externalizableId)`

Source: `vars/variables.groovy:12`

### FailFast Configuration

```groovy
def defaultFailFast = config.vars?.failFast ?: false
```

Source: `vars/myPipeline.groovy:5`

Controls whether parallel execution stops immediately if one branch fails.

## Usage Examples

### Single Agent Deployment

```groovy
variables {
    agent 'region-us'
}

deployPlan {
    step name: 'Deploy All', playbook: 'deploy.yml'
}

myDeploy()
```

All stages execute on `region-us`.

### Multi-Agent Deployment

```groovy
variables {
    agent 'region-us'
}

deployPlan {
    step name: 'Pre-checks', playbook: 'pre.yml', agent: 'validation'
    parallel {
        step name: 'DC-A Deploy', playbook: 'app1.yml', agent: 'dc-a'
        step name: 'DC-B Deploy', playbook: 'app2.yml', agent: 'dc-b'
    }
}

myDeploy()
```

- Pre-checks: `validation` agent
- DC-A Deploy: `dc-a` agent
- DC-B Deploy: `dc-b` agent

### No Agent Specified

```groovy
variables {
    // No agent specified
}

deployPlan {
    step name: 'Deploy', playbook: 'deploy.yml'
}

myDeploy()
```

- Pipeline and all steps use `'any'` agent (Jenkins default)

## Auto-Rollback and Agent Configuration

### Auto-Rollback Overview

When auto-rollback is enabled, the library automatically executes rollback steps if any deployment fails:

```groovy
variables {
    enableAutoRollback true
    successFileName 'build_success.txt'
    agent 'region-as'

    deployPlan {
        step name: 'App-1', playbook: 'app1.yml', agent: 'dc-a'
        step name: 'App-2', playbook: 'app2.yml', agent: 'dc-b'
    }

    rollbackPlan {
        step name: 'Rollback App-2', playbook: 'rollback_app2.yml', agent: 'dc-b'
        step name: 'Rollback App-1', playbook: 'rollback_app1.yml', agent: 'dc-a'
    }
}
```

### Rollback Agent Resolution

Rollback steps follow the same agent resolution logic as deployment steps:

- **Step-level agent:** If rollback step specifies an agent, that agent is used
- **Global default agent:** If no agent specified, uses global default agent from `variables {}`
- **Same agent as deployment:** Best practice - rollback on same agent that deployed the service

### Rollback Execution Flow

```
Deployment fails on DC-A (agent: dc-a)
    ↓
Auto-rollback triggers
    ↓
Execute Rollback App-2 on dc-b
    ↓
Execute Rollback App-1 on dc-a
```

Each rollback step runs in its own isolated `node()` block with:
- Clean workspace (`cleanWs()`)
- Unstashed workspace from pipeline stash
- Execution on designated agent

### Multi-Agent Rollback Example

```groovy
variables {
    enableAutoRollback true
    agent 'region-as'

    deployPlan {
        parallel {
            step name: 'US-East Deploy', playbook: 'app_us.yml', agent: 'us-east'
            step name: 'EU-West Deploy', playbook: 'app_eu.yml', agent: 'eu-west'
            step name: 'AP-South Deploy', playbook: 'app_ap.yml', agent: 'ap-south'
        }
    }

    rollbackPlan {
        step name: 'Rollback AP-South', playbook: 'rollback_ap.yml', agent: 'ap-south'
        step name: 'Rollback EU-West', playbook: 'rollback_eu.yml', agent: 'eu-west'
        step name: 'Rollback US-East', playbook: 'rollback_us.yml', agent: 'us-east'
    }
}
```

In this example:
- Deployment executes in parallel on 3 different agents
- Rollback executes sequentially (or parallel if configured) on the same 3 agents
- Each rollback step runs in isolation on its designated agent

### Rollback Agent Best Practices

1. **Use Same Agent:** Rollback should execute on the same agent that performed the deployment
   - Ensures access to same networks/credentials
   - Maintains consistency with deployment environment

2. **Explicit Agent Specification:** Always specify agent for rollback steps in multi-agent scenarios
   - Avoids accidental rollback on wrong data center
   - Provides clear documentation of rollback topology

3. **Parallel Rollback Support:** Rollback steps can also use parallel execution
   ```groovy
   rollbackPlan {
       parallel {
           step name: 'Rollback App-1', playbook: 'rollback_app1.yml', agent: 'dc-a'
           step name: 'Rollback App-2', playbook: 'rollback_app2.yml', agent: 'dc-b'
       }
   }
   ```

### Thread-Safe Failure Tracking

The `DeploymentState` class uses synchronized methods to track failures across parallel executions:

```groovy
class DeploymentState implements Serializable {
    synchronized void markFailed(String stepName) {
        // Thread-safe failure tracking
    }

    synchronized boolean hasFailure() {
        return anyFailure
    }
}
```

This ensures accurate failure detection even when:
- Multiple parallel steps fail simultaneously
- Deployments run on different isolated agents
- FailFast is disabled (wait for all parallel steps)

For complete auto-rollback documentation, see [docs/AUTO_ROLLBACK.md](docs/AUTO_ROLLBACK.md).
