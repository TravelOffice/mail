package traveloffice.mail.entities

data class PresignedUrlResponsePayload(
    var url: String,
    var key: String
)
