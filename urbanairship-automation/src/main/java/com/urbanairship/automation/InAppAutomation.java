/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.AirshipLoopers;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.auth.AuthException;
import com.urbanairship.automation.auth.AuthManager;
import com.urbanairship.automation.deferred.Deferred;
import com.urbanairship.automation.deferred.DeferredScheduleClient;
import com.urbanairship.automation.limits.FrequencyChecker;
import com.urbanairship.automation.limits.FrequencyConstraint;
import com.urbanairship.automation.limits.FrequencyLimitManager;
import com.urbanairship.automation.tags.AudienceManager;
import com.urbanairship.automation.tags.TagGroupResult;
import com.urbanairship.automation.tags.TagGroupUtils;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.iam.InAppAutomationScheduler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageManager;
import com.urbanairship.json.JsonMap;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.util.RetryingExecutor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

/**
 * In-app automation.
 */
public class InAppAutomation extends AirshipComponent implements InAppAutomationScheduler {

    /**
     * Preference key for pausing/unpausing in-app automation.
     */
    private final static String PAUSE_KEY = "com.urbanairship.iam.paused";

    // State
    private final InAppRemoteDataObserver remoteDataSubscriber;
    private final Handler backgroundHandler;
    private final AirshipChannel airshipChannel;
    private final AutomationEngine automationEngine;
    private final InAppMessageManager inAppMessageManager;
    private final AudienceManager audienceManager;
    private final RetryingExecutor retryingExecutor;
    private final DeferredScheduleClient deferredScheduleClient;
    private final FrequencyLimitManager frequencyLimitManager;
    private final PrivacyManager privacyManager;

    private final ActionsScheduleDelegate actionScheduleDelegate;
    private final InAppMessageScheduleDelegate inAppMessageScheduleDelegate;

    private final Map<String, ScheduleDelegate<?>> scheduleDelegateMap = new HashMap<>();
    private final Map<String, FrequencyChecker> frequencyCheckerMap = new HashMap<>();

    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private Subscription subscription;

    private final AutomationDriver driver = new AutomationDriver() {
        @Override
        public void onScheduleExecutionInterrupted(Schedule<? extends ScheduleData> schedule) {
            InAppAutomation.this.onScheduleExecutionInterrupted(schedule);
        }

        @Override
        public void onPrepareSchedule(@NonNull Schedule schedule, @Nullable TriggerContext triggerContext, @NonNull PrepareScheduleCallback callback) {
            InAppAutomation.this.onPrepareSchedule(schedule, triggerContext, callback);
        }

        @Override
        public int onCheckExecutionReadiness(@NonNull Schedule schedule) {
            return InAppAutomation.this.onCheckExecutionReadiness(schedule);
        }

        @Override
        public void onExecuteTriggeredSchedule(@NonNull Schedule schedule, @NonNull ExecutionCallback finishCallback) {
            InAppAutomation.this.onExecuteTriggeredSchedule(schedule, finishCallback);
        }
    };

    private final InAppRemoteDataObserver.Delegate remoteDataObserverDelegate = new InAppRemoteDataObserver.Delegate() {
        @Override
        @NonNull
        public PendingResult<Collection<Schedule<? extends ScheduleData>>> getSchedules() {
            return InAppAutomation.this.getSchedules();
        }

        @Override
        @NonNull
        public PendingResult<Boolean> editSchedule(@NonNull String scheduleId, @NonNull ScheduleEdits<? extends ScheduleData> edits) {
            return InAppAutomation.this.editSchedule(scheduleId, edits);
        }

        @NonNull
        @Override
        public PendingResult<Boolean> schedule(@NonNull List<Schedule<? extends ScheduleData>> schedules) {
            return InAppAutomation.this.schedule(schedules);
        }

        @Override
        public Future<Boolean> updateConstraints(@NonNull Collection<FrequencyConstraint> constraints) {
            return frequencyLimitManager.updateConstraints(constraints);
        }
    };

    private final PrivacyManager.Listener privacyManagerListener = new PrivacyManager.Listener() {
        @Override
        public void onEnabledFeaturesChanged() {
            checkUpdatesSubscription();
            updateEnginePauseState();
        }
    };

    /**
     * Gets the shared In-App Automation instance.
     *
     * @return The shared In-App Automation instance.
     */
    @NonNull
    public static InAppAutomation shared() {
        return UAirship.shared().requireComponent(InAppAutomation.class);
    }

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param runtimeConfig The runtime config.
     * @param privacyManager The privacy manager.
     * @param analytics Analytics instance.
     * @param remoteData Remote data.
     * @param airshipChannel The airship channel.
     * @param namedUser The named user.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public InAppAutomation(@NonNull Context context,
                           @NonNull PreferenceDataStore preferenceDataStore,
                           @NonNull AirshipRuntimeConfig runtimeConfig,
                           @NonNull PrivacyManager privacyManager,
                           @NonNull Analytics analytics,
                           @NonNull RemoteData remoteData,
                           @NonNull AirshipChannel airshipChannel,
                           @NonNull NamedUser namedUser) {
        super(context, preferenceDataStore);
        this.privacyManager = privacyManager;
        this.automationEngine = new AutomationEngine(context, runtimeConfig, analytics, preferenceDataStore);
        this.airshipChannel = airshipChannel;
        this.audienceManager = new AudienceManager(runtimeConfig, airshipChannel, namedUser, preferenceDataStore);
        this.remoteDataSubscriber = new InAppRemoteDataObserver(preferenceDataStore, remoteData);
        this.inAppMessageManager = new InAppMessageManager(context, preferenceDataStore, analytics, new InAppMessageManager.Delegate() {
            @Override
            public void onReadinessChanged() {
                automationEngine.checkPendingSchedules();
            }
        });

        this.backgroundHandler = new Handler(AirshipLoopers.getBackgroundLooper());
        this.retryingExecutor = RetryingExecutor.newSerialExecutor(Looper.getMainLooper());

        this.deferredScheduleClient = new DeferredScheduleClient(runtimeConfig, new AuthManager(runtimeConfig, airshipChannel));
        this.actionScheduleDelegate = new ActionsScheduleDelegate();
        this.inAppMessageScheduleDelegate = new InAppMessageScheduleDelegate(inAppMessageManager);
        this.frequencyLimitManager = new FrequencyLimitManager(context, runtimeConfig);
    }

    @VisibleForTesting
    InAppAutomation(@NonNull Context context,
                    @NonNull PreferenceDataStore preferenceDataStore,
                    @NonNull PrivacyManager privacyManager,
                    @NonNull AutomationEngine engine,
                    @NonNull AirshipChannel airshipChannel,
                    @NonNull AudienceManager audienceManager,
                    @NonNull InAppRemoteDataObserver observer,
                    @NonNull InAppMessageManager inAppMessageManager,
                    @NonNull RetryingExecutor retryingExecutor,
                    @NonNull DeferredScheduleClient deferredScheduleClient,
                    @NonNull ActionsScheduleDelegate actionsScheduleDelegate,
                    @NonNull InAppMessageScheduleDelegate inAppMessageScheduleDelegate,
                    @NonNull FrequencyLimitManager frequencyLimitManager) {
        super(context, preferenceDataStore);
        this.privacyManager = privacyManager;
        this.automationEngine = engine;
        this.airshipChannel = airshipChannel;
        this.audienceManager = audienceManager;
        this.remoteDataSubscriber = observer;
        this.inAppMessageManager = inAppMessageManager;
        this.retryingExecutor = retryingExecutor;
        this.deferredScheduleClient = deferredScheduleClient;
        this.backgroundHandler = new Handler(AirshipLoopers.getBackgroundLooper());
        this.actionScheduleDelegate = actionsScheduleDelegate;
        this.inAppMessageScheduleDelegate = inAppMessageScheduleDelegate;
        this.frequencyLimitManager = frequencyLimitManager;
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    protected void init() {
        super.init();
        audienceManager.setRequestTagsCallback(new AudienceManager.RequestTagsCallback() {
            @NonNull
            @Override
            public Map<String, Set<String>> getTags() throws ExecutionException, InterruptedException {
                Map<String, Set<String>> tags = new HashMap<>();

                Collection<Schedule<? extends ScheduleData>> schedules = getSchedules().get();
                if (schedules == null) {
                    return tags;
                }

                for (Schedule<? extends ScheduleData> schedule : schedules) {
                    Audience audience = schedule.getAudience();
                    if (audience != null && audience.getTagSelector() != null && audience.getTagSelector().containsTagGroups()) {
                        TagGroupUtils.addAll(tags, audience.getTagSelector().getTagGroups());
                    }
                }

                return tags;
            }
        });

        this.automationEngine.setScheduleListener(new AutomationEngine.ScheduleListener() {
            @Override
            public void onScheduleExpired(@NonNull final Schedule<? extends ScheduleData> schedule) {
                ScheduleDelegate<? extends ScheduleData> delegate = delegateForSchedule(schedule);
                if (delegate != null) {
                    delegate.onScheduleFinished(schedule);
                }
            }

            @Override
            public void onScheduleCancelled(@NonNull final Schedule<? extends ScheduleData> schedule) {
                ScheduleDelegate<? extends ScheduleData> delegate = delegateForSchedule(schedule);
                if (delegate != null) {
                    delegate.onScheduleFinished(schedule);
                }
            }

            @Override
            public void onScheduleLimitReached(@NonNull final Schedule<? extends ScheduleData> schedule) {
                ScheduleDelegate<? extends ScheduleData> delegate = delegateForSchedule(schedule);
                if (delegate != null) {
                    delegate.onScheduleFinished(schedule);
                }
            }

            @Override
            public void onNewSchedule(@NonNull final Schedule<? extends ScheduleData> schedule) {
                ScheduleDelegate<? extends ScheduleData> delegate = delegateForSchedule(schedule);
                if (delegate != null) {
                    delegate.onNewSchedule(schedule);
                }
            }
        });
        updateEnginePauseState();
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    public int getComponentGroup() {
        return AirshipComponentGroups.IN_APP;
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onAirshipReady(@NonNull UAirship airship) {
        super.onAirshipReady(airship);
        inAppMessageManager.onAirshipReady();
        privacyManager.addListener(privacyManagerListener);
        checkUpdatesSubscription();
    }

    @Override
    protected void tearDown() {
        super.tearDown();
        if (subscription != null) {
            subscription.cancel();
        }
        automationEngine.stop();
        isStarted.set(false);
        privacyManager.removeListener(privacyManagerListener);
    }

    @Override
    public void onNewConfig(@Nullable JsonMap configValue) {
        InAppRemoteConfig config = InAppRemoteConfig.fromJsonMap(configValue);
        audienceManager.setEnabled(config.tagGroupsConfig.isEnabled);
        audienceManager.setCacheStaleReadTime(config.tagGroupsConfig.cacheStaleReadTimeSeconds, TimeUnit.SECONDS);
        audienceManager.setPreferLocalTagDataTime(config.tagGroupsConfig.cachePreferLocalTagDataTimeSeconds, TimeUnit.SECONDS);
        audienceManager.setCacheMaxAgeTime(config.tagGroupsConfig.cacheMaxAgeInSeconds, TimeUnit.SECONDS);
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    protected void onComponentEnableChange(boolean isEnabled) {
        updateEnginePauseState();
    }

    /**
     * Gets the in-app message manager.
     *
     * @return The in-app message manager.
     */
    public InAppMessageManager getInAppMessageManager() {
        return inAppMessageManager;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Boolean> schedule(@NonNull List<Schedule<? extends ScheduleData>> schedules) {
        ensureStarted();
        return automationEngine.schedule(schedules);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Boolean> schedule(@NonNull Schedule<? extends ScheduleData> schedule) {
        ensureStarted();
        return automationEngine.schedule(schedule);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public PendingResult<Boolean> cancelSchedule(@NonNull String scheduleId) {
        ensureStarted();
        return automationEngine.cancel(Collections.singletonList(scheduleId));
    }

    /**
     * Cancels all schedules of a given type.
     *
     * @param type The type.
     * @return {@code true} if a schedule was cancelled, otherwise {@code false}.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public PendingResult<Boolean> cancelSchedules(@NonNull @Schedule.Type String type) {
        ensureStarted();
        return automationEngine.cancelByType(type);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public PendingResult<Boolean> cancelScheduleGroup(@NonNull String group) {
        ensureStarted();
        return automationEngine.cancelGroup(group);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<Actions>>> getActionScheduleGroup(@NonNull final String group) {
        ensureStarted();
        return automationEngine.getSchedules(group, Schedule.TYPE_ACTION);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Schedule<Actions>> getActionSchedule(@NonNull String scheduleId) {
        ensureStarted();
        return automationEngine.getSchedule(scheduleId, Schedule.TYPE_ACTION);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<Actions>>> getActionSchedules() {
        ensureStarted();
        return automationEngine.getSchedulesByType(Schedule.TYPE_ACTION);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<InAppMessage>>> getMessageScheduleGroup(@NonNull String group) {
        ensureStarted();
        return automationEngine.getSchedules(group, Schedule.TYPE_IN_APP_MESSAGE);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Schedule<InAppMessage>> getMessageSchedule(@NonNull String scheduleId) {
        ensureStarted();
        return automationEngine.getSchedule(scheduleId, Schedule.TYPE_IN_APP_MESSAGE);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Collection<Schedule<InAppMessage>>> getMessageSchedules() {
        ensureStarted();
        return automationEngine.getSchedulesByType(Schedule.TYPE_IN_APP_MESSAGE);
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public PendingResult<Schedule<Deferred>> getDeferredMessageSchedule(@NonNull String scheduleId) {
        ensureStarted();
        return automationEngine.getSchedule(scheduleId, Schedule.TYPE_DEFERRED);
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public PendingResult<Schedule<? extends ScheduleData>> getSchedule(@NonNull String scheduleId) {
        ensureStarted();
        return automationEngine.getSchedule(scheduleId);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public PendingResult<Collection<Schedule<? extends ScheduleData>>> getSchedules() {
        ensureStarted();
        return automationEngine.getSchedules();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PendingResult<Boolean> editSchedule(@NonNull String scheduleId, @NonNull ScheduleEdits<? extends ScheduleData> edits) {
        ensureStarted();
        return automationEngine.editSchedule(scheduleId, edits);
    }

    /**
     * Pauses or unpauses automations.
     *
     * @param paused {@code true} to pause in-app automations, otherwise {@code false}.
     */
    public void setPaused(boolean paused) {
        boolean storedPausedState = getDataStore().getBoolean(PAUSE_KEY, paused);

        // Only update when paused state transitions from paused to unpaused
        if (storedPausedState && !paused) {
            automationEngine.checkPendingSchedules();
        }

        getDataStore().put(PAUSE_KEY, paused);
    }

    /**
     * Returns {@code true} if automations are paused, otherwise {@code false}.
     *
     * @return {@code true} automations are paused, otherwise {@code false}.
     */
    public boolean isPaused() {
        return getDataStore().getBoolean(PAUSE_KEY, false);
    }

    /**
     * Enables or disables automations.
     *
     * @param enabled {@code true} to enable automations, otherwise {@code false}.
     * @deprecated Enable/disable by enabling {@link PrivacyManager#FEATURE_IN_APP_AUTOMATION} in {@link PrivacyManager}.
     * This will call through to the privacy manager
     */
    @Deprecated
    public void setEnabled(boolean enabled) {
        if (enabled) {
            privacyManager.enable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        } else {
            privacyManager.disable(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
        }
    }

    /**
     * Returns {@code true} if in-app automation is enabled, {@code false} if its disabled.
     *
     * @return {@code true} if in-app automation is enabled, {@code false} if its disabled.
     * @deprecated Use {@link PrivacyManager#isEnabled(int...)} to check {@link PrivacyManager#FEATURE_IN_APP_AUTOMATION}.
     */
    @Deprecated
    public boolean isEnabled() {
        return privacyManager.isEnabled(PrivacyManager.FEATURE_IN_APP_AUTOMATION);
    }

    @WorkerThread
    private void onPrepareSchedule(final @NonNull Schedule<? extends ScheduleData> schedule,
                                   final @Nullable TriggerContext triggerContext,
                                   final @NonNull AutomationDriver.PrepareScheduleCallback callback) {
        Logger.verbose("onPrepareSchedule schedule: %s, trigger context: %s", schedule.getId(), triggerContext);

        final AutomationDriver.PrepareScheduleCallback callbackWrapper = new AutomationDriver.PrepareScheduleCallback() {
            @Override
            public void onFinish(int result) {
                if (result != AutomationDriver.PREPARE_RESULT_CONTINUE) {
                    frequencyCheckerMap.remove(schedule.getId());
                }
                callback.onFinish(result);
            }
        };

        if (isScheduleInvalid(schedule)) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    // If the subscriber is already up to date, invalidate immediately
                    if (remoteDataSubscriber.isUpToDate()) {
                        callbackWrapper.onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
                    } else {
                        // Otherwise wait to invalidate
                        remoteDataSubscriber.addListener(new InAppRemoteDataObserver.Listener() {
                            @Override
                            public void onSchedulesUpdated() {
                                callbackWrapper.onFinish(AutomationDriver.PREPARE_RESULT_INVALIDATE);
                                remoteDataSubscriber.removeListener(this);
                            }
                        });
                    }
                }
            });
            return;
        }

        RetryingExecutor.Operation getFrequencyChecker = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                if (!schedule.getFrequencyConstraintIds().isEmpty()) {
                    FrequencyChecker frequencyChecker = getFrequencyChecker(schedule);
                    if (frequencyChecker == null) {
                        return RetryingExecutor.RESULT_RETRY;
                    }
                    frequencyCheckerMap.put(schedule.getId(), frequencyChecker);
                    if (frequencyChecker.isOverLimit()) {
                        // The frequency constraint is exceeded, skip
                        callbackWrapper.onFinish(AutomationDriver.PREPARE_RESULT_SKIP);
                    }
                }

                return RetryingExecutor.RESULT_FINISHED;
            }
        };

        // Audience checks
        RetryingExecutor.Operation checkAudience = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                Map<String, Set<String>> tagGroups = null;

                if (schedule.getAudience() == null) {
                    return RetryingExecutor.RESULT_FINISHED;
                }

                if (schedule.getAudience().getTagSelector() != null && schedule.getAudience().getTagSelector().containsTagGroups()) {
                    Map<String, Set<String>> tags = schedule.getAudience().getTagSelector().getTagGroups();
                    TagGroupResult result = audienceManager.getTags(tags);
                    if (!result.success) {
                        return RetryingExecutor.RESULT_RETRY;
                    }

                    tagGroups = result.tagGroups;
                }

                if (AudienceChecks.checkAudience(getContext(), schedule.getAudience(), tagGroups)) {
                    return RetryingExecutor.RESULT_FINISHED;
                }

                callbackWrapper.onFinish(getPrepareResultMissedAudience(schedule));
                return RetryingExecutor.RESULT_CANCEL;
            }
        };

        RetryingExecutor.Operation prepareSchedule = new RetryingExecutor.Operation() {
            @Override
            public int run() {
                switch (schedule.getType()) {
                    case Schedule.TYPE_DEFERRED:
                        return resolveDeferred(schedule, triggerContext, callbackWrapper);
                    case Schedule.TYPE_ACTION:
                        prepareSchedule(schedule, (Actions) schedule.coerceType(), actionScheduleDelegate, callbackWrapper);
                        break;
                    case Schedule.TYPE_IN_APP_MESSAGE:
                        prepareSchedule(schedule, (InAppMessage) schedule.coerceType(), inAppMessageScheduleDelegate, callbackWrapper);
                        break;
                }

                return RetryingExecutor.RESULT_FINISHED;
            }
        };

        retryingExecutor.execute(getFrequencyChecker, checkAudience, prepareSchedule);
    }

    private <T extends ScheduleData> void prepareSchedule(final Schedule<? extends ScheduleData> schedule, T scheduleData, final ScheduleDelegate<T> delegate, final @NonNull AutomationDriver.PrepareScheduleCallback callback) {
        delegate.onPrepareSchedule(schedule, scheduleData, new AutomationDriver.PrepareScheduleCallback() {
            @Override
            public void onFinish(int result) {
                if (result == AutomationDriver.PREPARE_RESULT_CONTINUE) {
                    scheduleDelegateMap.put(schedule.getId(), delegate);
                }
                callback.onFinish(result);
            }
        });
    }

    @RetryingExecutor.Result
    private int resolveDeferred(final @NonNull Schedule<? extends ScheduleData> schedule,
                                final @Nullable TriggerContext triggerContext,
                                final @NonNull AutomationDriver.PrepareScheduleCallback callback) {

        Deferred deferredScheduleData = (Deferred) schedule.coerceType();
        Response<DeferredScheduleClient.Result> response;

        String channelId = airshipChannel.getId();
        if (channelId == null) {
            return RetryingExecutor.RESULT_RETRY;
        }

        try {
            response = deferredScheduleClient.performRequest(deferredScheduleData.getUrl(),
                    channelId, triggerContext, audienceManager.getTagOverrides(),
                    audienceManager.getAttributeOverrides());
        } catch (RequestException e) {
            if (deferredScheduleData.getRetryOnTimeout()) {
                Logger.debug(e, "Failed to resolve deferred schedule, will retry. Schedule: %s", schedule.getId());
                return RetryingExecutor.RESULT_RETRY;
            } else {
                Logger.debug(e, "Failed to resolve deferred schedule. Schedule: %s", schedule.getId());
                callback.onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
                return RetryingExecutor.RESULT_CANCEL;
            }
        } catch (AuthException e) {
            Logger.debug(e, "Failed to resolve deferred schedule: %s", schedule.getId());
            return RetryingExecutor.RESULT_RETRY;
        }

        if (!response.isSuccessful()) {
            Logger.debug("Failed to resolve deferred schedule, will retry. Schedule: %s, Response: %s", schedule.getId(), response);
            return RetryingExecutor.RESULT_RETRY;
        }

        if (!response.getResult().isAudienceMatch()) {
            callback.onFinish(getPrepareResultMissedAudience(schedule));
            return RetryingExecutor.RESULT_CANCEL;
        }

        InAppMessage message = response.getResult().getMessage();
        if (message != null) {
            prepareSchedule(schedule, message, inAppMessageScheduleDelegate, callback);
        } else {
            // Handled by backend, penalize to count towards limit
            callback.onFinish(AutomationDriver.PREPARE_RESULT_PENALIZE);
        }

        return RetryingExecutor.RESULT_FINISHED;
    }

    @MainThread
    @AutomationDriver.ReadyResult
    private int onCheckExecutionReadiness(@NonNull Schedule<? extends ScheduleData> schedule) {
        Logger.verbose("onCheckExecutionReadiness schedule: %s", schedule.getId());

        // Prevent display on pause.
        if (isPaused()) {
            return AutomationDriver.READY_RESULT_NOT_READY;
        }

        if (isScheduleInvalid(schedule)) {
            ScheduleDelegate<?> delegate = scheduleDelegateMap.remove(schedule.getId());
            if (delegate != null) {
                delegate.onExecutionInvalidated(schedule);
            }
            return AutomationDriver.READY_RESULT_INVALIDATE;
        }

        FrequencyChecker frequencyChecker = frequencyCheckerMap.remove(schedule.getId());
        if (frequencyChecker != null && !frequencyChecker.checkAndIncrement()) {
            ScheduleDelegate<?> delegate = scheduleDelegateMap.remove(schedule.getId());
            if (delegate != null) {
                delegate.onExecutionInvalidated(schedule);
            }
            return AutomationDriver.READY_RESULT_SKIP;
        }

        ScheduleDelegate<?> delegate = scheduleDelegateMap.get(schedule.getId());
        return delegate == null ? AutomationDriver.READY_RESULT_NOT_READY : delegate.onCheckExecutionReadiness(schedule);
    }

    @MainThread
    private void onExecuteTriggeredSchedule(@NonNull Schedule<? extends
            ScheduleData> schedule, @NonNull AutomationDriver.ExecutionCallback callback) {
        Logger.verbose("onExecuteTriggeredSchedule schedule: %s", schedule.getId());
        ScheduleDelegate<?> delegate = scheduleDelegateMap.remove(schedule.getId());
        if (delegate != null) {
            delegate.onExecute(schedule, callback);
        } else {
            Logger.error("Unexpected schedule type: %s", schedule.getType());
            callback.onFinish();
        }
    }

    private void onScheduleExecutionInterrupted(Schedule<? extends ScheduleData> schedule) {
        Logger.verbose("onScheduleExecutionInterrupted schedule: %s", schedule.getId());
        ScheduleDelegate<? extends ScheduleData> delegate = delegateForSchedule(schedule);
        if (delegate != null) {
            delegate.onExecutionInterrupted(schedule);
        }
    }

    /**
     * Updates the automation engine pause state with user enable and component enable flags.
     */
    private void updateEnginePauseState() {
        boolean isEnabled = privacyManager.isEnabled(PrivacyManager.FEATURE_IN_APP_AUTOMATION) && isComponentEnabled();
        automationEngine.setPaused(!isEnabled);
    }

    /**
     * Checks to see if a schedule from remote-data is still valid by checking
     * the schedule metadata.
     *
     * @param schedule The in-app schedule.
     * @return {@code true} if the schedule is valid, otherwise {@code false}.
     */
    private boolean isScheduleInvalid(@NonNull Schedule<? extends ScheduleData> schedule) {
        return remoteDataSubscriber.isRemoteSchedule(schedule) && !remoteDataSubscriber.isScheduleValid(schedule);
    }

    /**
     * Gets the Frequency Checker
     *
     * @param schedule The in-app schedule.
     * @return a FrequencyChecker
     */
    @Nullable
    private FrequencyChecker getFrequencyChecker(@NonNull Schedule<? extends ScheduleData> schedule) {
        try {
            return frequencyLimitManager.getFrequencyChecker(schedule.getFrequencyConstraintIds()).get();
        } catch (ExecutionException e) {
            Logger.error("InAppAutomation - Failed to get Frequency Limit Checker : " + e);
        } catch (InterruptedException e) {
            Logger.error("InAppAutomation - Failed to get Frequency Limit Checker : " + e);
        }
        return null;
    }

    @AutomationDriver.PrepareResult
    private int getPrepareResultMissedAudience(@NonNull Schedule<? extends ScheduleData> schedule) {
        @AutomationDriver.PrepareResult int result = AutomationDriver.PREPARE_RESULT_PENALIZE;
        if (schedule.getAudience() != null) {
            switch (schedule.getAudience().getMissBehavior()) {
                case Audience.MISS_BEHAVIOR_CANCEL:
                    result = AutomationDriver.PREPARE_RESULT_CANCEL;
                    break;
                case Audience.MISS_BEHAVIOR_SKIP:
                    result = AutomationDriver.PREPARE_RESULT_SKIP;
                    break;
                case Audience.MISS_BEHAVIOR_PENALIZE:
                    result = AutomationDriver.PREPARE_RESULT_PENALIZE;
                    break;
            }
        }

        return result;
    }

    @Nullable
    private ScheduleDelegate<? extends ScheduleData> delegateForSchedule(Schedule<? extends ScheduleData> schedule) {
        ScheduleDelegate<? extends ScheduleData> delegate = null;

        switch (schedule.getType()) {
            case Schedule.TYPE_ACTION:
                delegate = actionScheduleDelegate;
                break;

            case Schedule.TYPE_DEFERRED:
                Deferred deferred = schedule.coerceType();
                if (Deferred.TYPE_IN_APP_MESSAGE.equals(deferred.getType())) {
                    delegate = inAppMessageScheduleDelegate;
                }
                break;

            case Schedule.TYPE_IN_APP_MESSAGE:
                delegate = inAppMessageScheduleDelegate;
                break;
        }

        return delegate;
    }

    private void ensureStarted() {
        if (!isStarted.getAndSet(true)) {
            Logger.verbose("Starting In-App automation");
            automationEngine.start(driver);
        }
    }

    private void checkUpdatesSubscription() {
        synchronized (remoteDataObserverDelegate) {
            if (privacyManager.isEnabled(PrivacyManager.FEATURE_IN_APP_AUTOMATION)) {
                ensureStarted();
                if (subscription == null) {
                    // New user cut off time
                    if (remoteDataSubscriber.getScheduleNewUserCutOffTime() == -1) {
                        remoteDataSubscriber.setScheduleNewUserCutOffTime(airshipChannel.getId() == null ? System.currentTimeMillis() : 0);
                    }
                    subscription = remoteDataSubscriber.subscribe(backgroundHandler.getLooper(), remoteDataObserverDelegate);
                }
            } else if (subscription != null) {
                subscription.cancel();
                subscription = null;
            }
        }
    }

}
