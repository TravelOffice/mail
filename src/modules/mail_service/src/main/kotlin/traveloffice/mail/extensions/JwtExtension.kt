package traveloffice.mail.extensions

import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt
import traveloffice.mail.common.Constant
import traveloffice.mail.entities.AuthenticatedUser

/**
 * This fun to allow get tenant, username from cognito token
 */
fun SecurityContext.getUser() : AuthenticatedUser {
    val authentication = this.authentication
    val principal = authentication.principal as Jwt
    val cognitoUsername = principal.claims["cognito:username"] as String
    val arr = cognitoUsername.split(Constant.COGNITO_USERNAME_DELIMITER).toTypedArray()
    val tenant = arr[0]
    val username = arr[arr.size - 1]
    return AuthenticatedUser(
        username = username,
        tenant = tenant,
    )
}
