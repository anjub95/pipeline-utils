import groovy.transform.Field

/**
 * environmentSelector.groovy- This groovy file reads the values from Jenkinsfile and prompt users to select the required environment for deployment.
 * call method returns user selected environments.
 * @version 1.0
 */

@Field
static argDesc = [
        name: 'environmentSelector',
        description: 'Prompt user to select an environment for deployment.',
        args: [
                envSelectApprover: [
                        description: 'The LDAP group required to enter the environment for deployment.',
                ],
                approvalTimeout:[
                        description: 'Number of minutes before an envSelectApprover request times out.  Optional.',
                        default: 720
                ]
        ],
]

def call(body) {
    if (env.NODE_NAME) {
        error 'environmentSelector global function should not be inside node block, Please correct it.'
    }
    library 'pipeline-common'
    def config = demoCommon.parseArgs(argDesc, body)
    def selectedEnvs = askWhichEnvironments(config)
    if(selectedEnvs.length()<=0){
        currentBuild.result = 'ABORTED'
        echo 'No Environment entered for deployment.'
        return
    }
    return selectedEnvs.tokenize(',')
}


/**
 * It prompts user to select the environment for deployment.
 * approvalTimeout is default as 720 minutes,user can override the default value by passing approvalTimeout parameter in method call.
 * @param config is the map that contains all the parameter passed in method call as key value pairs.
 */
def askWhichEnvironments(config){
    def EnvSelector = ''
    def timeoutMinutes = config.approvalTimeout
        timeout(time: timeoutMinutes, unit: 'MINUTES') {
            EnvSelector = input(message: 'Which environments should code deploy to?', ok: 'OK', submitter: config.envSelectApprover, parameters: [[$class: 'TextParameterDefinition', description: 'Input a comma separated list of environments.', name: 'Deploy Environments']])
        }
    EnvSelector
}
