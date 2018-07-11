package tech.ula.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.support.test.espresso.intent.Intents.intended
import android.support.test.espresso.intent.Intents.intending
import android.support.test.espresso.intent.matcher.IntentMatchers.*
import android.support.test.espresso.intent.rule.IntentsTestRule
import android.support.test.runner.AndroidJUnit4
import android.widget.Toast
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.*
import org.mockito.Mockito.*
import tech.ula.model.entities.Session

@RunWith(AndroidJUnit4::class)
class ClientUtilityTest {

    lateinit var clientUtility: ClientUtility


//    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    @Mock
    lateinit var context: Context

    @Mock
    lateinit var packageManager: PackageManager

    @Mock
    lateinit var toast: Toast

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        setupStubs()
        clientUtility = ClientUtility(context)
    }

    fun setupStubs() {
        `when`(context.packageManager).thenReturn(packageManager)
//        doNothing().`when`(toast.show())
//        `when`(resources.getText(anyInt())).thenReturn("")
    }

    @Test
    fun test() {
        val session = Session(0, filesystemId = 0, clientType = "ConnectBot")
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.connectbot"))
        marketIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        `when`(context.packageManager.queryIntentActivities(any(), eq(0))).thenReturn(listOf())

        clientUtility.startClient(session)

        intended(allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData(Uri.parse("market://details?id=org.connectbot")),
                hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK)
        ))
//        verify(context, times(1)).startActivity(marketIntent)
    }
}