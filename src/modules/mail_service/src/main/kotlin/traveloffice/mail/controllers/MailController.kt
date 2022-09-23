package traveloffice.mail.controllers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import traveloffice.mail.common.Constant
import traveloffice.mail.common.Constant.Companion.ERROR_UNKNOWN
import traveloffice.mail.common.Constant.Companion.ERROR_VALIDATOR
import traveloffice.mail.entities.*
import traveloffice.mail.entities.ResponseBody
import traveloffice.mail.exceptions.ResponseException
import traveloffice.mail.extensions.getUser
import traveloffice.mail.services.MailService
import java.util.function.Consumer
import javax.validation.ConstraintViolationException
import javax.validation.Valid
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty


@RestController
@Validated
class MailController {
    @Autowired
    lateinit var mailService: MailService

    @GetMapping("/healthcheck")
    fun healthCheck(): ResponseEntity<String> {
        return ResponseEntity.ok("OK")
    }

    @PostMapping("/link-email")
    fun linkEmail(@Valid @RequestBody mailAccount: MailAccount): ResponseEntity<ResponseBody<Map<String, Any>>> {
        val authenticatedUser = SecurityContextHolder.getContext().getUser()
        mailService.linkEmail(mailAccount, authenticatedUser)
        return ResponseEntity.ok(
            ResponseBody(
                code = "SUCCESS", data = mailAccount.restrict(), message = "Link successfully"
            )
        )
    }

    @GetMapping("/presigned-url")
    fun getPresignedUrl(@RequestParam @NotEmpty fileName: String): ResponseEntity<ResponseBody<PresignedUrlResponsePayload>> {
        val presignedUrl = mailService.generatePresignedUrl(fileName)
        return ResponseEntity.ok(
            ResponseBody(
                code = "SUCCESS", data = presignedUrl, message = "Generate presigned-url successfully"
            )
        )
    }

    /**
     * API to get mail boxes for given [mail]
     *
     * Email param will be validated as email address format
     */
    @GetMapping("/mail-boxes")
    fun getMailBoxes(@Email @RequestParam("email") mail: String): ResponseEntity<ResponseBody<List<Map<String, Any>>>> {
        // Get user that own Cognito token
        val authenticatedUser = SecurityContextHolder.getContext().getUser()
        // Get mail boxes for given email
        val mailBoxes = mailService.getMailBoxes(mail, authenticatedUser)
        return ResponseEntity.ok(
            ResponseBody(
                code = "SUCCESS", data = mailBoxes, message = "Get mail boxes successfully"
            )
        )
    }

    @GetMapping("/mail-accounts")
    fun getUserMailAccounts(): ResponseEntity<ResponseBody<List<Map<String, Any>>>> {
        // Get user that own Cognito token
        val authenticatedUser = SecurityContextHolder.getContext().getUser()
        // Get mail accounts for authenticated user
        val accounts = mailService.getUserMailAccounts(authenticatedUser)
        return ResponseEntity.ok(
            ResponseBody(
                code = "SUCCESS", data = accounts, message = "Get user's mail accounts successfully"
            )
        )
    }

    @GetMapping("/mails")
    fun getMailsInsideMailBox(
        @Email @RequestParam("email") mail: String,
        @NotEmpty @RequestParam("mailbox") mailbox: String,
        @RequestParam("token") token: String?
    ): ResponseEntity<ResponseBody<Map<String, Any>>> {
        // Get user that own Cognito token
        val authenticatedUser = SecurityContextHolder.getContext().getUser()
        // Get mails related to mailbox for given email
        val mailsData = mailService.getMailsInsideMailBox(authenticatedUser, mail, mailbox, token)
        return ResponseEntity.ok(
            ResponseBody(
                code = "SUCCESS", data = mailsData, message = "Get [${mailbox}] mails successfully"
            )
        )
    }


    @PostMapping("/sendEmail")
    fun sendEmail() {
        TODO("Method not implemented yet")
    }

    /**
     * API to get mail message for given [mail] [mailId]
     *
     * Email param will be validated as email address format
     */
    @GetMapping("/mails")
    fun getMailMessagesFromMailBox(
        @Valid
        @Email(message = Constant.INVALID_MESSAGE)
        @NotBlank(message = Constant.REQUIRED_MESSAGE)
        @RequestParam("email") email: String,

        @Valid
        @NotBlank(message = Constant.REQUIRED_MESSAGE)
        @RequestParam("mailbox") mailbox: String,

        @Valid
        @NotBlank(message = Constant.REQUIRED_MESSAGE)
        @RequestParam("mailId")
        mailId: String,
    ): ResponseEntity<ResponseBody<List<Map<String, Any>>>> {
        // Get user that own Cognito token
        val authenticatedUser = SecurityContextHolder.getContext().getUser()
        val getMailRequest = GetMailRequestParams(email, mailbox, mailId)
        // Get mail message for given mailbox
        val mailMessages = mailService.getMailMessagesFromMailBox(getMailRequest, authenticatedUser)
        return ResponseEntity.ok(
            ResponseBody(
                code = "SUCCESS", data = mailMessages, message = "Get mail message successfully"
            )
        )
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(
        ex: MethodArgumentNotValidException
    ): ResponseEntity<ResponseBody<Any>> {
        val errors: MutableMap<String, String?> = HashMap()
        ex.bindingResult.allErrors.forEach(Consumer { error: ObjectError ->
            val fieldName = (error as FieldError).field
            val errorMessage = error.getDefaultMessage()
            errors[fieldName] = errorMessage
        })
        return ResponseEntity.ok(
            ValidatorResponseBody(
                code = ERROR_VALIDATOR,
                message = "Invalid params",
                data = null,
                errors = errors,
            )
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidationException(
        ex: ConstraintViolationException
    ): ResponseEntity<ResponseBody<Any>> {
        return ResponseEntity.ok(ResponseBody(code = ERROR_VALIDATOR, message = ex.message.toString()))
    }

    @ExceptionHandler(ResponseException::class)
    fun handleResponseException(
        ex: ResponseException
    ): ResponseEntity<ResponseBody<Any>> {
        return ResponseEntity.ok(ResponseBody(code = ex.code, message = ex.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleResponseException(
        ex: Exception
    ): ResponseEntity<ResponseBody<Any>> {
        return ResponseEntity.ok(ResponseBody(code = ERROR_UNKNOWN, message = ex.message.toString()))
    }
}
