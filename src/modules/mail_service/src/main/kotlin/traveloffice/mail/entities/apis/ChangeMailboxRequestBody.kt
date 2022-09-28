package traveloffice.mail.entities.apis

import com.fasterxml.jackson.annotation.JsonProperty
import traveloffice.mail.controllers.constraints.EmailMessagesList
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

data class ChangeMailboxRequestBody(
    @field:NotBlank @field:NotNull val from: String,
    @field:NotBlank @field:NotNull val to: String,
    @field:Email @field:NotNull val mail: String,
    @field:EmailMessagesList @field:NotEmpty @JsonProperty("messages") val messages: List<String>?
)
