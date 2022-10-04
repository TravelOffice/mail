package traveloffice.mail.extensions

import java.net.URLEncoder
import java.security.Key
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

fun String.encrypt(secretKey: String): String {
    val cipher: Cipher = Cipher.getInstance("AES")
    val aesKey: Key = SecretKeySpec(secretKey.toByteArray(), "AES")
    cipher.init(Cipher.ENCRYPT_MODE, aesKey)
    val encrypted: ByteArray = cipher.doFinal(this.toByteArray())
    return Base64.getEncoder().encodeToString(encrypted)
}

fun String.decrypt(secretKey: String): String {
    val cipher: Cipher = Cipher.getInstance("AES")
    val aesKey: Key = SecretKeySpec(secretKey.toByteArray(), "AES")
    cipher.init(Cipher.DECRYPT_MODE, aesKey)
    val decrypted = Base64.getDecoder().decode(this)
    return String(cipher.doFinal(decrypted))
}

fun String.encodeToURL(): String {
    return URLEncoder.encode(this, "UTF-8")
}
