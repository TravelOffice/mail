package traveloffice.mail.providers

import aws.sdk.kotlin.services.sfn.SfnClient

class WorkflowProvider(r: String) {
    private var sfnProvider: SfnClient

    init {
        this.sfnProvider = SfnClient { region = r }
    }

    fun startWorkflow(): String? {
        
    }

    fun stopWorkFlow() {

    }
}