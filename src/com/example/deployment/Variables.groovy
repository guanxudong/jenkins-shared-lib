package com.example.deployment

class VariablesDSL implements Serializable {
    def variablesMap = [:]
    def deployStepsList = []
    def rollbackStepsList = []
    
    boolean enableAutoRollback = false
    String successFileName = 'build_success.txt'

    private static Map<String, VariablesDSL> buildRepository = [:]

    static void setForBuild(String buildId, VariablesDSL config) {
        buildRepository[buildId] = config
    }

    static VariablesDSL getForBuild(String buildId) {
        return buildRepository[buildId]
    }

    static void clearForBuild(String buildId) {
        buildRepository.remove(buildId)
    }

    def deployPlan(Closure body) {
        def planDSL = new PlanDSL()
        body.delegate = planDSL
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body()
        this.deployStepsList = planDSL.steps
    }

    def rollbackPlan(Closure body) {
        def planDSL = new PlanDSL()
        body.delegate = planDSL
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body()
        this.rollbackStepsList = planDSL.steps
    }

    def methodMissing(String name, args) {
        if (args.length == 1) {
            variablesMap[name] = args[0]
        } else {
            throw new MissingMethodException(name, this.class, args)
        }
    }
}


class PlanDSL implements Serializable {
    def steps = []

    def step(Map args) {
        steps << args
    }

    def parallel(Closure body) {
        def parallelDSL = new ParallelDSL()
        body.delegate = parallelDSL
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body()
        steps << [parallel: parallelDSL.parallelSteps]
    }
}

class ParallelDSL implements Serializable {
    def parallelSteps = []

    def step(Map args) {
        parallelSteps << args
    }
}
