@Library('ops-library') _

variables {
    // 1. SETTER: Defines a custom variable (stored in ansibleVars map)
    my_region "us-west-2" 
    
    // 2. GETTER: References 'my_region' immediately
    // The class 'propertyMissing' getter finds "us-west-2" and returns it
    agent my_region 

    // 3. INTERPOLATION: Works inside strings too
    credentialsId "deploy-key-${my_region}"

    // 4. USAGE: Passing these variables into the flow
    deployment_flow = [
        [
            name: "Stop Service",
            tags: "stop_app",
            // Reference the variable again
            extra_vars: [ region: my_region ] 
        ],
        [
            name: "Deploy",
            tags: "deploy_app",
            extra_vars: [ version: "2.0.0" ]
        ]
    ]
}

appDeploy()
