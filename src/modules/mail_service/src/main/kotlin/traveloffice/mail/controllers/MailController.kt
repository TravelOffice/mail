package traveloffice.mail.controllers

import com.google.gson.Gson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import traveloffice.mail.entities.MailAccount
import traveloffice.mail.entities.ResponseBody
import traveloffice.mail.entities.RestrictedMailAccount
import traveloffice.mail.extensions.getUser
import traveloffice.mail.services.MailService
import java.lang.Exception
import java.util.function.Consumer
import javax.validation.Valid


@RestController
class MailController {
    @Autowired
    lateinit var mailService: MailService

    @GetMapping("/healthcheck")
    fun healthCheck(): ResponseEntity<String> {
        return ResponseEntity.ok("OK")
    }

    @PostMapping("/link-email")
    fun linkEmail(@Valid @RequestBody mailAccount: MailAccount): ResponseEntity<ResponseBody<RestrictedMailAccount>> {
        return try {
            val authenticatedUser = SecurityContextHolder.getContext().getUser()
            mailService.linkEmail(mailAccount, authenticatedUser)
            ResponseEntity.ok(ResponseBody(code = "SUCCESS", data = mailAccount.restrict(), message = "Link successfully"))
        } catch (e: Exception) {
            ResponseEntity.ok(ResponseBody(code = "FAILED", message = e.message.toString()))
        }
    }

    @PostMapping("/sendEmail")
    fun sendEmail() {
        TODO("Method not implemented yet")
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(
        ex: MethodArgumentNotValidException
    ): Map<String, String?>? {
        val errors: MutableMap<String, String?> = HashMap()
        ex.bindingResult.allErrors.forEach(Consumer { error: ObjectError ->
            val fieldName = (error as FieldError).field
            val errorMessage = error.getDefaultMessage()
            errors[fieldName] = errorMessage
        })
        return errors
    }
}