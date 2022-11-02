package traveloffice.mail.entities.apis

import traveloffice.mail.common.Constant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

class AutoLinkEmailRequest {
    @NotBlank(message = Constant.REQUIRED_MESSAGE)
    @NotNull(message = Constant.REQUIRED_MESSAGE)
    lateinit var name: String

    @NotBlank(message = Constant.REQUIRED_MESSAGE)
    @NotNull(message = Constant.REQUIRED_MESSAGE)
    lateinit var email: String

    var password: String? = null
    var code: String? = null
    var state: String? = null
}
