package traveloffice.mail.extensions

import java.io.InputStream

fun InputStream.subString(size: Int): String {
    if (this.available() > 0) {
        return if (size < this.available()) {
            String(this.readNBytes(size))
        } else {
            String(this.readAllBytes())
        }
    }
    return ""
}

