package traveloffice.mail.entities

import traveloffice.mail.common.Constant.Companion.BLANK_MESSAGE
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

data class SendMailRequestPayload(
    val subject: String,

    @field:NotBlank(message = BLANK_MESSAGE)
    val htmlContent: String,

    @field:Size(min = 1, max = 1, message = "[from] must have only one email address")
    @field:Valid
    val from: List<EmailAddress>,

    @field:Size(min = 1, message = "[to] must have at least one email address")
    @field:Valid
    val to: List<EmailAddress>,

    @field:Valid
    var cc: List<EmailAddress>?,

    @field:Valid
    var bcc: List<EmailAddress>?,

    @field:Valid
    var attachments: List<SendAttachmentPayload>?
)

