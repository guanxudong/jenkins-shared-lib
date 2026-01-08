// vars/appDeploy.groovy
import com.globalclass.Variables

def call() {
    // 1. Retrieve the Configuration Object
    // We check if it exists to provide a helpful error message
    if (!binding.hasVariable('myGlobalVars')) {
        error "❌ Error: You must define a 'variables { ... }' block before calling appDeploy()."
    }
    
    // Cast it to the class so your IDE (and Groovy) knows the types
    Variables config = (Variables) binding.getVariable('myGlobalVars')

    pipeline {
        // 2. Use the "agent" defined in the variables block
        agent { label config.agent } 

        stages {
            stage('Initialize') {
                steps {
                    script {
                        echo "🚀 Starting Deployment for: ${config.ansibleVars.service_name ?: 'Unknown Service'}"
                        echo "🔑 Using Credentials: ${config.credentialsId}"
                    }
                }
            }

            // 3. Dynamic Orchestration (The Loop)
            stage('Execution Plan') {
                steps {
                    script {
                        // If no flow is defined, maybe run a default generic role?
                        if (config.deployment_flow.isEmpty()) {
                            echo "No deployment_flow defined. Running default deployment..."
                            // You could trigger a default Ansible run here
                        }

                        // Iterate through the user's defined steps
                        config.deployment_flow.each { stepDef ->
                            
                            // Create a visualization stage in Jenkins UI
                            stage(stepDef.name) {
                                echo "▶ Executing Step: ${stepDef.name}"

                                // Merge Global Ansible Vars + Step Specific Vars
                                // Step vars override global vars if keys collide
                                def combinedVars = config.ansibleVars + (stepDef.extra_vars ?: [:])

                                // Write vars to JSON to safely pass to Ansible
                                writeJSON file: 'ansible_vars.json', json: combinedVars

                                // Execute Ansible
                                // Note: We use the tags defined in the step
                                sshagent([config.credentialsId]) {
                                    // Example command - adjust to your real Ansible path
                                    sh """
                                        echo "Running Ansible Tag: ${stepDef.tags}"
                                        
                                        # ansible-playbook site.yml \
                                        # -i production.inv \
                                        # -e @ansible_vars.json \
                                        # --tags "${stepDef.tags}"
                                    """
                                }
                            }
                        }
                    }
                }
            }
        }
        
        post {
            always {
                // Cleanup the temp file
                sh "rm -f ansible_vars.json"
            }
        }
    }
}
