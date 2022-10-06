package traveloffice.mail.extensions

import java.text.SimpleDateFormat
import java.util.*

fun Date.between(start: Date, end: Date): Boolean {
    return this.after(start) && this.before(end)
}

fun Date.getCronExpression(): String {
    val dateFormat = SimpleDateFormat("mm HH dd MM ? yyyy")
    return "cron(${dateFormat.format(this)})"
}
