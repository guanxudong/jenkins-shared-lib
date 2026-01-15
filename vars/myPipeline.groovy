import com.example.deployment.VariablesDSL

def call(Map config) {
    def defaultAgent = config.vars?.agent ?: 'any'
    def defaultFailFast = config.vars?.failFast ?: false

    def deploySteps = config.deploySteps ?: []
    def rollbackSteps = config.rollbackSteps ?: []

    def runStep = { map ->
        def targetNode = map.agent ?: defaultAgent

        node(targetNode) {
            stage(map.name) {
                cleanWs()
                unstash 'deploy-workspace'

                echo "Running Ansible playbook: ${map.playbook} on ${map.inventory}"
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
                                        runStep(pStep)
                                    }
                                }
                                parallel parallelSteps, failFast: defaultFailFast
                            } else {
                                runStep(step)
                            }
                        }
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
                                        runStep(pStep)
                                    }
                                }
                                parallel parallelSteps, failFast: defaultFailFast
                            } else {
                                runStep(step)
                            }
                        }
                    }
                }
            }
        }
    }
}