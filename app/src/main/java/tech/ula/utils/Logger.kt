package tech.ula.utils

import android.app.Application
import android.content.Context
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import io.sentry.event.BreadcrumbBuilder
import io.sentry.event.Event
import io.sentry.event.EventBuilder
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.HttpSenderConfigurationBuilder
import org.acra.data.StringFormat
import org.acra.sender.HttpSender
import tech.ula.BuildConfig
import tech.ula.viewmodel.IllegalState

interface Logger {
    fun initialize(context: Context? = null)

    fun addBreadcrumb(key: String, value: String)

    fun addExceptionBreadcrumb(err: Exception)

    fun sendIllegalStateLog(state: IllegalState)
}

class SentryLogger : Logger {
    override fun initialize(context: Context?) {
        Sentry.init(AndroidSentryClientFactory(context!!))
    }

    override fun addBreadcrumb(key: String, value: String) {
        val breadcrumb = BreadcrumbBuilder()
                .setCategory(key)
                .setMessage(value)
                .build()
        Sentry.getContext().recordBreadcrumb(breadcrumb)
    }

    override fun addExceptionBreadcrumb(err: Exception) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun sendIllegalStateLog(state: IllegalState) {
        val message = state.javaClass.simpleName
        val event = EventBuilder()
                .withMessage(message)
                .withLevel(Event.Level.ERROR)
        Sentry.capture(event)
    }
}

class AcraLogger : Logger {
    override fun initialize(context: Context?) {
        val builder = CoreConfigurationBuilder(context!!)
        builder.setBuildConfigClass(BuildConfig::class.java)
                .setReportFormat(StringFormat.JSON)
        builder.getPluginConfigurationBuilder(HttpSenderConfigurationBuilder::class.java)
                .setUri(BuildConfig.tracepotHttpsEndpoint)
                .setHttpMethod(HttpSender.Method.POST)
                .setEnabled(true)
        ACRA.init(context as Application, builder)
    }

    override fun addBreadcrumb(key: String, value: String) {
        ACRA.getErrorReporter().putCustomData(key, value)
    }

    override fun addExceptionBreadcrumb(err: Exception) {
        val topOfStackTrace = err.stackTrace.first()
        val key = "Exception: ${topOfStackTrace.fileName}"
        val value = "${topOfStackTrace.lineNumber}"
        ACRA.getErrorReporter().putCustomData(key, value)
//        return err
    }

    override fun sendIllegalStateLog(state: IllegalState) {
        val type = state.javaClass.simpleName
        addBreadcrumb("State when sending silent report", type)
        ACRA.getErrorReporter().handleSilentException(IllegalStateException(type))
    }
}