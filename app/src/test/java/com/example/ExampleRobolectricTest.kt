package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.photo.ImageEditorUtils
import com.example.data.photo.PhotoEditSettings
import com.example.data.photo.PhotoFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Smart Generic Modle", appName)
  }

  @Test
  fun `photo edit settings default check`() {
    val settings = PhotoEditSettings()
    assertTrue(settings.isDefault)

    val modified = settings.copy(filter = PhotoFilter.VIBRANT)
    assertFalse(modified.isDefault)

    val modifiedBrightness = settings.copy(brightness = 15f)
    assertFalse(modifiedBrightness.isDefault)
  }

  @Test
  fun `apply edits to test bitmap`() {
    val testBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    val settings = PhotoEditSettings(
      filter = PhotoFilter.CYBERPUNK,
      brightness = 10f,
      contrast = 1.2f,
      saturation = 1.3f
    )
    val edited = ImageEditorUtils.applyEditsToBitmap(testBmp, settings)
    assertNotNull(edited)
    assertEquals(100, edited.width)
    assertEquals(100, edited.height)
  }
}

