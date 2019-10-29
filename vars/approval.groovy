import groovy.transform.Field

@Field
static argDesc = [
        name: 'approval',
        description: 'Request for approval before deploying code to an environment.',
        args: [
                environmentName: [
                        description: 'Name of the Environment required for deployment.',
                ],
                ldapApprovalGroup:[
                        description: 'The LDAP group required for approval.  Optional.',
                        default: null,
                ],
                message:[
                        description: 'Message to be displayed for the approval input box.  Optional.',
                        default: 'Press Deploy to proceed.',
                ],
                approverEmailAddress:[
                        description: 'Approver Email Address to send the emails.  Optional.',
                        default: null,
                ],
                AbortReasonEmailAddress:[
                        description: 'Email Address to send the Email when job get aborted.  Optional.',
                        default: null,
                ],
                approvalTimeout:[
                        description: 'Number of Minutes before an approval requests times out.  Optional.',
                        default: 720,
                ],
                changeDetailsUrl:[
                        description: 'URL needs to be added in the Email body.   Optional.',
                        default: null,
                ],


        ],
]

def call(body) {
   if (env.NODE_NAME) {
        error 'Approval global function should not be inside node block,  Please correct it.'
    }
    library 'pipeline-common'
    def config = demoCommon.parseArgs(argDesc, body)
    timestamps {
        getApproval(config)
    }
}

def getApproval(def config) {
    def ldapApprovalGroup = config.ldapApprovalGroup
    def message = config.message
    def timeoutMinutes = config.approvalTimeout
    try {
         if (ldapApprovalGroup) {
             echo "allowed approvers ${ldapApprovalGroup}"
             def sendTo = config.approverEmailAddress
             sendEmailToApprovers(config,sendTo)
             timeout(time: timeoutMinutes, unit: 'MINUTES') {
                 input(message: message, ok: 'Deploy', submitter: ldapApprovalGroup)
             }
        } else {
             echo "allowed approvers everyone"
             def sendTo = config.approverEmailAddress
             sendEmailToApprovers(config,sendTo)
             timeout(time: timeoutMinutes, unit: 'MINUTES') {
                 input(message: message, ok: 'Deploy')
             }
        }
    }catch(org.jenkinsci.plugins.workflow.steps.FlowInterruptedException err){
        //If it timeouts or click abort it comes to catch block
        String abortedBy = err.getCauses()[0].getUser()
        def sendTo = config.approverEmailAddress
        if (abortedBy != null && 'SYSTEM'.equalsIgnoreCase(abortedBy)) {
            echo "build timed out waiting for approval:${abortedBy}"
            sendTimeoutEmail(config,sendTo)
        } else {
            echo "build aborted by :${abortedBy}"
            promptForReasonToAbort(config, abortedBy,sendTo)
        }
        throw err
    }
}

def promptForReasonToAbort(def config,def abortedBy,def sendTo) {
    def userInput
    stage('Abort Reason') {
        timeout(time: 2, unit: 'MINUTES') {
            userInput = input(message: 'Please enter reason why pipeline was aborted.', ok: 'Submit Reason', submitterParameter: 'personName', parameters: [
                    [$class: 'TextParameterDefinition', description: 'Enter reason aborted then click Submit Reason button.', name: 'Reason']
            ])
        }
        sendTo = config.AbortReasonEmailAddress
        sendAbortReasonEmail(userInput,abortedBy,sendTo)
    }
}
def sendEmailToApprovers(def config,def sendTo){
    def timeoutMinutes = config.approvalTimeout
    def emailSubject = "Jenkins Pipeline Approval Needed - Job Name '${config.environmentName}' - Build Number ${env.BUILD_NUMBER}";
    def emailBody = """
                     Approval request will timeout after ${timeoutMinutes} minutes.<br><br>
                     To approve or deny:<br>
                     1) Open this URL <a href='${env.JOB_URL}'>${env.JOB_URL}</a><br>
                     2) Log into Jenkins.<br>
                     3) Hover over "Approval"<br>
                     4) To approve click "Deploy". To deny click "Abort".<br>
                     5) If we click on "Deploy" ,we can see "Deploy to ${config.environmentName}" In Progress.<br>
                     6) If denied, after clicking "Abort", hover over "Abort Reason", enter reason denied and click "Submit Reason".</b>
     """
    if(config.changeDetailsUrl){
        emailBody += "<br><br>Open this URL to learn what changes are in the build <a href='#'>${config.changeDetailsUrl}</a>"
    }
    sendEmail(sendTo, emailSubject, emailBody)
}

def sendAbortReasonEmail(def userInput ,def abortedBy,def sendTo) {
    def emailSubject = "Pipeline Aborted - Job Name '${env.JOB_NAME}' - Build Number ${env.BUILD_NUMBER} aborted by ${abortedBy}";
    def emailBody = """
                    Pipeline aborted by ${abortedBy}<br>
                    Reason:"${userInput.Reason}"<br>
                    Build Number:</b> [${env.BUILD_NUMBER}]<br>
                    <a href=Job URL: '${env.JOB_URL}'>${env.JOB_URL}</a><br>
                    <a href= Build URL: '${env.BUILD_URL}'>${env.BUILD_URL}</a>
    """

    sendEmail(sendTo, emailSubject, emailBody);
}

def sendTimeoutEmail(def config,def sendTo) {
    def timeoutMinutes = config.approvalTimeout
    def emailSubject = "Pipeline Timeout Exceeded - Job Name '${env.JOB_NAME}' - Build Number ${env.BUILD_NUMBER}";
    def emailBody = """
                     Pipeline aborted by system, because ${timeoutMinutes} minutes timeout exceeded<br>
                     Job: ${env.JOB_NAME}<br>
                     Build Number: [${env.BUILD_NUMBER}]<br>
                     <a href='${env.BUILD_URL}/changes'>Changes</a><br>
                     <a href='${env.BUILD_URL}/console'>Job Console</a>
  """
    sendEmail(sendTo, emailSubject, emailBody);
}

def sendEmail(emailTo, emailSubject, emailBody) {
    emailext(subject: emailSubject, body: emailBody, to: emailTo, mimeType: 'text/html')
}
