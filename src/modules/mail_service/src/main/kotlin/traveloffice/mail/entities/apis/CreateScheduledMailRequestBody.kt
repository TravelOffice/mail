package traveloffice.mail.entities.apis

import traveloffice.mail.common.Constant
import traveloffice.mail.entities.EmailAddress
import traveloffice.mail.entities.SendAttachmentPayload
import javax.validation.Valid
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Positive
import javax.validation.constraints.Size

data class CreateScheduledMailRequestBody(
    val subject: String,

    @field:NotBlank(message = Constant.BLANK_MESSAGE) val htmlContent: String,

    @field:Size(min = 1, max = 1, message = "[from] must have only one email address")
    val from: List<EmailAddress>,

    @field:Size(min = 1, message = "[to] must have at least one email address") val to: List<@Valid@Email EmailAddress>,
    @field:Valid var cc: List<EmailAddress>?,
    var bcc: List<EmailAddress>?,

    @field:Valid
    var attachments: List<SendAttachmentPayload>?,

    @field:Positive
    val scheduledTime: Long
)
