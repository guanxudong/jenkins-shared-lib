// vars/variables.groovy
import com.example.deployment.VariablesDSL

def call(Closure body) {
    def config = new VariablesDSL()

    body.delegate = config
    body.resolveStrategy = Closure.DELEGATE_FIRST

    body()

    VariablesDSL.setForBuild(currentBuild.externalizableId, config)
}
