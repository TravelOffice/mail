package traveloffice.mail.validators

import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class OneOfValidator: ConstraintValidator<OneOf, String> {
    private var oneOfArray: Array<String> = emptyArray()
    override fun initialize(constraintAnnotation: OneOf?) {
        super.initialize(constraintAnnotation)
        oneOfArray = constraintAnnotation?.array ?: emptyArray()
    }
    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean {
        return oneOfArray.contains(value)
    }
}
