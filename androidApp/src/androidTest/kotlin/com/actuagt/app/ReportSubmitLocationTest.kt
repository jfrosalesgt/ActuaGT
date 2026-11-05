package com.actuagt.app

import com.actuagt.shared.auth.AppUser
import com.actuagt.shared.auth.AuthRepository
import com.actuagt.shared.data.ReportRepository
import com.actuagt.shared.media.CapturedPhoto
import com.actuagt.shared.media.NeutralizedText
import com.actuagt.shared.media.PhotoMetadata
import com.actuagt.shared.model.CoherenceVerdict
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.OfficialRequest
import com.actuagt.shared.model.Ratification
import com.actuagt.shared.model.ReportUpdate
import com.actuagt.shared.model.User
import com.actuagt.shared.ui.screens.ReportScreenModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Submit-validation for the report form, including the location gate: a
// manually marked point wins, photo EXIF is the fallback, and a report with
// neither is rejected before any upload. Photos are injected via reflection
// because the camera bridge is platform code; production code is untouched.
private class FakeAuthRepository : AuthRepository {
    override val session: Flow<AppUser?> =
        flowOf(AppUser(uid = "u1", displayName = null, email = null, photoUrl = null, isAnonymous = true))

    override suspend fun signInAnonymously(): Result<Unit> = Result.success(Unit)

    override suspend fun signInWithGoogle(): Result<Unit> = Result.success(Unit)

    override suspend fun signOut(): Unit = Unit

    override suspend fun getUserProfile(uid: String): User? = null

    override suspend fun ensureUserProfile(user: AppUser): Result<User> =
        Result.success(User(uid = user.uid))
}

private class FakeReportRepository : ReportRepository {
    var saved: IncidentReport? = null

    override val verifiedFeed: Flow<List<IncidentReport>> = flowOf(emptyList())

    override fun observeVerifiedFeedPage(limit: Long, startAfterEpochMillis: Long?): Flow<List<IncidentReport>> =
        flowOf(emptyList())

    override fun observeRatificationQueue(zone: String): Flow<List<IncidentReport>> = flowOf(emptyList())

    override fun observeAllPublishedReports(): Flow<List<IncidentReport>> = flowOf(emptyList())

    override fun observeAllProofReviews(): Flow<List<IncidentReport>> = flowOf(emptyList())

    override fun observeReport(reportId: String): Flow<IncidentReport?> = flowOf(null)

    override fun observeZoneReports(zone: String): Flow<List<IncidentReport>> = flowOf(emptyList())

    override fun newPendingReportId(): String = "r1"

    override suspend fun savePendingReport(report: IncidentReport): Result<String> {
        saved = report
        return Result.success(report.id)
    }

    override suspend fun addRatification(ratification: Ratification): Result<String> =
        Result.failure(NotImplementedError())

    var uploadCalls = 0
    val uploadedIndices = mutableListOf<Int>()
    var lastCoherenceUrls: List<String> = emptyList()

    override suspend fun uploadReportPhoto(
        reportId: String,
        photo: CapturedPhoto,
        photoIndex: Int,
    ): Result<String> {
        uploadCalls += 1
        uploadedIndices += photoIndex
        return Result.success("https://example.com/$reportId.jpg")
    }

    // Verify re-runs the neutral cleanup on the current text: echo it back
    // cleaned so the adopt path stays deterministic in these tests.
    var neutralizeResult: Result<NeutralizedText> =
        Result.success(NeutralizedText.Clean(title = "Título limpio", text = "Texto limpio"))

    override suspend fun neutralizeText(transcript: String): Result<NeutralizedText> =
        neutralizeResult

    override suspend fun verifyReportCoherence(
        title: String,
        description: String,
        photoUrls: List<String>,
    ): Result<CoherenceVerdict> {
        lastCoherenceUrls = photoUrls
        return if (coherenceMatch) {
            Result.success(CoherenceVerdict(match = true, reason = "ok"))
        } else {
            Result.success(CoherenceVerdict(match = false, reason = "las fotos no muestran lo descrito"))
        }
    }

    var coherenceMatch = true

    override suspend fun refreshPushToken(token: String): Result<Unit> = Result.success(Unit)

    override fun observeReportUpdates(reportId: String): Flow<List<ReportUpdate>> = flowOf(emptyList())

    override fun observeProofReviews(zone: String): Flow<List<IncidentReport>> = flowOf(emptyList())

    override suspend fun officialRespond(
        reportId: String,
        message: String?,
        status: String?,
        proofPhoto: CapturedPhoto?,
        manualReview: String?,
        reviewReason: String?,
    ): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun submitOfficialRequest(
        request: OfficialRequest,
        credentialPhoto: CapturedPhoto?,
    ): Result<Unit> = Result.failure(NotImplementedError())

    override fun observeOfficialRequests(): Flow<List<OfficialRequest>> = flowOf(emptyList())

    override suspend fun setOfficialRole(
        uid: String,
        zone: String?,
        institution: String?,
        approved: Boolean,
        reviewReason: String?,
    ): Result<Unit> = Result.failure(NotImplementedError())
}

// 8x8 red JPEG: the smallest real image the on-device verification
// encoder accepts, so verify() exercises the production encode path.
private const val TINY_JPEG_BASE64 =
    "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAIAAgDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDi6KKK+ZP3E//Z"

private fun photoWith(latitude: Double?, longitude: Double?): CapturedPhoto =
    CapturedPhoto(
        // Minimal valid JPEG so the real verification encoder runs on device.
        bytes = kotlin.io.encoding.Base64.Default.decode(TINY_JPEG_BASE64),
        metadata = PhotoMetadata(
            capturedAtEpochMillis = null,
            latitude = latitude,
            longitude = longitude,
        ),
    )

@Suppress("UNCHECKED_CAST")
private fun ReportScreenModel.setPhotos(photos: List<CapturedPhoto>) {
    val field = ReportScreenModel::class.java.getDeclaredField("_photos")
    field.isAccessible = true
    (field.get(this) as MutableStateFlow<List<CapturedPhoto>>).value = photos
}

private fun testModel(repository: FakeReportRepository = FakeReportRepository()): ReportScreenModel =
    ReportScreenModel(authRepository = FakeAuthRepository(), repository = repository)

private fun awaitResult(
    model: ReportScreenModel,
    repository: FakeReportRepository,
    timeoutMillis: Long = 10_000L,
) {
    // Done when the upload landed, a verdict arrived, or any terminal
    // message was set; polling on `sending` alone races with startup.
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (repository.saved == null && model.message.value == null && model.verdict.value == null &&
        System.currentTimeMillis() < deadline
    ) {
        Thread.sleep(50)
    }
}

private fun submitVerified(model: ReportScreenModel, repository: FakeReportRepository) {
    model.verifyReport()
    awaitResult(model, repository)
    model.submit()
    awaitSubmit(model, repository)
}

// Unlike awaitResult, this waits for the submit coroutine itself: after a
// successful verify the verdict is already set, so awaiting it would return
// before the submit even starts and the assertions would race the upload.
private fun awaitSubmit(
    model: ReportScreenModel,
    repository: FakeReportRepository,
    timeoutMillis: Long = 10_000L,
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (repository.saved == null &&
        model.message.value != "Reporte enviado. Quedará visible al reunir 3 ratificaciones." &&
        model.message.value != "No se pudo enviar el reporte. Revisá tu conexión e inténtalo de nuevo." &&
        System.currentTimeMillis() < deadline
    ) {
        Thread.sleep(50)
    }
}

class ReportSubmitLocationTest {
    @Test
    fun emptyFormRequiresTitleAndDescription() {
        val model = testModel()
        model.submit()
        assertEquals("Completá el título y la descripción.", model.message.value)
    }

    @Test
    fun formRequiresZone() {
        val model = testModel()
        model.onTitleChange("Bache")
        model.onDescriptionChange("Agujero")
        model.submit()
        assertEquals("Indicá la zona del reporte.", model.message.value)
    }

    @Test
    fun formRequiresPhoto() {
        val model = testModel()
        model.onTitleChange("Bache")
        model.onDescriptionChange("Agujero")
        model.onZoneChange("Mixco")
        model.submit()
        assertEquals("Agregá al menos una foto como evidencia.", model.message.value)
    }

    @Test
    fun formRequiresLocationWithoutManualPointOrExif() {
        val model = testModel()
        model.onTitleChange("Bache")
        model.onDescriptionChange("Agujero")
        model.onZoneChange("Mixco")
        model.setPhotos(listOf(photoWith(latitude = null, longitude = null)))
        model.submit()
        assertEquals("Marcá la ubicación del problema en el mapa.", model.message.value)
    }

    @Test
    fun manualPointBeatsExifFallback() {
        val repository = FakeReportRepository()
        val model = testModel(repository)
        model.onTitleChange("Bache")
        model.onDescriptionChange("Agujero")
        model.onZoneChange("Mixco")
        // Photo carries different EXIF coordinates: the manual point must win.
        model.setPhotos(listOf(photoWith(latitude = 1.0, longitude = 2.0)))
        model.onLocationChange(latitude = 14.61879, longitude = -90.53605)
        submitVerified(model, repository)
        assertTrue(repository.saved != null)
        val saved = repository.saved!!
        assertEquals(14.61879, saved.latitude, 0.0)
        assertEquals(-90.53605, saved.longitude, 0.0)
        assertEquals("Reporte enviado. Quedará visible al reunir 3 ratificaciones.", model.message.value)
    }

    @Test
    fun exifFallbackSuppliesLocationWithoutManualPoint() {
        val repository = FakeReportRepository()
        val model = testModel(repository)
        model.onTitleChange("Bache")
        model.onDescriptionChange("Agujero")
        model.onZoneChange("Mixco")
        model.setPhotos(listOf(photoWith(latitude = 14.5, longitude = -90.5)))
        submitVerified(model, repository)
        assertTrue(repository.saved != null)
        val saved = repository.saved!!
        assertEquals(14.5, saved.latitude, 0.0)
        assertEquals(-90.5, saved.longitude, 0.0)
    }

    @Test
    fun photoMismatchBlocksSubmit() {
        val repository = FakeReportRepository().also { it.coherenceMatch = false }
        val model = testModel(repository)
        model.onTitleChange("Bache")
        model.onDescriptionChange("Agujero")
        model.onZoneChange("Mixco")
        model.setPhotos(listOf(photoWith(latitude = 14.5, longitude = -90.5)))
        model.verifyReport()
        awaitResult(model, repository)
        assertTrue(model.verdict.value?.match == false)
        model.submit()
        awaitResult(model, repository)
        assertTrue(repository.saved == null)
        assertTrue((model.message.value ?: "").startsWith("Las fotos no coinciden con el reporte"))
    }

    @Test
    fun submitWithoutVerificationIsBlocked() {
        val repository = FakeReportRepository()
        val model = testModel(repository)
        model.onTitleChange("Bache")
        model.onDescriptionChange("Agujero")
        model.onZoneChange("Mixco")
        model.setPhotos(listOf(photoWith(latitude = 14.5, longitude = -90.5)))
        model.submit()
        awaitResult(model, repository)
        assertTrue(repository.saved == null)
        assertEquals("Verificá el reporte con la IA antes de enviarlo.", model.message.value)
    }

    @Test
    fun verifySendsInlinePhotoDataAndUploadsOnlyOnSubmit() {
        val repository = FakeReportRepository()
        val model = testModel(repository)
        model.onTitleChange("Bache")
        model.onDescriptionChange("Agujero")
        model.onZoneChange("Mixco")
        model.setPhotos(listOf(photoWith(latitude = 14.5, longitude = -90.5)))
        model.verifyReport()
        awaitResult(model, repository)
        assertTrue(model.verdict.value?.match == true)
        // Verification saw the photos inline: no Storage write happened.
        assertEquals(0, repository.uploadCalls)
        assertEquals(1, repository.lastCoherenceUrls.size)
        assertTrue(repository.lastCoherenceUrls.single().startsWith("data:image/jpeg;base64,"))
        model.submit()
        awaitSubmit(model, repository)
        // The single Storage write happens at submit with a stable name.
        assertEquals(1, repository.uploadCalls)
        assertEquals(listOf(0), repository.uploadedIndices)
        assertTrue(repository.saved != null)
    }

    @Test
    fun locationWithoutGeocoderKeepsTypedZone() {
        val model = testModel()
        model.onZoneChange("Mixco")
        model.onLocationChange(latitude = 14.5, longitude = -90.5)
        Thread.sleep(2000)
        assertEquals("Mixco", model.zone.value)
    }

    @Test
    fun formEditInvalidatesVerification() {
        val repository = FakeReportRepository()
        val model = testModel(repository)
        model.onTitleChange("Bache")
        model.onDescriptionChange("Agujero")
        model.onZoneChange("Mixco")
        model.setPhotos(listOf(photoWith(latitude = 14.5, longitude = -90.5)))
        model.verifyReport()
        awaitResult(model, repository)
        assertTrue(model.verdict.value?.match == true)
        model.onDescriptionChange("Agujero editado")
        assertTrue(model.verdict.value == null)
        model.submit()
        assertEquals("Verificá el reporte con la IA antes de enviarlo.", model.message.value)
        assertTrue(repository.saved == null)
    }

    @Test
    fun verifyBlocksSecurityText() {
        val repository = FakeReportRepository()
        repository.neutralizeResult = Result.success(NeutralizedText.SecurityAlert)
        val model = testModel(repository)
        model.onTitleChange("Robo")
        model.onDescriptionChange("Asaltaron la tienda")
        model.onZoneChange("Mixco")
        model.setPhotos(listOf(photoWith(latitude = 14.5, longitude = -90.5)))
        model.verifyReport()
        awaitResult(model, repository)
        assertTrue(model.verdict.value == null)
        assertTrue(repository.saved == null)
        assertEquals(
            "ActuaGT solo atiende infraestructura. Lo relatado parece un tema " +
                "de seguridad y no puede publicarse aquí.",
            model.message.value,
        )
    }

    @Test
    fun verifyAdoptsCleanedTextAndSuggestsCategory() {
        val repository = FakeReportRepository()
        val model = testModel(repository)
        model.onTitleChange("bache feo")
        model.onDescriptionChange("hoyo enorme que no deja pasar")
        model.onZoneChange("Mixco")
        model.setPhotos(listOf(photoWith(latitude = 14.5, longitude = -90.5)))
        model.verifyReport()
        awaitResult(model, repository)
        assertEquals("Título limpio", model.title.value)
        assertEquals("Texto limpio", model.description.value)
        assertTrue(model.verdict.value?.match == true)
        assertEquals(
            "Ajustamos el título y la descripción para mantener un tono neutral.",
            model.message.value,
        )
    }
}
