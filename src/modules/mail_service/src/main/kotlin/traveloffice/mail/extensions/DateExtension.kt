package traveloffice.mail.extensions

import java.util.*

fun Date.between(start: Date, end: Date): Boolean {
    return this.after(start) && this.before(end)
}
