/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.MessageCenterDataManager;
import com.urbanairship.UrbanAirshipProvider;
import com.urbanairship.UrbanAirshipResolver;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Rich Push specific database operations.
 */
class MessageCenterResolver extends UrbanAirshipResolver {

    private static final String WHERE_CLAUSE_CHANGED = MessageCenterDataManager.MessageTable.COLUMN_NAME_UNREAD +
            " <> " + MessageCenterDataManager.MessageTable.COLUMN_NAME_UNREAD_ORIG;
    private static final String WHERE_CLAUSE_READ = MessageCenterDataManager.MessageTable.COLUMN_NAME_UNREAD + " = ?";
    private static final String WHERE_CLAUSE_MESSAGE_ID = MessageCenterDataManager.MessageTable.COLUMN_NAME_MESSAGE_ID + " = ?";
    private static final String FALSE_VALUE = "0";
    private static final String TRUE_VALUE = "1";
    private final Uri uri;

    /**
     * Default constructor.
     *
     * @param context The application context.
     */
    MessageCenterResolver(Context context) {
        super(context);
        this.uri = UrbanAirshipProvider.getRichPushContentUri(context);
    }

    /**
     * Gets all the {@link Message} instances from the database.
     *
     * @return A collection of {@link Message}.
     */
    @NonNull
    Collection<Message> getMessages() {
        Cursor cursor = this.query(this.uri, null, null, null, null);
        return getMessagesFromCursor(cursor);
    }

    /**
     * Gets all the {@link Message} IDs in the database.
     *
     * @return A set of message IDs.
     */
    @NonNull
    Set<String> getMessageIds() {
        Cursor cursor = this.query(this.uri, null, null, null, null);
        return getMessageIdsFromCursor(cursor);
    }

    /**
     * Gets the messages in the database where the message is marked read on the
     * client, but not the origin.
     *
     * @return A collection of messages.
     */
    @NonNull
    Collection<Message> getLocallyReadMessages() {
        Cursor cursor = this.query(this.uri, null,
                WHERE_CLAUSE_READ + " AND " + WHERE_CLAUSE_CHANGED, new String[] { FALSE_VALUE }, null);
        return getMessagesFromCursor(cursor);
    }

    /**
     * Gets the deleted messages in the database.
     *
     * @return A collection of messages.
     */
    @NonNull
    Collection<Message> getLocallyDeletedMessages() {
        Cursor cursor = this.query(this.uri, null,
                MessageCenterDataManager.MessageTable.COLUMN_NAME_DELETED + " = ?", new String[] { TRUE_VALUE },
                null);
        return getMessagesFromCursor(cursor);
    }

    /**
     * Marks messages read.
     *
     * @param messageIds Set of message IDs to mark as read.
     * @return Count of messages that where updated.
     */
    int markMessagesRead(@NonNull Set<String> messageIds) {
        ContentValues values = new ContentValues();
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_UNREAD, false);
        return this.updateMessages(messageIds, values);
    }

    /**
     * Marks messages unread.
     *
     * @param messageIds Set of message IDs to mark as unread.
     * @return Count of messages that where updated.
     */
    int markMessagesUnread(@NonNull Set<String> messageIds) {
        ContentValues values = new ContentValues();
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_UNREAD, true);
        return this.updateMessages(messageIds, values);
    }

    /**
     * Marks messages deleted.
     *
     * @param messageIds Set of message IDs to mark as deleted.
     * @return Count of messages that where updated.
     */
    int markMessagesDeleted(@NonNull Set<String> messageIds) {
        ContentValues values = new ContentValues();
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_DELETED, true);
        return this.updateMessages(messageIds, values);
    }

    /**
     * Marks messages read on the origin.
     *
     * @param messageIds Set of message IDs to mark as read.
     * @return Count of messages that where updated.
     */
    int markMessagesReadOrigin(@NonNull Set<String> messageIds) {
        ContentValues values = new ContentValues();
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_UNREAD_ORIG, false);
        return this.updateMessages(messageIds, values);
    }

    /**
     * Deletes messages from the database.
     *
     * @param messageIds Set of message IDs to delete.
     * @return Count of messages that were deleted.
     */
    int deleteMessages(@NonNull Set<String> messageIds) {
        String query = MessageCenterDataManager.MessageTable.COLUMN_NAME_MESSAGE_ID + " IN ( " + UAStringUtil.repeat("?", messageIds.size(), ", ") + " )";
        return this.delete(this.uri, query, messageIds.toArray(new String[0]));
    }

    /**
     * Deletes all messages from the database.
     *
     * @return Count of messages that were deleted.
     */
    int deleteAllMessages() {
        return this.delete(this.uri, null, null);
    }

    /**
     * Inserts new messages into the database.
     *
     * @param messagePayloads A list of the raw message payloads.
     * @return The number of messages that were successfully inserted into the database.
     */
    int insertMessages(@NonNull List<JsonValue> messagePayloads) {
        List<ContentValues> contentValues = new ArrayList<>();
        for (JsonValue messagePayload : messagePayloads) {
            ContentValues values = parseMessageContentValues(messagePayload);

            if (values != null) {
                // Set the client unread status the same as the origin for new messages
                values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_UNREAD, values.getAsBoolean(MessageCenterDataManager.MessageTable.COLUMN_NAME_UNREAD_ORIG));
                contentValues.add(values);
            }
        }

        if (contentValues.isEmpty()) {
            return -1;
        }

        return this.bulkInsert(this.uri,
                contentValues.toArray(new ContentValues[0]));
    }

    /**
     * Updates a message in the database.
     *
     * @param messageId The message ID to update.
     * @param messagePayload The raw message payload.
     * @return The row number that new message, or -1 if the message failed ot be updated.
     */
    int updateMessage(@NonNull String messageId, @NonNull JsonValue messagePayload) {
        ContentValues values = parseMessageContentValues(messagePayload);
        if (values == null) {
            return -1;
        }

        Uri uri = Uri.withAppendedPath(this.uri, messageId);

        return this.update(uri, values, WHERE_CLAUSE_MESSAGE_ID, new String[] { messageId });
    }

    /**
     * Updates message IDs with the content values.
     *
     * @param messageIds The message IDs to update.
     * @param values The content values of the update.
     * @return Count of messages that where updated.
     */
    private int updateMessages(@NonNull Set<String> messageIds, @NonNull ContentValues values) {
        return this.update(this.uri,
                values,
                MessageCenterDataManager.MessageTable.COLUMN_NAME_MESSAGE_ID + " IN ( " + UAStringUtil.repeat("?", messageIds.size(), ", ") + " )",
                messageIds.toArray(new String[0]));
    }

    /**
     * Get the message IDs.
     *
     * @param cursor The cursor to get the message IDs from.
     * @return The message IDs as a set of strings.
     */
    @NonNull
    private Set<String> getMessageIdsFromCursor(@Nullable Cursor cursor) {
        if (cursor == null) {
            return new HashSet<>();
        }

        Set<String> ids = new HashSet<>(cursor.getCount());

        int messageIdIndex = -1;
        while (cursor.moveToNext()) {
            if (messageIdIndex == -1) {
                messageIdIndex = cursor.getColumnIndex(MessageCenterDataManager.MessageTable.COLUMN_NAME_MESSAGE_ID);
            }
            ids.add(cursor.getString(messageIdIndex));
        }

        cursor.close();

        return ids;
    }


    /**
     * Get the message reportings.
     *
     * @param cursor The cursor to get the message reportings from.
     * @return The message reportings as a set of strings.
     */
    @NonNull
    private Collection<Message> getMessagesFromCursor(@Nullable Cursor cursor) {
        List<Message> messages = new ArrayList<>();

        if (cursor == null) {
            return messages;
        }

        // Read all the messages from the database
        while (cursor.moveToNext()) {
            try {
                String messageJson = cursor.getString(cursor.getColumnIndex(MessageCenterDataManager.MessageTable.COLUMN_NAME_RAW_MESSAGE_OBJECT));
                boolean unreadClient = cursor.getInt(cursor.getColumnIndex(MessageCenterDataManager.MessageTable.COLUMN_NAME_UNREAD)) == 1;
                boolean deleted = cursor.getInt(cursor.getColumnIndex(MessageCenterDataManager.MessageTable.COLUMN_NAME_DELETED)) == 1;

                Message message = Message.create(JsonValue.parseString(messageJson), unreadClient, deleted);
                if (message != null) {
                    messages.add(message);
                }
            } catch (JsonException e) {
                Logger.error(e, "RichPushResolver - Failed to parse message from the database.");
            }
        }

        cursor.close();

        return messages;
    }

    /**
     * Parses a raw message payload into content values.
     *
     * @param messagePayload The raw message payload.
     * @return ContentValues that can be inserted into the database, or null if the message payload
     * was invalid.
     */
    @Nullable
    private ContentValues parseMessageContentValues(@Nullable JsonValue messagePayload) {
        if (messagePayload == null || !messagePayload.isJsonMap()) {
            Logger.error("RichPushResolver - Unexpected message: %s", messagePayload);
            return null;
        }

        JsonMap messageMap = messagePayload.optMap();

        if (UAStringUtil.isEmpty(messageMap.opt(Message.MESSAGE_ID_KEY).getString())) {
            Logger.error("RichPushResolver - Message is missing an ID: %s", messagePayload);
            return null;
        }

        ContentValues values = new ContentValues();
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_TIMESTAMP, messageMap.opt(Message.MESSAGE_SENT_KEY).getString());
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_MESSAGE_ID, messageMap.opt(Message.MESSAGE_ID_KEY).getString());
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_MESSAGE_URL, messageMap.opt(Message.MESSAGE_URL_KEY).getString());
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_MESSAGE_BODY_URL, messageMap.opt(Message.MESSAGE_BODY_URL_KEY).getString());
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_MESSAGE_READ_URL, messageMap.opt(Message.MESSAGE_READ_URL_KEY).getString());
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_TITLE, messageMap.opt(Message.TITLE_KEY).getString());
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_UNREAD_ORIG, messageMap.opt(Message.UNREAD_KEY).getBoolean(true));
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_EXTRA, messageMap.opt(Message.EXTRA_KEY).toString());
        values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_RAW_MESSAGE_OBJECT, messageMap.toString());

        if (messageMap.containsKey(Message.MESSAGE_EXPIRY_KEY)) {
            values.put(MessageCenterDataManager.MessageTable.COLUMN_NAME_EXPIRATION_TIMESTAMP, messageMap.opt(Message.MESSAGE_EXPIRY_KEY).getString());
        }

        return values;
    }

}
