package traveloffice.mail.entities

import traveloffice.mail.common.Constant.Companion.INVALID_MESSAGE
import traveloffice.mail.common.Constant.Companion.REQUIRED_MESSAGE
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank

data class GetMailRequestParams(
    @Email(message = INVALID_MESSAGE)
    @NotBlank(message = REQUIRED_MESSAGE)
    val email: String,

    @NotBlank(message = REQUIRED_MESSAGE)
    val mailbox: String,

    @NotBlank(message = REQUIRED_MESSAGE)
    var mailId: String
)
