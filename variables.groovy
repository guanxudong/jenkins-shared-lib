// vars/variables.groovy
import com.globalclass.Variables

def call(Closure body) {
    // 1. Create a fresh instance for this specific build
    def config = new Variables()

    // 2. Delegate execution to the class instance
    // DELEGATE_FIRST ensures that when the user types "agent", 
    // it looks at our class methods/properties first.
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config

    // 3. Run the user's DSL block
    body()

    // 4. Save the populated object to the global binding
    // This bridges the gap between variables{} and appDeploy()
    binding.setVariable('myGlobalVars', config)
}
