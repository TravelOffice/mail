package traveloffice.mail.controllers.validators

import org.hibernate.validator.internal.constraintvalidators.bv.NotBlankValidator
import traveloffice.mail.controllers.constraints.EmailMessagesList
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class EmailMessagesListValidator : ConstraintValidator<EmailMessagesList?, List<String>?> {
    override fun isValid(emails: List<String>?, context: ConstraintValidatorContext): Boolean {
        if (emails != null) {
            // Validate rule for message id
            // Rule can be validated follow system template in the future
            val validator = NotBlankValidator()
            return emails.all { it -> validator.isValid(it, context) }
        }

        return true
    }
}
