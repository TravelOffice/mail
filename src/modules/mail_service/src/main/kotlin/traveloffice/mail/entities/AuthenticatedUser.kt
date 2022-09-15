package traveloffice.mail.entities

data class AuthenticatedUser(
    val username: String,
    val tenant: String,
)
