# Agents Configuration

## Overview

This Jenkins shared library supports flexible agent configuration for multi-environment deployments. Agents can be configured at a global level or overridden per deployment step.

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
