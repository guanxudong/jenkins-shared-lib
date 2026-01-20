**CONTEXT RESTORATION: Jenkins & Ansible Unified Deployment Workflow**

**Project Goal:**
Design a standardized "Pipeline as Code" workflow for company developers using **Jenkins Shared Libraries** and **Ansible**.

* **User Interface:** Developers use a simplified, declarative `Jenkinsfile` (GitLab-CI style) without writing Groovy logic.
* **Engine:** A Shared Library (`deploy`) parses the config and generates dynamic stages.

**Key Architecture Decisions Established:**

1. **DSL Syntax:**
* Uses `library 'deploy'`, `variables { ... }`, and `myDeploy()` closure.
* Supports `executionPlan` list for defining Sequential vs. Parallel stages.
* Falls back to a "Default Workflow" (`ansible/deploy.yml`) if no plan is defined.


2. **Multi-Agent / Split-Horizon:**
* Supports global default agent vs. per-stage specific agents (e.g., for isolated Data Centers).
* Uses `stash` / `unstash` to move the workspace (Ansible code) between agents safely.


3. **Feature: Variable Injection:**
* Variables defined in the `variables {}` block are automatically injected into Ansible via `--extra-vars` as JSON.


4. **Feature: Rollback:**
* **Manual Rollback:** Triggered via `ENABLE_ROLLBACK` parameter. Executes all rollback steps defined in `rollbackPlan`.
* **Auto Rollback:** Automatically triggers when deployment steps fail based on success file detection.
* **Success File Detection:** Playbooks must create a success file (default: `build_success.txt`) upon successful deployment. Missing file indicates failure.
* **Build Status Logic:**
  - SUCCESS: All deployments succeed
  - UNSTABLE: Deployment fails but rollback succeeds
  - FAILURE: Deployment fails and rollback fails (or rollback not available)


5. **Feature: Deployment Verification:**
* **Success File Pattern:** Playbooks create a success file upon completion to indicate successful deployment.
* **Workspace Cleanup:** Each step runs `cleanWs()` before execution, ensuring clean workspace for success file detection.
* **Thread-Safe State Tracking:** Uses synchronized `DeploymentState` class to track failures across parallel executions.
* **Full Rollback:** When any deployment step fails, ALL rollback steps execute in the order defined in `rollbackPlan`.



**Current Code Snippets (Reference):**

**1. The Target `Jenkinsfile` (Developer View):**

```groovy
library 'devops-pipeline-library'

variables {
    // Auto-rollback configuration
    enableAutoRollback true
    successFileName 'build_success.txt'  // optional, default: 'build_success.txt'

    // Agent configuration
    agent 'region-as'

    deployPlan {
        step name: 'Deploy Application', playbook: 'deploy.yml'
        parallel {
            step name: 'App-1', playbook: 'app1.yml', agent: 'dc-a'
            step name: 'App-2', playbook: 'app2.yml', agent: 'dc-b'
        }
    }

    rollbackPlan {
        step name: 'Rollback App-2', playbook: 'rollback_app2.yml', agent: 'dc-b'
        step name: 'Rollback App-1', playbook: 'rollback_app1.yml', agent: 'dc-a'
    }
}

myDeploy()
```

**Ansible Playbook Example (app1.yml):**
```yaml
- name: Deploy application
  # ... deployment tasks ...

- name: Health check
  uri:
    url: http://localhost:8080/health
    method: GET
  register: health_check

- name: Fail if health check fails
  fail:
    msg: "Health check failed"
  when: health_check.status != 200

- name: Create success file
  copy:
    dest: build_success.txt
    content: "Deployment successful at {{ ansible_date_time.iso8601 }}"
```

**2. The Shared Library Logic (`vars/myPipeline.groovy`):**

* **Inputs:** Reads config from `VariablesDSL` (deployStepsList, rollbackStepsList, variablesMap).
* **Logic:** Executes deployment steps sequentially or in parallel.
* **Auto-Rollback Flow:**
  1. Executes each deployment step
  2. Checks for success file existence after playbook execution
  3. Tracks failures in thread-safe `DeploymentState`
  4. If ANY step fails, executes ALL rollback steps
  5. Sets build status: UNSTABLE (rollback succeeds) or FAILURE (rollback fails)
* **Execution:** `node(target) -> cleanWs -> unstash -> ansible-playbook -> check success file`.
* **Thread Safety:** `DeploymentState` uses synchronized methods for parallel execution.

**Status:**
Auto-rollback feature is fully implemented and integrated. See `docs/AUTO_ROLLBACK.md` for complete documentation and usage examples.
