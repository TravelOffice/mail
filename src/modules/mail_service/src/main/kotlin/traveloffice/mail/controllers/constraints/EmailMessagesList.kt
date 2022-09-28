package traveloffice.mail.controllers.constraints;

import traveloffice.mail.controllers.validators.EmailMessagesListValidator
import javax.validation.Constraint
import javax.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@MustBeDocumented
@Constraint(validatedBy = [EmailMessagesListValidator::class])
annotation class EmailMessagesList(
    val message: String = "Should be list of mail messages id",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
