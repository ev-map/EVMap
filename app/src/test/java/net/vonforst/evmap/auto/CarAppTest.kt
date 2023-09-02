package net.vonforst.evmap.auto

import android.content.ComponentName
import android.content.Intent
import androidx.car.app.HandshakeInfo
import androidx.car.app.testing.SessionController
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.TestScreenManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import net.vonforst.evmap.FakeAndroidKeyStore
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.internal.DoNotInstrument

@RunWith(RobolectricTestRunner::class)
@DoNotInstrument
@Ignore("Disabled because Robolectric does not yet support API 36")
class CarAppTest {
    private val testCarContext =
        TestCarContext.createCarContext(ApplicationProvider.getApplicationContext()).apply {
            updateHandshakeInfo(HandshakeInfo("auto.testing", 1))
        }

    @Before
    fun before() {
        FakeAndroidKeyStore.setup
    }

    @Test
    fun onCreateScreen_returnsExpectedScreen() {
        val service = Robolectric.setupService(CarAppService::class.java)
        val session = service.onCreateSession()
        val controller = SessionController(
            session, testCarContext,
            Intent().setComponent(
                ComponentName(testCarContext, CarAppService::class.java)
            )
        )
        controller.moveToState(Lifecycle.State.CREATED)
        val screenCreated =
            testCarContext.getCarService(TestScreenManager::class.java).screensPushed.last()

        // accept privacy required
        assert(screenCreated is AcceptPrivacyScreen)
    }
}