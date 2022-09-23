package traveloffice.mail.entities

data class AuthenticatedUser(
    val tenant: String,
    val username: String,
)
