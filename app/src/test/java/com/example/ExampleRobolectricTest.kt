package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.network.MlAndInstagramApiClient
import com.example.security.SecurityShield
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify security shield, username validation, public clue link builder, and area location discovery`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("InstaLens ML", appName)

        // Verify SecurityShield blocks untrusted servers, cleartext HTTP, and script injections
        assertTrue(SecurityShield.isTrustedHttpsUrl("https://www.instagram.com/natgeo/"))
        assertTrue(SecurityShield.isTrustedHttpsUrl("https://www.google.com/search?q=test"))
        assertFalse(SecurityShield.isTrustedHttpsUrl("http://www.instagram.com/natgeo/"))
        assertFalse(SecurityShield.isTrustedHttpsUrl("https://evil-attacker-server.com/steal"))
        assertFalse(SecurityShield.isTrustedHttpsUrl("javascript:alert(1)"))
        assertEquals("virat.kohli", SecurityShield.sanitizeInput("<script>virat.kohli'</script>"))

        // Verify official Instagram username syntax validation
        assertTrue(MlAndInstagramApiClient.isValidInstagramUsername("virat.kohli"))
        assertTrue(MlAndInstagramApiClient.isValidInstagramUsername("@natgeo_india"))
        assertFalse(MlAndInstagramApiClient.isValidInstagramUsername("..invalid.."))

        // Verify manual username candidate link generation
        val manualLinks = MlAndInstagramApiClient.buildCandidateLinksForManualQuery("@natgeo")
        assertTrue(manualLinks.isNotEmpty())
        assertEquals("https://www.instagram.com/natgeo/", manualLinks.first().instagramProfileUrl)

        // Verify area location Instagram discovery
        val areaInfo = MlAndInstagramApiClient.discoverInstagramIdsForArea(
            context = context,
            latitude = 25.5941,
            longitude = 85.1376,
            manualAreaOverride = "Patna, Bihar"
        )
        assertTrue(areaInfo.areaCandidateLinks.isNotEmpty())
        assertTrue(areaInfo.areaHashtags.isNotEmpty())

        // Verify blank photo without public clue produces empty candidate list and clear message (no fake match)
        val blankBmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        blankBmp.eraseColor(Color.DKGRAY)
        val clueResult = MlAndInstagramApiClient.analyzePhotoForPublicClues(blankBmp, "")
        assertTrue(clueResult.candidateLinks.isEmpty())
        assertTrue(clueResult.clearMessage.isNotBlank())
    }
}
