# PoC: Unified Continuous Deployment Workflow
### Standardization using Jenkins Shared Libraries & Ansible

## 1. Executive Summary
This document outlines a new Continuous Deployment (CD) workflow designed to standardize deployment logic across the company. By leveraging a **Jenkins Shared Library**, we abstract complex Pipeline logic away from developers. 

**Goals:**
* **Simplification:** Developers write a declarative, configuration-based `Jenkinsfile` (similar to GitLab CI) instead of complex Groovy scripts.
* **Flexibility:** Support for **Sequential** and **Parallel** execution flows (e.g., deploying to multiple Data Centers simultaneously).
* **Multi-Agent Support:** Capable of executing specific stages on specific agents (critical for isolated network segments).
* **Standardization:** Enforced "Pre-Flight" (syntax checks) and "Post-Deployment" (cleanup/notifications) stages.

---

## 2. The Developer Experience (User Interface)

Developers only interface with a clean, readable `Jenkinsfile` in their repository. They define *what* to do, not *how* to do it.

### Scenario A: Complex Multi-DC Deployment
This configuration runs common setup sequentially, then deploys to DC1 and DC2 in parallel (using different agents), and finally updates DNS sequentially.

**`Jenkinsfile`**
```groovy
// Load the library (versioning can be added, e.g., 'deploy@v1.0')
library 'deploy'

variables {
    // Global defaults
    agent 'infra-bastion-main'
    credentialsId 'ansible-ssh-key'
    
    // The Execution Plan
    executionPlan = [
        // 1. Sequential Step: Common Configuration
        ['name': 'Common Config', 'file': 'ansible/common.yml'],

        // 2. Parallel Step: Deploy to separate Data Centers simultaneously
        ['parallel': [
            // Uses agent 'agent-dc1' inside the DC1 network
            ['name': 'Deploy DC1', 'file': 'ansible/app.yml', 'agent': 'agent-dc1', 'inventory': 'ansible/inv_dc1.ini'],
            
            // Uses agent 'agent-dc2' inside the DC2 network
            ['name': 'Deploy DC2', 'file': 'ansible/app.yml', 'agent': 'agent-dc2', 'inventory': 'ansible/inv_dc2.ini']
        ]],

        // 3. Sequential Step: Finalize
        ['name': 'Global DNS Update', 'file': 'ansible/dns.yml']
    ]
}

// Invoke the pipeline engine
myDeploy()
```

---

## 3. The Architecture (DevOps Implementation)

The logic resides entirely in the Shared Library. The application repository structure remains standard.

### 3.1 Repository Structure
```text
project-repo/
├── Jenkinsfile           # The configuration shown above
├── ansible/
│   ├── inventory.ini     # Default inventory
│   ├── common.yml
│   ├── app.yml
│   └── dns.yml
└── README.md
```

### 3.2 Shared Library Code
The library consists of two main files in the `vars/` directory.

#### `vars/variables.groovy`
*Captures the user configuration map.*
```groovy
def call(Closure body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    // Save config to global binding for the main script to access
    getBinding().setVariable('pipelineConfig', config)
}
```

#### `vars/myDeploy.groovy`
*The logic engine. Handles node allocation, stashing, and parallel/sequential logic.*
```groovy
def call() {
    // 1. Load Configuration
    def config = getBinding().hasVariable('pipelineConfig') ? getBinding().getVariable('pipelineConfig') : [:]

    // 2. Set Defaults
    String defaultAgent = config.agent ?: 'linux'
    String credsId = config.credentialsId ?: 'default-key'
    List executionPlan = config.executionPlan ?: []

    // 3. Helper Closure: logic to run a single Ansible step
    def runStep = { map ->
        // Determine agent: specific stage agent OR global default
        String targetNode = map.agent ?: defaultAgent
        
        node(targetNode) {
            stage(map.name) {
                echo "--- [${map.name}] Executing on ${targetNode} ---"
                try {
                    cleanWs()
                    // Retrieve code (Ansible playbooks) from the stash created in Pre-Flight
                    unstash 'deploy-workspace'
                    
                    // Run Ansible
                    ansiblePlaybook(
                        playbook: map.file,
                        inventory: map.inventory ?: 'ansible/inventory.ini', 
                        credentialsId: credsId,
                        colorized: true,
                        extras: "-e 'env=${env.BRANCH_NAME}'"
                    )
                } finally {
                    cleanWs() // Cleanup to save disk space
                }
            }
        }
    }

    // 4. The Pipeline Definition
    pipeline {
        agent none // Disable global allocation; we allocate per stage
        
        options { 
            ansiColor('xterm')
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
        }

        stages {
            // --- STANDARD PRE-STAGE ---
            stage('Pre-Flight & Checkout') {
                agent { label defaultAgent }
                steps {
                    script {
                        echo "--- [Standardization] System Checks ---"
                        checkout scm
                        
                        // Stash the workspace so it can be moved to other agents
                        stash name: 'deploy-workspace', includes: '**/*'
                        
                        // Optional: Syntax Check all playbooks before starting
                        echo "--- [Standardization] Syntax Validation ---"
                        sh "find ansible -name '*.yml' | xargs -I {} ansible-playbook --syntax-check {}"
                    }
                }
            }

            // --- DYNAMIC STAGES ---
            stage('Execution Plan') {
                steps {
                    script {
                        executionPlan.each { step ->
                            if (step.containsKey('parallel')) {
                                // --- Parallel Logic ---
                                Map branches = [:]
                                branches.failFast = true // If one DC fails, stop the others
                                
                                step.parallel.each { pItem ->
                                    branches[pItem.name] = { runStep(pItem) }
                                }
                                
                                parallel branches
                            } else {
                                // --- Sequential Logic ---
                                runStep(step)
                            }
                        }
                    }
                }
            }

            // --- STANDARD POST-STAGE ---
            stage('Post-Deployment') {
                agent { label defaultAgent }
                steps {
                    script {
                        echo "--- [Standardization] Notification & Metrics ---"
                        // slackSend ...
                    }
                }
            }
        }
    }
}
```

---

## 4. Key Benefits

| Feature | Benefit |
| :--- | :--- |
| **Workspace Mobility** | Uses `stash`/`unstash`. Code checked out once (Pre-Flight) is guaranteed to be identical across all agents (DC1, DC2), preventing race conditions during git commits. |
| **Split-Horizon Support** | `node(targetNode)` logic allows deploying to isolated networks where a single global agent cannot reach all targets. |
| **Pipeline-as-Code** | Logic is version-controlled in the Shared Library. To update the deployment logic for *all* teams, we only edit the Shared Library, not 100 individual Jenkinsfiles. |
| **Parallel Execution** | Significantly reduces deployment time by running independent tasks (like multi-region deploys) concurrently. |

## 5. Implementation Roadmap

1.  **Create Repository:** Create `git-server/jenkins-shared-lib`.
2.  **Push Code:** Commit the `vars/` scripts defined above.
3.  **Configure Jenkins:**
    * Go to *Manage Jenkins > System*.
    * Under *Global Pipeline Libraries*, add a library named `deploy`.
    * Point it to the new Git repository.
    * Select "Load implicitly" (optional) or require `library 'deploy'` at the top of Jenkinsfiles.
4.  **Pilot:** Create a dummy project with the `Jenkinsfile` defined in Section 2 and verify the visualization in Blue Ocean.
