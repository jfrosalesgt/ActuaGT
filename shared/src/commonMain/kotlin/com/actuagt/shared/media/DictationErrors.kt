package com.actuagt.shared.media

// Maps a neutralizeText failure to the Spanish message the citizen sees.
// The callable hides every cause behind one generic sentence on-device,
// so the mapping also prints the real error: on Android println lands in
// logcat, which is the only trace when the Meta call fails in the field.
fun userMessageForCleanupFailure(error: Throwable): String {
    println("ActuaGT cleanup failed: ${error::class.simpleName}: ${error.message}")
    val detail = (error.message ?: "").lowercase()
    return when {
        "unauthenticated" in detail || "sign-in is required" in detail ->
            "Tu sesión expiró. Ingresá de nuevo y probá dictar otra vez."
        "unusable answer" in detail || "respuesta de limpieza" in detail ->
            "La IA devolvió una respuesta incompleta. Probá dictar de nuevo con una frase corta."
        else ->
            "El servicio de limpieza no está disponible. Escribí la descripción manualmente."
    }
}
