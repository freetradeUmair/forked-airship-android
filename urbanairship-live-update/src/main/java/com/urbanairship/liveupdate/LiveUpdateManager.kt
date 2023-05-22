/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.Logger
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.PrivacyManager.FEATURE_PUSH
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.json.JsonMap
import com.urbanairship.liveupdate.api.ChannelBulkUpdateApiClient
import com.urbanairship.liveupdate.api.LiveUpdateMutation
import com.urbanairship.liveupdate.data.LiveUpdateDatabase
import com.urbanairship.liveupdate.notification.LiveUpdatePayload
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager

/**
 * Airship Live Updates.
 */
public class LiveUpdateManager

/** @hide */
@VisibleForTesting
internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    config: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    private val pushManager: PushManager,
    private val channel: AirshipChannel,
    private val bulkUpdateClient: ChannelBulkUpdateApiClient = ChannelBulkUpdateApiClient(config),
    db: LiveUpdateDatabase = LiveUpdateDatabase.createDatabase(context, config),
    private val registrar: LiveUpdateRegistrar = LiveUpdateRegistrar(context, db.liveUpdateDao()),
) : AirshipComponent(context, dataStore) {

    private val isFeatureEnabled: Boolean
        get() = privacyManager.isEnabled(FEATURE_PUSH) && channel.id != null

    private val pushListener = PushListener { message, _ ->
        message.liveUpdatePayload
            ?.let { LiveUpdatePayload.fromJson(it) }
            ?.let { registrar.onLiveUpdatePushReceived(message, it) }
    }

    public constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        channel: AirshipChannel,
        pushManager: PushManager
    ) : this(context, dataStore, config, privacyManager, pushManager, channel)

    /**
     * Registers a [handler] for the given [type].
     *
     * @param type The handler type.
     * @param handler A [LiveUpdateHandler].
     */
    public fun register(type: String, handler: LiveUpdateHandler<*>) {
        registrar.register(type, handler)
    }

    /**
     * Starts tracking for a Live Update, with initial [content].
     *
     * @param name The Live Update name.
     * @param type The handler type.
     * @param content A [JsonMap] with initial content.
     * @param timestamp The start timestamp, used to filter out-of-order events (default: now).
     * @param dismissTimestamp Optional timestamp, when to stop this Live Update (default: null).
     */
    @JvmOverloads
    public fun start(
        name: String,
        type: String,
        content: JsonMap,
        timestamp: Long = System.currentTimeMillis(),
        dismissTimestamp: Long? = null,
    ) {
        if (isFeatureEnabled) {
            registrar.start(name, type, content, timestamp, dismissTimestamp)
        }
    }

    /**
     * Updates the [content] for a tracked Live Update.
     *
     * @param name The live update name.
     * @param content A [JsonMap] with updated content.
     * @param timestamp The update timestamp, used to filter out-of-order events (default: now).
     */
    @JvmOverloads
    public fun update(
        name: String,
        content: JsonMap,
        timestamp: Long = System.currentTimeMillis(),
        dismissTimestamp: Long? = null,
    ) {
        if (isFeatureEnabled) {
            registrar.update(name, content, timestamp, dismissTimestamp)
        }
    }

    /**
     * Stops tracking for the Live Update with the given [name].
     *
     * @param name The live update name.
     * @param timestamp The stop timestamp, used to filter out-of-order events (default: now).
     */
    @JvmOverloads
    public fun stop(
        name: String,
        content: JsonMap? = null,
        timestamp: Long = System.currentTimeMillis(),
        dismissTimestamp: Long? = null,
    ) {
        if (isFeatureEnabled) {
            registrar.stop(name, content, timestamp, dismissTimestamp)
        }
    }

    /** Stops tracking for all active Live Updates. */
    public fun clearAll() {
        if (isFeatureEnabled) {
            registrar.clearAll()
        }
    }

    /**
     * Cancels the notification associated with the given Live Update [name].
     *
     * This will not stop tracking the Live Update and is a no-op for live updates that use custom
     * handlers.
     *
     * @param name The live update name.
     */
    internal fun cancel(name: String) {
        registrar.cancel(name)
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getComponentGroup(): Int = AirshipComponentGroups.LIVE_UPDATE

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public override fun init() {
        super.init()

        privacyManager.addListener { updateLiveActivityEnablement() }
        updateLiveActivityEnablement()
    }

    override fun onPerformJob(airship: UAirship, jobInfo: JobInfo): JobResult {
        return when (jobInfo.action) {
            ACTION_UPDATE_CHANNEL ->
                channel.id?.let { channelId ->
                    try {
                        val update = LiveUpdateMutation.fromJson(jobInfo.extras)
                        val resp = bulkUpdateClient.update(channelId, liveUpdates = listOf(update))
                        if (resp.isSuccessful) {
                            JobResult.SUCCESS
                        } else {
                            JobResult.RETRY
                        }
                    } catch (e: Throwable) {
                        Logger.error(e, "Failed to batch update channel for live update.")
                        JobResult.RETRY
                    }
                } ?: run {
                    Logger.warn("Unable to update channel for live update. Channel ID is null.")
                    JobResult.RETRY
                }
            else -> {
                Logger.debug("Unexpected job: $jobInfo")
                JobResult.SUCCESS
            }
        }
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onComponentEnableChange(isEnabled: Boolean): Unit =
        updateLiveActivityEnablement()

    private fun updateLiveActivityEnablement() {
        if (isFeatureEnabled) {
            pushManager.addPushListener(pushListener)
        } else {
            // Clear all live updates.
            registrar.clearAll()
            pushManager.removePushListener(pushListener)
        }
    }

    public companion object {
        internal const val ACTION_UPDATE_CHANNEL = "ACTION_UPDATE_CHANNEL"

        /**
         * Gets the shared [LiveUpdateManager] instance.
         *
         * @return the shared instance of `LiveUpdateManager`.
         */
        @JvmStatic
        public fun shared(): LiveUpdateManager =
            UAirship.shared().requireComponent(LiveUpdateManager::class.java)
    }
}
