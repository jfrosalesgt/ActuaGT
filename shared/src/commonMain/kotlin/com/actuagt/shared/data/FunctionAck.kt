package com.actuagt.shared.data

import kotlinx.serialization.Serializable

// Success acknowledgement the admin functions return ({ok:true}).
// Decoded as a data class: GitLive cannot materialise Map targets on-device.
@Serializable
data class FunctionAck(val ok: Boolean = false)
