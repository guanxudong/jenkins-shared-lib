// vars/myDeploy.groovy
import com.example.deployment.VariablesDSL


def call() {
    def config = VariablesDSL.getForBuild(currentBuild.externalizableId)

    def vars = config.variablesMap

    def deploySteps = config.deployStepsList
    def rollbackSteps = config.rollbackStepsList


    myPipeline(
        vars: vars,
        deploySteps: deploySteps,
        rollbackSteps: rollbackSteps
    )
}
