package com.actuagt.shared.ui.screens

import com.actuagt.shared.media.NeutralizedText

// Decision for the title and description the user may have edited after
// dictation. Verify re-runs the neutral cleanup: security topics block the
// submit, cleaned text that only rewords is adopted so insults and personal
// references never reach moderation, identical text keeps flowing untouched.
sealed interface VerifyTextOutcome {
    data object Keep : VerifyTextOutcome

    data class Adopt(val title: String, val text: String) : VerifyTextOutcome

    data object BlockedSecurity : VerifyTextOutcome
}

fun resolveVerifyText(
    currentTitle: String,
    currentDescription: String,
    cleaned: NeutralizedText,
): VerifyTextOutcome {
    if (cleaned is NeutralizedText.SecurityAlert) return VerifyTextOutcome.BlockedSecurity
    cleaned as NeutralizedText.Clean
    return if (cleaned.title == currentTitle.trim() && cleaned.text == currentDescription.trim()) {
        VerifyTextOutcome.Keep
    } else {
        VerifyTextOutcome.Adopt(title = cleaned.title, text = cleaned.text)
    }
}
