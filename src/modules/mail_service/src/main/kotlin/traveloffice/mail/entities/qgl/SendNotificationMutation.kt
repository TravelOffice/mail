package traveloffice.mail.entities.qgl

object SendNotificationMutation {
    const val sendNotificationGql =
        "mutation SendNotificationMutation(\$type: String, \$username: String, \$tenant: String, \$timestamp: AWSTimestamp!, \$data: AWSJSON) {\n" +
        "    sendNotification(type: \$type, username: \$username, tenant: \$tenant, timestamp: \$timestamp, data: \$data) {\n" +
        "      tenant\n" + "      username\n" + "      type\n" + "      timestamp\n" + "      data\n" + "    }\n" + "  }"
}
