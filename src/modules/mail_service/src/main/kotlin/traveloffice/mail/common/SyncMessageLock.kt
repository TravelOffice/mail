package traveloffice.mail.common

import kotlinx.coroutines.sync.Mutex

object SyncMessageLock {
    val mutex: Mutex = Mutex()
}
