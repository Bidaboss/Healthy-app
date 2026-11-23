package com.bidaboss.healthy.ui

import com.bidaboss.healthy.HealthyApplication
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Les mêmes aperçus en mode sombre (retouches du 23/11/2026) : les images portent le suffixe « -sombre ». */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HealthyApplication::class, qualifiers = "w390dp-h1500dp-night-xhdpi")
class ScreenshotsDarkTest : ScreenshotsTest() {
    override val suffix = "-sombre"
}
