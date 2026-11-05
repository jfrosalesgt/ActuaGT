package com.actuagt.shared.data

import dev.gitlive.firebase.storage.Data

// GitLive's Data wraps ByteArray on Android and NSData on iOS, so raw bytes
// cross the platform boundary through this seam.
expect fun ByteArray.toStorageData(): Data
