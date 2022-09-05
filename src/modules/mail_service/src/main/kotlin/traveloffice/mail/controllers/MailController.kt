package traveloffice.mail.controllers

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import traveloffice.mail.entities.MailAccount
import java.util.function.Consumer
import javax.validation.Valid

@RestController
class MailController {

    @GetMapping("/healthcheck")
    fun healthCheck(): ResponseEntity<String> {
        return ResponseEntity.ok("OK")
    }
    @PostMapping("/link-email")
    fun linkEmail(@Valid @RequestBody mailAccount: MailAccount): ResponseEntity<String> {
        TODO("Method not implemented yet")
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