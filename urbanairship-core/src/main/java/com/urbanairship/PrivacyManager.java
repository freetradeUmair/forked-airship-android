/* Copyright Airship and Contributors */

package com.urbanairship;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * The privacy manager allows enabling/disabling features in the SDK that require user data.
 * The SDK will not make any network requests or collect data if all features our disabled, with
 * a few exceptions when going from enabled -> disabled. To have the SDK opt-out of all features on startup,
 * set the default enabled features in the AirshipConfig to {@link #FEATURE_NONE}, or in the
 * airshipconfig.properties file with `enabledFeatures = none`.
 *
 * Some features might offer additional opt-in settings directly on the module. For instance, enabling
 * {@link #FEATURE_PUSH} will only enable push message delivery, however you still need to opt-in to
 * {@link com.urbanairship.push.PushManager#setUserNotificationsEnabled(boolean)} before notifications
 * will be allowed.
 *
 * If any feature is enabled, the SDK will collect and send the following data:
 * - Channel ID
 * - Locale
 * - TimeZone
 * - Platform
 * - Opt in state (push and notifications)
 * - SDK version
 * - Push provider (HMS, FCM, ADM)
 * - Manufacturer (if Huawei)
 * - Accengage Device ID (when using the Accengage module for migration)
 */
public class PrivacyManager {

    /**
     * Privacy Manager listener.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface Listener {

        /**
         * Called when the set of enabled features changes.
         */
        void onEnabledFeaturesChanged();

    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = { FEATURE_NONE, FEATURE_IN_APP_AUTOMATION, FEATURE_MESSAGE_CENTER, FEATURE_PUSH,
                    FEATURE_CHAT, FEATURE_ANALYTICS, FEATURE_TAGS_AND_ATTRIBUTES, FEATURE_CONTACTS, FEATURE_ALL })
    public @interface Feature {}

    /**
     * Enables In-App Automation.
     *
     * In addition to the default data collection, In-App Automation will collect:
     * - App Version (App update triggers)
     *
     * {@link #FEATURE_ANALYTICS} is required for event and screen view triggers.
     */
    public final static int FEATURE_IN_APP_AUTOMATION = 1;

    /**
     * Enables Message Center.
     *
     * In addition to the default data collection, Message Center will collect:
     * - Message Center User
     * - Message Reads & Deletes
     */
    public final static int FEATURE_MESSAGE_CENTER = 1 << 1;

    /**
     * Enables push.
     *
     * User notification still must be enabled using {@link com.urbanairship.push.PushManager#setUserNotificationsEnabled(boolean)}.
     *
     * In addition to the default data collection, push will collect:
     * - Push tokens
     */
    public final static int FEATURE_PUSH = 1 << 2;

    /**
     * Enables Airship Chat.
     *
     * In addition to the default data collection, Airship Chat will collect:
     * - User messages
     */
    public final static int FEATURE_CHAT = 1 << 3;

    /**
     * Enables analytics.
     *
     * In addition to the default data collection, analytics will collect:
     * - Events
     * - Associated Identifiers
     * - Registered Notification Types
     * - Time in app
     * - App Version
     * - Device model
     * - Device manufacturer
     * - OS version
     * - Carrier
     * - Connection type
     * - Framework usage
     * - Location (Allows collecting location using the deprecated Airship Location module. Location still needs to be enabled)
     * - Location Permissions (With deprecated Airship Location module)
     */
    public final static int FEATURE_ANALYTICS = 1 << 4;

    /**
     * Enables tags and attributes.
     *
     * In addition to the default data collection, tags and attributes will collect:
     * - Channel and Contact Tags
     * - Channel and Contact Attributes
     */
    public final static int FEATURE_TAGS_AND_ATTRIBUTES = 1 << 5;

    /**
     * Enables contacts.
     *
     * In addition to the default data collection, contacts will collect:
     * - External ids (named user)
     */
    public final static int FEATURE_CONTACTS = 1 << 6;

    /**
     * Helper flag that can be used to set enabled features to none.
     */
    public final static int FEATURE_NONE = 0;

    /**
     * Helper flag that is all features.
     */
    public final static int FEATURE_ALL = FEATURE_ANALYTICS | FEATURE_MESSAGE_CENTER | FEATURE_PUSH
            | FEATURE_CHAT | FEATURE_ANALYTICS | FEATURE_TAGS_AND_ATTRIBUTES | FEATURE_CONTACTS;

    private final String ENABLED_FEATURES_KEY = "com.urbanairship.PrivacyManager.enabledFeatures";

    // legacy keys for migration
    private static final String DATA_COLLECTION_ENABLED_KEY = "com.urbanairship.DATA_COLLECTION_ENABLED";
    private static final String ANALYTICS_ENABLED_KEY = "com.urbanairship.analytics.ANALYTICS_ENABLED";
    private static final String PUSH_TOKEN_REGISTRATION_ENABLED_KEY = "com.urbanairship.push.PUSH_TOKEN_REGISTRATION_ENABLED";
    private static final String PUSH_ENABLED_KEY = "com.urbanairship.push.PUSH_ENABLED";
    private static final String CHAT_ENABLED_KEY = "com.urbanairship.chat.CHAT";

    private final Object lock = new Object();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private final PreferenceDataStore dataStore;
    private final int defaultEnabledFeatures;

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    PrivacyManager(@NonNull PreferenceDataStore dataStore, @Feature int defaultEnabledFeatures) {
        this.dataStore = dataStore;
        this.defaultEnabledFeatures = defaultEnabledFeatures;
    }

    /**
     * Sets the current enabled features.
     *
     * @param features The features to set as enabled.
     */
    public void setEnabledFeatures(@Feature int... features) {
        synchronized (lock) {
            dataStore.put(ENABLED_FEATURES_KEY, combine(features));
            notifyListeners();
        }
    }

    /**
     * Gets the current enabled features.
     *
     * @return The enabled features.
     */
    @Feature
    public int getEnabledFeatures() {
        return dataStore.getInt(ENABLED_FEATURES_KEY, defaultEnabledFeatures);
    }

    /**
     * Enables features.
     *
     * @param features The features to enable.
     */
    public void enable(@Feature int... features) {
        synchronized (lock) {
            int updated = getEnabledFeatures() | combine(features);
            dataStore.put(ENABLED_FEATURES_KEY, updated);
            notifyListeners();
        }
    }

    /**
     * Disables features.
     *
     * @param features The features to disable.
     */
    public void disable(@Feature int... features) {
        synchronized (lock) {
            int updated = getEnabledFeatures() & ~combine(features);
            dataStore.put(ENABLED_FEATURES_KEY, updated);
            notifyListeners();
        }
    }

    /**
     * Checks if a given feature is enabled.
     *
     * @param features The features to check.
     * @return {@code true} if the provided features are enabled, otherwise {@code false}.
     */
    public boolean isEnabled(@Feature int... features) {
        int enabledFeatures = getEnabledFeatures();

        int combined = combine(features);
        if (combined == FEATURE_NONE) {
            return enabledFeatures == FEATURE_NONE;
        } else {
            return (enabledFeatures & combined) == combined;
        }
    }

    public boolean isAnyEnabled(@Feature int... features) {
        int enabledFeatures = getEnabledFeatures();

        for (int feature : features) {
            if (feature == FEATURE_NONE) {
                return enabledFeatures == FEATURE_NONE;
            } else {
                return (enabledFeatures & feature) == feature;
            }
        }

        return false;
    }

    /**
     * Checks if any feature is enabled.
     *
     * @return {@code true} if any feature is enabled, otherwise {@code false}.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public boolean isAnyFeatureEnabled() {
        return getEnabledFeatures() != FEATURE_NONE;
    }

    /**
     * Adds a listener.
     *
     * @param listener The listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener.
     *
     * @param listener The listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    void migrateData() {
        if (this.dataStore.isSet(DATA_COLLECTION_ENABLED_KEY)) {
            if (this.dataStore.getBoolean(DATA_COLLECTION_ENABLED_KEY, false)) {
                this.setEnabledFeatures(PrivacyManager.FEATURE_ALL);
            } else {
                this.setEnabledFeatures(PrivacyManager.FEATURE_NONE);
            }
            this.dataStore.remove(DATA_COLLECTION_ENABLED_KEY);
        }

        if (!this.dataStore.getBoolean(ANALYTICS_ENABLED_KEY, true)) {
            this.disable(FEATURE_ANALYTICS);
            this.dataStore.remove(ANALYTICS_ENABLED_KEY);
        }

        if (!this.dataStore.getBoolean(PUSH_TOKEN_REGISTRATION_ENABLED_KEY, true)) {
            this.disable(FEATURE_PUSH);
            this.dataStore.remove(PUSH_TOKEN_REGISTRATION_ENABLED_KEY);
        }

        if (!this.dataStore.getBoolean(PUSH_ENABLED_KEY, true)) {
            this.disable(FEATURE_PUSH);
            this.dataStore.remove(PUSH_ENABLED_KEY);
        }

        if (!this.dataStore.getBoolean(CHAT_ENABLED_KEY, true)) {
            this.disable(FEATURE_CHAT);
            this.dataStore.remove(CHAT_ENABLED_KEY);
        }
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            listener.onEnabledFeaturesChanged();
        }
    }

    static int combine(@Feature int... features) {
        int result = FEATURE_NONE;
        if (features != null) {
            for (int feature : features) {
                result |= feature;
            }
        }
        return result;
    }

}
