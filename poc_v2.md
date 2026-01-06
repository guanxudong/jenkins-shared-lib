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
* Pipeline uses `try/catch`. If a deployment step fails, it checks for a defined `rollback` playbook in the plan and executes it automatically.


5. **Feature: Verification & Output:**
* Supports a `verify` stage.
* **Parallel Safety:** To prevent race conditions when reading Ansible output files in parallel stages, we inject a unique `jenkins_step_id` into Ansible.
* Ansible writes output to `ansible_output_{{ jenkins_step_id }}.json`. Jenkins reads this file to validate success/failure.



**Current Code Snippets (Reference):**

**1. The Target `Jenkinsfile` (Developer View):**

```groovy
library 'deploy'
variables {
    agent 'infra-bastion'
    // Custom vars injected to Ansible
    app_version '2.0'
    
    // Complex Plan
    executionPlan = [
        ['name': 'Common', 'file': 'ansible/common.yml'],
        ['parallel': [
            ['name': 'DC1', 'file': 'ansible/app.yml', 'agent': 'dc1-node', 'inventory': 'inv_dc1'],
            ['name': 'DC2', 'file': 'ansible/app.yml', 'agent': 'dc2-node', 'inventory': 'inv_dc2']
        ]],
        // Rollback definition
        ['name': 'Finalize', 'file': 'ansible/fin.yml', 'rollback': 'ansible/undo_fin.yml']
    ]
}
myDeploy()

```

**2. The Shared Library Logic (`vars/myDeploy.groovy` - Conceptual):**

* **Inputs:** Reads `pipelineConfig` global variable.
* **Logic:** Iterates `executionPlan`.
* **Parallel:** Uses `parallel branches` map.
* **Execution:** `node(target) -> cleanWs -> unstash -> ansiblePlaybook`.
* **Safety:** Generates `safeStepName` for unique JSON output files.

**Status:**
We have completed the architectural design and generated a PoC Markdown file. The next steps would likely involve specific implementation details of the library code or setting up the Jenkins environment.
