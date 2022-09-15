package traveloffice.mail.validators

import javax.validation.Constraint
import kotlin.reflect.KClass

@Target(allowedTargets = [AnnotationTarget.FIELD])
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [OneOfValidator::class])
@MustBeDocumented
annotation class OneOf(
    val message: String = "{field.invalid}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Any>> = [],
    val array: Array<String>,
)
