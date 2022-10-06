package traveloffice.mail.entities

import traveloffice.mail.common.Constant.Companion.BLANK_MESSAGE
import traveloffice.mail.common.Constant.Companion.DEFAULT_ENCODING
import javax.validation.constraints.NotBlank

data class SendAttachmentPayload (
    @field:NotBlank(message = BLANK_MESSAGE) val name: String,
    @field:NotBlank(message = BLANK_MESSAGE) val mimeType: String,
    @field:NotBlank(message = BLANK_MESSAGE) val path: String,
    @field:NotBlank(message = BLANK_MESSAGE) var encoding: String = DEFAULT_ENCODING
)
