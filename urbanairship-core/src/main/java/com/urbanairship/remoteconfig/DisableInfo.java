/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.remoteconfig;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.json.JsonSerializable;
import com.urbanairship.util.VersionUtils;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.IvyVersionMatcher;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Disable remote config info.
 *
 * @hide
 */
class DisableInfo implements JsonSerializable {

    /**
     * Push module.
     */
    public final static String PUSH_MODULE = "push";

    /**
     * Analytics module.
     */
    public final static String ANALYTICS_MODULE = "analytics";

    /**
     * Message center module.
     */
    public final static String MESSAGE_CENTER = "message_center";

    /**
     * In-app module.
     */
    public final static String IN_APP_MODULE = "in_app_v2";

    /**
     * Automation module.
     */
    public final static String AUTOMATION_MODULE = "automation";

    /**
     * Named user module.
     */
    public final static String NAMED_USER_MODULE = "named_user";

    /**
     * Location module
     */
    public final static String LOCATION_MODULE = "location";

    final static List<String> ALL_MODULES = Arrays.asList(PUSH_MODULE, ANALYTICS_MODULE,
            MESSAGE_CENTER, IN_APP_MODULE, AUTOMATION_MODULE, NAMED_USER_MODULE, LOCATION_MODULE);

    // JSON keys
    private static final String MODULES_KEY = "modules";
    private static final String SDK_VERSIONS_KEY = "sdk_versions";
    private static final String APP_VERSIONS_KEY = "app_versions";

    private static final String REMOTE_DATA_REFRESH_INTERVAL_KEY = "remote_data_refresh_interval";
    private static final String MODULES_ALL_KEY = "all";

    private final Set<String> disabledModules;
    private final long remoteDataInterval;
    private final Set<String> sdkVersionConstraints;

    private JsonPredicate appVersionPredicate;

    /***
     * Default constructor.
     * @param builder The builder.
     */
    private DisableInfo(Builder builder) {
        this.disabledModules = builder.disabledModules;
        this.remoteDataInterval = builder.remoteDataInterval;
        this.sdkVersionConstraints = builder.sdkVersionConstraints;
        this.appVersionPredicate = builder.appVersionPredicate;
    }

    /**
     * Returns a list of all the matching disable info.
     *
     * @param disableInfos The collection of disable infos.
     * @param sdkVersion The sdk version to filter disable infos.
     * @param appVersion The app version.
     *
     * @return A list of matching disable infos.
     */
    @NonNull
    public static List<DisableInfo> filter(@NonNull Collection<DisableInfo> disableInfos, @NonNull String sdkVersion, int appVersion) {
        JsonSerializable versionObject = VersionUtils.createVersionObject(appVersion);
        List<DisableInfo> filtered = new ArrayList<>();

        for (DisableInfo info : disableInfos) {
            if (info.sdkVersionConstraints != null) {
                boolean isSdkVersionMatch = false;

                for (String constraint : info.sdkVersionConstraints) {
                    if (IvyVersionMatcher.newMatcher(constraint).apply(sdkVersion)) {
                        isSdkVersionMatch = true;
                    }
                    break;
                }

                if (!isSdkVersionMatch) {
                    continue;
                }
            }

            if (info.appVersionPredicate != null && !info.appVersionPredicate.apply(versionObject)) {
                continue;
            }

            filtered.add(info);
        }

        return filtered;
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(MODULES_KEY, disabledModules)
                      .putOpt(REMOTE_DATA_REFRESH_INTERVAL_KEY, remoteDataInterval)
                      .putOpt(SDK_VERSIONS_KEY, sdkVersionConstraints)
                      .putOpt(APP_VERSIONS_KEY, appVersionPredicate)
                      .build()
                      .toJsonValue();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DisableInfo info = (DisableInfo) o;

        if (remoteDataInterval != info.remoteDataInterval) {
            return false;
        }
        if (!disabledModules.equals(info.disabledModules)) {
            return false;
        }
        if (sdkVersionConstraints != null ? !sdkVersionConstraints.equals(info.sdkVersionConstraints) : info.sdkVersionConstraints != null) {
            return false;
        }
        return appVersionPredicate != null ? appVersionPredicate.equals(info.appVersionPredicate) : info.appVersionPredicate == null;
    }

    /**
     * Parses a disable info from the json value.
     *
     * Note: This method has the side effect of multiplying the refresh interval by a factor of 1000.
     *
     * @param jsonValue The json value.
     * @return The disable info.
     * @throws JsonException If the json value is invalid.
     */
    public static DisableInfo parseJson(@NonNull JsonValue jsonValue) throws JsonException {
        JsonMap jsonMap = jsonValue.optMap();


        Builder builder = newBuilder();


        if (jsonMap.containsKey(MODULES_KEY)) {
            Collection<String> modules = new HashSet<>();

            if (MODULES_ALL_KEY.equals(jsonMap.opt(MODULES_KEY).getString())) {
                modules.addAll(ALL_MODULES);
            } else {
                JsonList modulesList = jsonMap.opt(MODULES_KEY).getList();
                if (modulesList == null) {
                    throw new JsonException("Modules must be an array of strings: " + jsonMap.opt(MODULES_KEY));
                }

                for (JsonValue moduleValue : modulesList) {
                    if (!moduleValue.isString()) {
                        throw new JsonException("Modules must be an array of strings: " + jsonMap.opt(MODULES_KEY));
                    }

                    String module = moduleValue.getString();

                    // Avoid throwing an exception in this case so the SDK does not break
                    // when we introduce new modules in the future
                    if (ALL_MODULES.contains(module)) {
                        modules.add(moduleValue.getString());
                    }
                }
            }
            builder.setDisabledModules(modules);
        }

        if (jsonMap.containsKey(REMOTE_DATA_REFRESH_INTERVAL_KEY)) {
            if (!jsonMap.get(REMOTE_DATA_REFRESH_INTERVAL_KEY).isNumber()) {
                throw new IllegalArgumentException("Remote data refresh interval must be a number: " + jsonMap.get(REMOTE_DATA_REFRESH_INTERVAL_KEY));
            }

            long remoteDataInterval = TimeUnit.SECONDS.toMillis(jsonMap.get(REMOTE_DATA_REFRESH_INTERVAL_KEY).getLong(0));
            builder.setRemoteDataInterval(remoteDataInterval);
        }


        if (jsonMap.containsKey(SDK_VERSIONS_KEY)) {
            Collection<String> sdkVersionConstraints = new HashSet<>();

            JsonList constraintList = jsonMap.opt(SDK_VERSIONS_KEY).getList();
            if (constraintList == null) {
                throw new JsonException("SDK Versions must be an array of strings: " + jsonMap.opt(SDK_VERSIONS_KEY));
            }

            for (JsonValue constraintValue : constraintList) {
                if (!constraintValue.isString()) {
                    throw new JsonException("SDK Versions must be an array of strings: " + jsonMap.opt(SDK_VERSIONS_KEY));
                }

                sdkVersionConstraints.add(constraintValue.getString());
            }

            builder.setSDKVersionConstraints(sdkVersionConstraints);
        }

        if (jsonMap.containsKey(APP_VERSIONS_KEY)) {
            builder.setAppVersionPredicate(JsonPredicate.parse(jsonMap.get(APP_VERSIONS_KEY)));
        }

        return builder.build();
    }

    /**
     * Returns the set SDK version constraints.
     *
     * @return Set of SDK version constraints.
     */
    @Nullable
    public Set<String> getSdkVersionConstraints() {
        return sdkVersionConstraints;
    }

    /**
     * Returns the JSON predicate to be used to match the app's version int.
     *
     * @return predicate Json predicate to match the app version object.
     */
    @Nullable
    public JsonPredicate getAppVersionPredicate() {
        return appVersionPredicate;
    }

    /**
     * Returns the set of disabled modules.
     *
     * @return Set of disabled modules.
     */
    @NonNull
    public Set<String> getDisabledModules() {
        return disabledModules;
    }

    /**
     * Returns the remote data refresh interval.
     *
     * @return The remote data refresh interval in milliseconds.
     */
    public long getRemoteDataRefreshInterval() {
        return remoteDataInterval;
    }

    /**
     * Builder factory method.
     *
     * @return A new builder instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Disable info builder.
     */
    public static class Builder {

        private Set<String> disabledModules = new HashSet<>();
        private long remoteDataInterval;
        private Set<String> sdkVersionConstraints;
        private JsonPredicate appVersionPredicate;

        private Builder() { }

        /**
         * Collection of modules to be disabled.
         *
         * @param disabledModules The modules to be disabled.
         * @return The builder.
         */
        public Builder setDisabledModules(@NonNull Collection<String> disabledModules) {
            this.disabledModules.addAll(disabledModules);
            return this;
        }

        /**
         * Remote data interval.
         *
         * Note: The remote data interval is converted from seconds to milliseconds when parsed.
         *
         * @param remoteDataInterval The remote data interval in milliseconds.
         * @return The builder.
         */
        public Builder setRemoteDataInterval(long remoteDataInterval) {
            this.remoteDataInterval = remoteDataInterval;
            return this;
        }

        /**
         * Collection of SDK constraints to be applied.
         *
         * @param sdkVersionConstraints The SDK version constraints to be applied.
         * @return The builder.
         */
        public Builder setSDKVersionConstraints(@NonNull Collection<String> sdkVersionConstraints) {
            this.sdkVersionConstraints = new HashSet<>(sdkVersionConstraints);
            return this;
        }

        /**
         * JSON predicate to be used to match the app's version int.
         *
         * @param predicate JSON predicate to match the app version object.
         * @return The builder.
         */
        public Builder setAppVersionPredicate(JsonPredicate predicate) {
            this.appVersionPredicate = predicate;
            return this;
        }


        /**
         * Builds the DisableInfo.
         *
         * @return The DisableInfo.
         */
        public DisableInfo build() {
            return new DisableInfo(this);
        }
    }
}
