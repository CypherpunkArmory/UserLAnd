package tech.ula.utils

import android.content.Context
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import io.sentry.event.BreadcrumbBuilder
import io.sentry.event.Event
import io.sentry.event.EventBuilder
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
        val breadcrumb = BreadcrumbBuilder()
                .setCategory("Exception")
                .setData(mapOf(
                        "type" to err.javaClass.simpleName,
                        "stackTrace" to err.stackTrace.toString()
                ))
                .build()
        Sentry.getContext().recordBreadcrumb(breadcrumb)
    }

    override fun sendIllegalStateLog(state: IllegalState) {
        val message = state.javaClass.simpleName
        val event = EventBuilder()
                .withMessage(message)
                .withLevel(Event.Level.ERROR)
        Sentry.capture(event)
    }
}