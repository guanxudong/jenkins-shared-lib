import com.example.deployment.VariablesDSL
import com.example.deployment.DeploymentState

def call(Map config) {
    def defaultAgent = config.vars?.agent ?: 'any'
    def defaultFailFast = config.vars?.failFast ?: false

    def deploySteps = config.deploySteps ?: []
    def rollbackSteps = config.rollbackSteps ?: []

    def deploymentState = new DeploymentState()

    def runStep = { map, DeploymentState state ->
        def targetNode = map.agent ?: defaultAgent

        node(targetNode) {
            stage(map.name) {
                cleanWs()
                unstash 'deploy-workspace'

                try {
                    echo "Running Ansible playbook: ${map.playbook}"
                    sh "ansible-playbook ${map.playbook}"

                    if (config.vars?.enableAutoRollback) {
                        def fileName = config.vars?.successFileName ?: 'build_success.txt'

                        if (!fileExists(fileName)) {
                            echo "❌ Deployment failed for ${map.name}: ${fileName} not found"
                            state.markFailed(map.name)
                        } else {
                            echo "✅ Deployment successful for ${map.name}: ${fileName} found"
                        }
                    }
                } catch (Exception e) {
                    if (config.vars?.enableAutoRollback) {
                        echo "❌ Error executing ${map.name}: ${e.message}"
                        state.markFailed(map.name)
                    } else {
                        throw e
                    }
                }
            }
        }
    }

    def executeRollback = { rollbackSteps, DeploymentState state ->
        echo "🔄 Starting full deployment rollback..."

        def failedList = state.getFailedSteps()
        echo "Failed steps: ${failedList.join(', ')}"

        try {
            rollbackSteps.each { step ->
                if (step.containsKey('parallel')) {
                    def parallelSteps = [:]
                    step.parallel.each { pStep ->
                        parallelSteps[pStep.name] = {
                            runRollbackStep(pStep)
                        }
                    }
                    parallel parallelSteps
                } else {
                    runRollbackStep(step)
                }
            }

            echo "✅ Rollback completed successfully"
            currentBuild.result = 'UNSTABLE'

        } catch (Exception e) {
            echo "❌ Rollback failed: ${e.message}"
            currentBuild.result = 'FAILURE'
            throw e
        }
    }

    def runRollbackStep = { step ->
        def targetNode = step.agent ?: defaultAgent

        node(targetNode) {
            stage("Rollback: ${step.name}") {
                cleanWs()
                unstash 'deploy-workspace'

                echo "Running rollback playbook: ${step.playbook}"
                sh "ansible-playbook ${step.playbook}"
            }
        }
    }

    pipeline {
        agent { label defaultAgent }

        stages {
            stage("Pre Checks") {
                steps {
                    checkout scm
                    stash name: 'deploy-workspace', includes: '**/*'
                }
            }

            stage("Deploy Plan") {
                when {
                    expression { !params.ENABLE_ROLLBACK }
                }
                steps {
                    script {
                        deploySteps.each { step ->
                            if (step.containsKey('parallel')) {
                                def parallelSteps = [:]
                                step.parallel.each { pStep ->
                                    parallelSteps[pStep.name] = {
                                        runStep(pStep, deploymentState)
                                    }
                                }
                                parallel parallelSteps, failFast: defaultFailFast
                            } else {
                                runStep(step, deploymentState)
                            }
                        }
                    }
                }
            }

            stage("Auto Rollback") {
                when {
                    allOf {
                        expression { config.vars?.enableAutoRollback }
                        expression { deploymentState.hasFailure() }
                        expression { !params.ENABLE_ROLLBACK }
                    }
                }
                steps {
                    script {
                        executeRollback(rollbackSteps, deploymentState)
                    }
                }
            }

            stage ("Rollback Plan") {
                when {
                    expression { params.ENABLE_ROLLBACK }
                }
                steps {
                    script {
                        rollbackSteps.each { step ->
                            if (step.containsKey('parallel')) {
                                def parallelSteps = [:]
                                step.parallel.each { pStep ->
                                    parallelSteps[pStep.name] = {
                                        runRollbackStep(pStep)
                                    }
                                }
                                parallel parallelSteps, failFast: defaultFailFast
                            } else {
                                runRollbackStep(step)
                            }
                        }
                    }
                }
            }
        }
    }
}