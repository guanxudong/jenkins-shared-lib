library 'devops-pipeline-library'

variables {
    agent 'region-as'

    deployPlan {
        step name: 'Deploy Application', playbook: 'deploy.yml'
        parallel {
            step name: 'App-1', playbook: 'app1.yml', agent: 'dc-a'
            step name: 'App-2', playbook: 'app2.yml', agent: 'dc-b'
        }
    }

    rollbackPlan {
        step name: 'Rollback Application', playbook: 'rollback.yml'
    }
}

myDeploy()
