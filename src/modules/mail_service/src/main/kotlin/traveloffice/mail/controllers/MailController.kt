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
import traveloffice.mail.common.Constant.Companion.ERROR_UNKNOWN
import traveloffice.mail.common.Constant.Companion.ERROR_VALIDATOR
import traveloffice.mail.entities.*
import traveloffice.mail.entities.ResponseBody
import traveloffice.mail.exceptions.ResponseException
import traveloffice.mail.extensions.getUser
import traveloffice.mail.services.MailService
import java.lang.Exception
import java.util.function.Consumer
import javax.validation.ConstraintViolationException
import javax.validation.Valid
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Email


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
    fun linkEmail(@Valid @RequestBody mailAccount: MailAccount): ResponseEntity<ResponseBody<RestrictedMailAccount>> {
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
    fun getMailBoxes(@Valid @Email @RequestParam("email") mail: String): ResponseEntity<ResponseBody<List<Map<String, Any>>>> {
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

    @PostMapping("/sendEmail")
    fun sendEmail() {
        TODO("Method not implemented yet")
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
