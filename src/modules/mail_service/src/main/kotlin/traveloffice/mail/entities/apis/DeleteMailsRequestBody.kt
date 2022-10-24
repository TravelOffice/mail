package traveloffice.mail.entities.apis

import traveloffice.mail.common.Constant.Companion.INVALID_MESSAGE
import traveloffice.mail.common.Constant.Companion.REQUIRED_MESSAGE
import javax.validation.constraints.*

class DeleteMailsRequestBody {
    @Email(message = INVALID_MESSAGE)
    @NotBlank(message = REQUIRED_MESSAGE)
    lateinit var email: String

    @NotBlank(message = REQUIRED_MESSAGE)
    @NotNull(message = REQUIRED_MESSAGE)
    lateinit var mailbox: String

    @NotEmpty(message = REQUIRED_MESSAGE)
    lateinit var messagesId: List<String>
}
