/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushProvider;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChannelRegistrationPayloadTest extends BaseTestCase {

    private final boolean testOptIn = true;
    private final boolean testBackgroundEnabled = true;
    private final String testDeviceType = "android";
    private final String testPushAddress = "gcmRegistrationId";
    private final String testUserId = "fakeUserId";
    private final String testAccengageDeviceId = "accengage-device-id";
    private final boolean testSetTags = true;
    private Set<String> testTags;
    private final String testLanguage = "test_language";
    private final String testTimezone = "test_timezone";
    private final String testCountry = "test_country";

    private ChannelRegistrationPayload payload;

    @Before
    public void setUp() {
        testTags = new HashSet<>();
        testTags.add("tagOne");
        testTags.add("tagTwo");
    }

    /**
     * Test that minimized payload doesn't include creation-specific data such as the APID and user id.
     */
    @Test
    public void testMinimizedPayloadIgnoresCreationSpecificData() {
        payload = new ChannelRegistrationPayload.Builder()
                .setUserId(testUserId)
                .setAccengageDeviceId(testAccengageDeviceId)
                .build();

        ChannelRegistrationPayload newPayload = new ChannelRegistrationPayload.Builder(payload).build();
        ChannelRegistrationPayload minPayload = newPayload.minimizedPayload(payload);

        assertNull(minPayload.userId);
        assertNull(minPayload.accengageDeviceId);
    }

    /**
     * Test that the minimized payload includes optional fields if changed
     */
    @Test
    public void testMinimizedPayloadIncludesOptionalFieldsIfChanged() throws JsonException {
        payload = new ChannelRegistrationPayload.Builder()
                .setTags(testSetTags, testTags)
                .setLanguage(testLanguage)
                .setTimezone(testTimezone)
                .setCountry(testCountry)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .build();

        ChannelRegistrationPayload newPayload = new ChannelRegistrationPayload.Builder(payload)
                .setLanguage("newLanguage")
                .setTimezone("newTimezone")
                .setCountry("newCountry")
                .setTags(true, new HashSet<>(Arrays.asList("new", "tags")))
                .setLocationSettings(false)
                .setAppVersion("234")
                .setApiVersion(234)
                .setSdkVersion("2.3.4")
                .setDeviceModel("Other device model")
                .setCarrier("Other carrier")
                .build();

        ChannelRegistrationPayload minPayload = newPayload.minimizedPayload(payload);

        JsonMap.Builder builder = JsonMap.newBuilder();
        Set<String> expectedAdd = new HashSet<>(Arrays.asList("new", "tags"));
        Set<String> expectedRemove = new HashSet<>(Arrays.asList("tagOne", "tagTwo"));
        builder.put("add", JsonValue.wrap(expectedAdd));
        builder.put("remove", JsonValue.wrap(expectedRemove));
        JsonMap expectedTagChanges = builder.build();

        assertEquals("newLanguage", minPayload.language);
        assertEquals("newTimezone", minPayload.timezone);
        assertEquals("newCountry", minPayload.country);
        assertTrue(minPayload.setTags);
        assertEquals(new HashSet<>(Arrays.asList("new", "tags")), minPayload.tags);
        assertEquals(expectedTagChanges, minPayload.tagChanges);
        assertEquals(false, minPayload.locationSettings);
        assertEquals("234", minPayload.appVersion);
        assertEquals(234, (Object) minPayload.apiVersion);
        assertEquals("2.3.4", minPayload.sdkVersion);
        assertEquals("Other device model", minPayload.deviceModel);
        assertEquals("Other carrier", minPayload.carrier);
    }

    /**
     * Test that the minimized payload ignores optional fields if unchanged
     */
    @Test
    public void testMinimizedPayloadIgnoresOptionalFieldsIfUnchanged() {
        payload = new ChannelRegistrationPayload.Builder()
                .setTags(testSetTags, testTags)
                .setLanguage(testLanguage)
                .setTimezone(testTimezone)
                .setCountry(testCountry)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .build();

        ChannelRegistrationPayload newPayload = new ChannelRegistrationPayload.Builder(payload)
                .build();

        ChannelRegistrationPayload minPayload = newPayload.minimizedPayload(payload);

        assertNull(minPayload.language);
        assertNull(minPayload.timezone);
        assertNull(minPayload.country);
        assertFalse(minPayload.setTags);
        assertNull(minPayload.tags);
        assertNull(minPayload.tagChanges);
        assertNull(minPayload.locationSettings);
        assertNull(minPayload.appVersion);
        assertNull(minPayload.sdkVersion);
        assertNull(minPayload.apiVersion);
        assertNull(minPayload.deviceModel);
        assertNull(minPayload.carrier);
    }

    /**
     * Test that the minimized payload contains all required fields
     */
    @Test
    public void testMinimizedPayloadContainsRequiredFields() {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setBackgroundEnabled(testBackgroundEnabled)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .build();

        ChannelRegistrationPayload newPayload = new ChannelRegistrationPayload.Builder(payload)
                .setOptIn(!payload.optIn)
                .setBackgroundEnabled(!payload.backgroundEnabled)
                .build();
        ChannelRegistrationPayload minPayload = newPayload.minimizedPayload(payload);

        assertEquals(minPayload.optIn, newPayload.optIn);
        assertEquals(minPayload.backgroundEnabled, newPayload.backgroundEnabled);
        assertEquals(minPayload.deviceType, newPayload.deviceType);
        assertEquals(minPayload.pushAddress, newPayload.pushAddress);
    }

    /**
     * Test that when the last payload is null, the minimized payload is unchanged
     */
    @Test
    public void testMinimizedPayloadWhenLastIsNull() {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setBackgroundEnabled(testBackgroundEnabled)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setAccengageDeviceId(testAccengageDeviceId)
                .setLanguage(testLanguage)
                .setTimezone(testTimezone)
                .setCountry(testCountry)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .build();

        ChannelRegistrationPayload minPayload = payload.minimizedPayload(null);

        assertEquals(payload, minPayload);
    }

    /**
     * Test that the json has the full expected payload when analytics is enabled.
     */
    @Test
    public void testAsJsonFullPayloadAnalyticsEnabled() {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setBackgroundEnabled(testBackgroundEnabled)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setAccengageDeviceId(testAccengageDeviceId)
                .setLanguage(testLanguage)
                .setTimezone(testTimezone)
                .setCountry(testCountry)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .build();

        JsonMap body = payload.toJsonValue().getMap();

        // Top level fields
        assertTrue("Channel should be present in payload.", body.containsKey(ChannelRegistrationPayload.CHANNEL_KEY));
        assertTrue("Identity hints should be present in payload.", body.containsKey(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));

        JsonMap identityHints = body.opt(ChannelRegistrationPayload.IDENTITY_HINTS_KEY).getMap();
        JsonMap channel = body.opt(ChannelRegistrationPayload.CHANNEL_KEY).getMap();

        // Identity items
        assertTrue("User ID should be present in payload.", identityHints.containsKey(ChannelRegistrationPayload.USER_ID_KEY));
        assertEquals("User ID should be fakeUserId.", identityHints.get(ChannelRegistrationPayload.USER_ID_KEY).getString(), testUserId);

        assertTrue("Accengage Device ID should be present in payload.", identityHints.containsKey(ChannelRegistrationPayload.ACCENGAGE_DEVICE_ID));
        assertEquals("Accengage Device ID should match.", identityHints.get(ChannelRegistrationPayload.ACCENGAGE_DEVICE_ID).getString(), testAccengageDeviceId);

        // Channel specific items
        assertTrue("Device type should be present in payload.", channel.containsKey(ChannelRegistrationPayload.DEVICE_TYPE_KEY));
        assertEquals("Device type should be android.", channel.get(ChannelRegistrationPayload.DEVICE_TYPE_KEY).getString(), testDeviceType);
        assertTrue("Opt in should be present in payload.", channel.containsKey(ChannelRegistrationPayload.OPT_IN_KEY));
        assertEquals("Opt in should be true.", channel.get(ChannelRegistrationPayload.OPT_IN_KEY).getBoolean(!testOptIn), testOptIn);
        assertTrue("Background flag should be present in payload.", channel.containsKey(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY));
        assertEquals("Background flag should be true.", channel.get(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY).getBoolean(!testBackgroundEnabled), testBackgroundEnabled);
        assertTrue("Push address should be present in payload.", channel.containsKey(ChannelRegistrationPayload.PUSH_ADDRESS_KEY));
        assertEquals("Push address should be gcmRegistrationId.", channel.get(ChannelRegistrationPayload.PUSH_ADDRESS_KEY).getString(), testPushAddress);
        assertTrue("Set tags should be present in payload", channel.containsKey(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be true.", channel.get(ChannelRegistrationPayload.SET_TAGS_KEY).getBoolean(!testSetTags), testSetTags);
        assertTrue("Tags should be present in payload", channel.containsKey(ChannelRegistrationPayload.TAGS_KEY));

        assertTrue("Timezone should be in payload", channel.containsKey(ChannelRegistrationPayload.TIMEZONE_KEY));
        assertTrue("Language should be in payload", channel.containsKey(ChannelRegistrationPayload.LANGUAGE_KEY));
        assertTrue("Country should be in payload", channel.containsKey(ChannelRegistrationPayload.COUNTRY_KEY));

        // Check the tags within channel item
        JsonList tags = channel.get(ChannelRegistrationPayload.TAGS_KEY).getList();
        assertEquals("Tags size should be 2.", tags.size(), testTags.size());
        assertTrue("Tags should contain tagOne.", testTags.contains(tags.get(0).getString()));
        assertTrue("Tags should contain tagTwo.", testTags.contains(tags.get(1).getString()));

        assertTrue(channel.containsKey(ChannelRegistrationPayload.LOCATION_SETTINGS_KEY));
        assertEquals(channel.get(ChannelRegistrationPayload.LOCATION_SETTINGS_KEY).getBoolean(false), true);

        assertTrue(channel.containsKey(ChannelRegistrationPayload.APP_VERSION_KEY));
        assertEquals(channel.get(ChannelRegistrationPayload.APP_VERSION_KEY).getString(), "123");

        assertTrue(channel.containsKey(ChannelRegistrationPayload.API_VERSION_KEY));
        assertEquals(channel.get(ChannelRegistrationPayload.API_VERSION_KEY).getInt(234), 123);

        assertTrue(channel.containsKey(ChannelRegistrationPayload.SDK_VERSION_KEY));
        assertEquals(channel.get(ChannelRegistrationPayload.SDK_VERSION_KEY).getString(), "1.2.3");

        assertTrue(channel.containsKey(ChannelRegistrationPayload.DEVICE_MODEL_KEY));
        assertEquals(channel.get(ChannelRegistrationPayload.DEVICE_MODEL_KEY).getString(), "Device model");

        assertTrue(channel.containsKey(ChannelRegistrationPayload.CARRIER_KEY));
        assertEquals(channel.get(ChannelRegistrationPayload.CARRIER_KEY).getString(), "Carrier");
    }

    /**
     * Test when tags are empty.
     */
    @Test
    public void testAsJsonEmptyTags() {
        // Create payload with empty tags
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().setTags(testSetTags, new HashSet<String>()).build();
        JsonMap channel = payload.toJsonValue().getMap().opt(ChannelRegistrationPayload.CHANNEL_KEY).getMap();

        // Verify setTags is true in order for tags to be present in payload
        assertTrue("Set tags should be present in payload.", channel.containsKey(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be true.", channel.get(ChannelRegistrationPayload.SET_TAGS_KEY).getBoolean(!testSetTags), testSetTags);

        // Verify tags are present, but empty
        assertTrue("Tags should be present in payload.", channel.containsKey(ChannelRegistrationPayload.TAGS_KEY));

        JsonList tags = channel.opt(ChannelRegistrationPayload.TAGS_KEY).getList();
        Assert.assertTrue("Tags size should be 0.", tags.isEmpty());
    }

    /**
     * Test that tags are not sent when setTags is false.
     */
    @Test
    public void testAsJsonNoTags() {
        // Create payload with setTags is false
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().setTags(false, testTags).build();
        JsonMap channel = payload.toJsonValue().getMap().opt(ChannelRegistrationPayload.CHANNEL_KEY).getMap();

        // Verify setTags is present and is false
        assertTrue("Set tags should be present in payload.", channel.containsKey(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be false.", channel.opt(ChannelRegistrationPayload.SET_TAGS_KEY).getBoolean(true), false);

        // Verify tags are not present
        assertFalse("Tags should not be present in payload.", channel.containsKey(ChannelRegistrationPayload.TAGS_KEY));
    }

    /**
     * Test that an empty identity hints section is not included.
     */
    @Test
    public void testAsJsonEmptyIdentityHints() {
        // Create empty payload
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
        JsonMap body = payload.toJsonValue().getMap();

        // Verify the identity hints section is not included
        assertFalse("Identity hints should not be present in payload.", body.containsKey(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));
    }

    /**
     * Test that an empty user ID is not included in the identity hints.
     */
    @Test
    public void testAsJsonEmptyUserId() {
        // Create payload with empty userId
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().setUserId("").build();
        JsonMap body = payload.toJsonValue().getMap();

        // Verify the identity hints section is not included
        assertFalse("Identity hints should not be present in payload.", body.containsKey(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));
    }

    /**
     * Test an empty builder.
     */
    @Test
    public void testEmptyBuilder() {
        // Create an empty payload
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
        JsonMap body = payload.toJsonValue().getMap();

        // Top level fields
        assertTrue("Channel should be present in payload.", body.containsKey(ChannelRegistrationPayload.CHANNEL_KEY));
        assertFalse("Identity hints should not be present in payload.", body.containsKey(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));

        // Channel specific items
        JsonMap channel = body.get(ChannelRegistrationPayload.CHANNEL_KEY).getMap();
        assertTrue("Opt in should be present in payload.", channel.containsKey(ChannelRegistrationPayload.OPT_IN_KEY));
        assertEquals("Opt in should be false.", channel.get(ChannelRegistrationPayload.OPT_IN_KEY).getBoolean(true), false);
        assertTrue("Background flag should be present in payload.", channel.containsKey(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY));
        assertEquals("Background flag should be false.", channel.get(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY).getBoolean(true), false);
        assertFalse("Push address should not be present in payload.", channel.containsKey(ChannelRegistrationPayload.PUSH_ADDRESS_KEY));
        assertTrue("Set tags should be present in payload.", channel.containsKey(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be false.", channel.get(ChannelRegistrationPayload.SET_TAGS_KEY).getBoolean(true), false);
        assertFalse("Tags should not be present in payload.", channel.containsKey(ChannelRegistrationPayload.TAGS_KEY));
    }

    /**
     * Test when payload is equal to itself
     */
    @Test
    public void testPayloadEqualToItself() {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .build();

        assertTrue("Payload should be equal to itself.", payload.equals(payload));
    }

    /**
     * Test when payloads are the same
     */
    @Test
    public void testPayloadsEqual() {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setBackgroundEnabled(testBackgroundEnabled)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .build();

        ChannelRegistrationPayload payload2 = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setBackgroundEnabled(testBackgroundEnabled)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .build();

        assertTrue("Payloads should match.", payload.equals(payload2));
        assertEquals("The hashCode for the payloads should match.", payload.hashCode(), payload2.hashCode());
    }

    /**
     * Test when payloads are not equal
     */
    @Test
    public void testPayloadsNotEqual() {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setBackgroundEnabled(testBackgroundEnabled)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .build();

        ChannelRegistrationPayload emptyPayload = new ChannelRegistrationPayload.Builder()
                .setOptIn(false)
                .setDeviceType(testDeviceType)
                .setPushAddress(null)
                .setTags(false, null)
                .setUserId(null)
                .setLocationSettings(false)
                .setAppVersion("234")
                .setApiVersion(234)
                .setSdkVersion("2.3.4")
                .setDeviceModel("Other device model")
                .setCarrier("Other carrier")
                .setBackgroundEnabled(!testBackgroundEnabled)
                .build();

        assertFalse("Payloads should not match.", payload.equals(emptyPayload));
        assertNotSame("The hashCode for the payloads should not match.", payload.hashCode(), emptyPayload.hashCode());
    }

    /**
     * Test empty payloads are equal
     */
    @Test
    public void testEmptyPayloadsEqual() {
        ChannelRegistrationPayload payload1 = new ChannelRegistrationPayload.Builder().build();
        ChannelRegistrationPayload payload2 = new ChannelRegistrationPayload.Builder().build();
        assertTrue("Payloads should match.", payload1.equals(payload2));
        assertEquals("The hashCode for the payloads should match.", payload1.hashCode(), payload2.hashCode());
    }

    /**
     * Test payload created from JSON
     */
    @Test
    public void testCreateFromJSON() throws JsonException {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setAccengageDeviceId(testAccengageDeviceId)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .build();

        ChannelRegistrationPayload jsonPayload = ChannelRegistrationPayload.fromJson(payload.toJsonValue());
        assertTrue("Payloads should match.", payload.equals(jsonPayload));
        assertEquals("Payloads should match.", payload.hashCode(), jsonPayload.hashCode());

    }

    /**
     * Test payload created from empty JSON
     */
    @Test(expected = JsonException.class)
    public void testCreateFromEmptyJSON() throws JsonException {
        ChannelRegistrationPayload.fromJson(JsonValue.NULL);
    }

    @Test
    public void testFromJsonNoTags() throws JsonException {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setUserId(testUserId)
                .setAccengageDeviceId(testAccengageDeviceId)
                .build();

        ChannelRegistrationPayload jsonPayload = ChannelRegistrationPayload.fromJson(payload.toJsonValue());
        assertTrue("Payloads should match.", payload.equals(jsonPayload));
        assertEquals("Payloads should match.", payload.hashCode(), jsonPayload.hashCode());
    }

    @Test
    public void testFromJsonEmptyAlias() throws JsonException {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setUserId(testUserId)
                .setAccengageDeviceId(testAccengageDeviceId)
                .build();

        ChannelRegistrationPayload jsonPayload = ChannelRegistrationPayload.fromJson(payload.toJsonValue());
        assertTrue("Payloads should match.", payload.equals(jsonPayload));
        assertEquals("Payloads should match.", payload.hashCode(), jsonPayload.hashCode());
    }

    /**
     * Test payload created from JSON with Tag Changes
     */
    @Test
    public void testCreateFromJSONWithTagChanges() throws JsonException {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setAccengageDeviceId(testAccengageDeviceId)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .build();

        ChannelRegistrationPayload newPayload = new ChannelRegistrationPayload.Builder(payload)
                .setLanguage("newLanguage")
                .setTimezone("newTimezone")
                .setCountry("newCountry")
                .setTags(true, new HashSet<>(Arrays.asList("new", "tags")))
                .setLocationSettings(false)
                .setAppVersion("234")
                .setApiVersion(234)
                .setSdkVersion("2.3.4")
                .setDeviceModel("Other device model")
                .setCarrier("Other carrier")
                .build();

        ChannelRegistrationPayload minPayload = newPayload.minimizedPayload(payload);

        ChannelRegistrationPayload jsonPayload = ChannelRegistrationPayload.fromJson(minPayload.toJsonValue());
        assertTrue("Payloads should match.", minPayload.equals(jsonPayload));
        assertEquals("Payloads should match.", minPayload.hashCode(), jsonPayload.hashCode());

    }

    @Test
    public void testDeliveryTypeAndroid() {
        payload = new ChannelRegistrationPayload.Builder()
                .setDeviceType(ChannelRegistrationPayload.ANDROID_DEVICE_TYPE)
                .setDeliveryType(PushProvider.HMS_DELIVERY_TYPE)
                .build();

        JsonValue expected = JsonMap.newBuilder()
                                    .put("channel", JsonMap.newBuilder()
                                                           .put("set_tags", false)
                                                           .put("device_type", "android")
                                                           .put("opt_in", false)
                                                           .put("background", false)
                                                           .put("is_activity", false)
                                                           .put("android", JsonMap.newBuilder()
                                                                                  .put("delivery_type", "hms")
                                                                                  .build())
                                                           .build())
                                    .build()
                                    .toJsonValue();

        assertEquals(expected, payload.toJsonValue());
    }

    @Test
    public void testDeliveryTypeAmazon()  {
        payload = new ChannelRegistrationPayload.Builder()
                .setDeviceType(ChannelRegistrationPayload.AMAZON_DEVICE_TYPE)
                .setDeliveryType(PushProvider.ADM_DELIVERY_TYPE)
                .build();

        JsonValue expected = JsonMap.newBuilder()
                                    .put("channel", JsonMap.newBuilder()
                                                           .put("set_tags", false)
                                                           .put("device_type", "amazon")
                                                           .put("opt_in", false)
                                                           .put("background", false)
                                                           .put("is_activity", false)
                                                           .build())
                                    .build()
                                    .toJsonValue();

        assertEquals(expected, payload.toJsonValue());
    }

    /**
     * Test that the minimized payload includes all attribute fields if named user changes.
     */
    @Test
    public void testMinimizedPayloadNamedUserChanges() {
        payload = new ChannelRegistrationPayload.Builder()
                .setTags(true, testTags)
                .setLanguage(testLanguage)
                .setTimezone(testTimezone)
                .setCountry(testCountry)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .setContactId("contact id")
                .setAccengageDeviceId("accengage ID")
                .build();

        ChannelRegistrationPayload newPayload = new ChannelRegistrationPayload.Builder(payload)
                .setContactId("different contact id")
                .build();

        ChannelRegistrationPayload minPayload = newPayload.minimizedPayload(payload);

        ChannelRegistrationPayload expected =  new ChannelRegistrationPayload.Builder(payload)
                .setTags(false, null)
                .setAccengageDeviceId(null)
                .setContactId("different contact id")
                .build();

        assertEquals(expected, minPayload);
    }

    /**
     * Test that the minimized payload removes attribute fields if named user changes but is null.
     */
    @Test
    public void testMinimizePayloadIfNamedUserIsNull() {
        payload = new ChannelRegistrationPayload.Builder()
                .setTags(true, testTags)
                .setLanguage(testLanguage)
                .setTimezone(testTimezone)
                .setCountry(testCountry)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .setCarrier("Carrier")
                .setContactId("contact id")
                .setUserId("some-user")
                .build();

        ChannelRegistrationPayload newPayload = new ChannelRegistrationPayload.Builder(payload)
                .setContactId(null)
                .build();

        ChannelRegistrationPayload minPayload = newPayload.minimizedPayload(payload);

        ChannelRegistrationPayload expected =  new ChannelRegistrationPayload.Builder(payload)
                .setTags(false, null)
                .setAccengageDeviceId(null)
                .setContactId(null)
                .setDeviceType(null)
                .setLanguage(null)
                .setTimezone(null)
                .setCountry(null)
                .setLocationSettings(null)
                .setAppVersion(null)
                .setApiVersion(null)
                .setSdkVersion(null)
                .setDeviceModel(null)
                .setCarrier(null)
                .setUserId(null)
                .build();

        assertEquals(expected, minPayload);
    }


    @Test
    public void testEqualsIgnoreActive() {
        ChannelRegistrationPayload active = new ChannelRegistrationPayload.Builder()
                .setIsActive(true)
                .build();

        ChannelRegistrationPayload inActive = new ChannelRegistrationPayload.Builder()
                .setIsActive(false)
                .build();

        assertNotEquals(active, inActive);
        assertTrue(active.equals(inActive, false));
        assertFalse(active.equals(inActive, true));

    }
}
