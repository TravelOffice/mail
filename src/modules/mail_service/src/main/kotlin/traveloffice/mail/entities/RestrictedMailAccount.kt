package traveloffice.mail.entities

data class RestrictedMailAccount(
    var email: String,
    var name: String,
    var protocol: String,
    var inboundHost: String,
    var inboundPort: Int,
    var inboundSslMode: String,
    var inboundUser: String,
    var outboundHost: String,
    var outboundPort: Int,
    var outboundSslMode: String,
    var outboundUser: String,
    var provider: String,
    var tenant: String,
    var username: String,
    var createdAt: Long,
    var updatedAt: Long,
)