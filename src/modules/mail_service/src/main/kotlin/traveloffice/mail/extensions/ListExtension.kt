package traveloffice.mail.extensions

fun<T> List<T>.indexOf(fn: (element: T) -> Boolean): Int {
    for (i in indices) {
        val e = this[i]
        if (fn(e)) {
            return i
        }
    }
    return -1
}
