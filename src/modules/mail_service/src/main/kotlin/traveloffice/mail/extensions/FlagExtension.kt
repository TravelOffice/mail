package traveloffice.mail.extensions

import javax.mail.Flags

fun Flags.toList(): List<String> {
    var flagsString = mutableListOf<String>()
    // Get system flags
    var systemFlags = this.clone() as Flags
    systemFlags.clearUserFlags()
    flagsString.addAll(systemFlags.systemFlags.map {
        Flags(it).toString()
    })
    //Get user flags
    flagsString.addAll(this.userFlags.map {
        it.toString()
    })
    return flagsString
}
