/* Copyright Airship and Contributors */

package com.urbanairship.chat.api

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/** Data classes for building request payloads to be sent to the server. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal sealed class ChatRequest(
    val action: String,
    private val origin: String = ORIGIN
) : JsonSerializable {
    companion object {
        private const val ACTION_FETCH_CONVERSATION = "fetch_conversation"
        private const val ACTION_SEND_MESSAGE = "send_message"
        private const val ACTION_HEARTBEAT = "heartbeat"

        private const val ORIGIN = "mobile_android"

        private const val KEY_ACTION = "action"
        private const val KEY_ORIGIN = "origin"
        private const val KEY_UVP = "uvp"
        private const val KEY_PAYLOAD = "payload"
        private const val KEY_TEXT = "text"
        private const val KEY_ATTACHMENT = "attachment"
        private const val KEY_REQUEST_ID = "request_id"
        private const val KEY_ROUTING = "routing"
        private const val KEY_AGENT = "agent"
    }

    abstract val uvp: String

    internal fun toJsonString(): String = toJsonValue().toString()

    protected fun jsonMapBuilder() =
            JsonMap.newBuilder()
                    .put(KEY_ACTION, action)
                    .put(KEY_UVP, uvp)
                    .put(KEY_ORIGIN, origin)

    /**
     * Heartbeat request.
     */
    internal data class Heartbeat(
        override val uvp: String
    ) : ChatRequest(ACTION_HEARTBEAT) {

        companion object {
            fun parse(json: String): Heartbeat {
                val jsonMap = JsonValue.parseString(json).optMap()
                val uvp = requireNotNull(jsonMap.opt(KEY_UVP).string) { "'$KEY_UVP' may not be null!" }
                return Heartbeat(uvp = uvp)
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapBuilder().build().toJsonValue()
    }

    /**
     * Requests the current conversation for the given [uvp] from the server.
     *
     * If successful, a new `conversation_loaded` message will be returned over the WebSocket.
     * @see ChatResponse.ConversationLoaded
     */
    internal data class FetchConversation(
        override val uvp: String
    ) : ChatRequest(ACTION_FETCH_CONVERSATION) {
        companion object {
            fun parse(json: String): FetchConversation {
                val jsonMap = JsonValue.parseString(json).optMap()
                val uvp = requireNotNull(jsonMap.opt(KEY_UVP).string) { "'$KEY_UVP' may not be null!" }
                return FetchConversation(uvp = uvp)
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapBuilder().build().toJsonValue()
    }

    /**
     * Sends a new message in the conversation.
     *
     * If successful, a new `message_received` message will be returned over the WebSocket.
     * @see ChatResponse.MessageReceived
     */
    internal data class SendMessage(
        override val uvp: String,
        val payload: Message
    ) : ChatRequest(ACTION_SEND_MESSAGE) {

        companion object {
            fun parse(json: String): SendMessage {
                val jsonMap = JsonValue.parseString(json).optMap()
                val uvp = requireNotNull(jsonMap.opt(KEY_UVP).string) { "'$KEY_UVP' may not be null!" }
                val payload = Message.fromJsonMap(jsonMap.opt(KEY_PAYLOAD).optMap())
                return SendMessage(uvp = uvp, payload = payload)
            }
        }

        internal constructor(
            uvp: String,
            text: String? = null,
            attachment: String? = null,
            requestId: String,
            routing: ChatRouting
        ) : this(uvp, Message(requestId, text, attachment, routing))

        internal data class Message(
                val requestId: String,
                val text: String? = null,
                val attachment: String? = null,
                val routing: JsonSerializable,
        ) : JsonSerializable {
            companion object {
                fun fromJsonMap(jsonMap: JsonMap): Message {
                    val requestId = requireNotNull(jsonMap.opt(KEY_REQUEST_ID).string) { "$KEY_REQUEST_ID' may not be null!" }
                    val text = jsonMap.get(KEY_TEXT)?.string
                    val attachment = jsonMap.get(KEY_ATTACHMENT)?.string
                    val routing = jsonMap.get(KEY_ROUTING)?.value
                    return Message(
                            requestId = requestId,
                            text = text,
                            attachment = attachment,
                            routing = routing as JsonSerializable
                    )
                }
            }

            override fun toJsonValue(): JsonValue =
                    JsonMap.newBuilder()
                            .put(KEY_REQUEST_ID, requestId)
                            .put(KEY_TEXT, text)
                            .put(KEY_ATTACHMENT, attachment)
                            .put(KEY_ROUTING, routing)
                            .build()
                            .toJsonValue()
        }

        override fun toJsonValue(): JsonValue =
                jsonMapBuilder()
                        .put(KEY_PAYLOAD, payload)
                        .build()
                        .toJsonValue()
    }

    internal data class ChatRouting(
            val agent: String? = ""
    ) : JsonSerializable {
        companion object {
            fun fromJsonMap(jsonMap: JsonMap): ChatRouting {
                val agent = jsonMap.get(KEY_AGENT)?.string
                return ChatRouting(
                        agent = agent
                )
            }
        }

        override fun toJsonValue(): JsonValue =
                JsonMap.newBuilder()
                        .put(KEY_AGENT, agent)
                        .build()
                        .toJsonValue()
    }
}
