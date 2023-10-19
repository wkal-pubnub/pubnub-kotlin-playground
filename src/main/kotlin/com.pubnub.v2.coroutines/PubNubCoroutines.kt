package com.pubnub.v2.coroutines

import com.pubnub.api.PNConfiguration
import com.pubnub.api.PubNub
import com.pubnub.api.UserId
import com.pubnub.api.callbacks.SubscribeCallback
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.pubsub.*
import com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult
import com.pubnub.api.models.consumer.pubsub.message_actions.PNMessageActionResult
import com.pubnub.api.models.consumer.pubsub.objects.PNObjectEventResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private typealias StatusOrEvent = Pair<PNStatus?, PNEvent?>

interface EventsSource {
    val messages: Flow<PNMessageResult>
    val objects: Flow<PNObjectEventResult>
    val files: Flow<PNFileEventResult>
    val messageAction: Flow<PNMessageActionResult>
    val presence: Flow<PNPresenceEventResult>
    val signal: Flow<PNSignalResult>
}

class PubNubCoroutines(uuid: String, subscribeKey: String, publishKey: String? = null) : EventsSource {
    private val scope = CoroutineScope(SupervisorJob())

    fun destroy() {
        scope.cancel()
    }

    internal val pubnub = PubNub(PNConfiguration(UserId(uuid)).apply {
        this.subscribeKey = subscribeKey
        if (publishKey != null) {
            this.publishKey = publishKey
        }
    })


    private val internalFlow: SharedFlow<StatusOrEvent> = callbackFlow {
        val listener = object : SubscribeCallback() {
            override fun status(pubnub: PubNub, pnStatus: PNStatus) {
                trySendBlocking(StatusOrEvent(pnStatus, null))
            }

            override fun message(pubnub: PubNub, pnMessageResult: PNMessageResult) {
                trySendBlocking(StatusOrEvent(null, pnMessageResult))
            }

            override fun file(pubnub: PubNub, pnFileEventResult: PNFileEventResult) {
                trySendBlocking(StatusOrEvent(null, pnFileEventResult))
            }

            override fun objects(pubnub: PubNub, objectEvent: PNObjectEventResult) {
                trySendBlocking(StatusOrEvent(null, objectEvent))
            }

            override fun messageAction(pubnub: PubNub, pnMessageActionResult: PNMessageActionResult) {
                trySendBlocking(StatusOrEvent(null, pnMessageActionResult))
            }

            override fun presence(pubnub: PubNub, pnPresenceEventResult: PNPresenceEventResult) {
                trySendBlocking(StatusOrEvent(null, pnPresenceEventResult))
            }

            override fun signal(pubnub: PubNub, pnSignalResult: PNSignalResult) {
                trySendBlocking(StatusOrEvent(null, pnSignalResult))
            }
        }
        pubnub.addListener(listener)
        awaitClose {
            pubnub.removeListener(listener)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(0, 0), 0)

    val status = internalFlow.mapNotNull { it.first }
    val allEvents: Flow<PNEvent> = internalFlow.mapNotNull { it.second }

    override val messages: Flow<PNMessageResult> = allEvents.filterIsInstance<PNMessageResult>()
    override val objects: Flow<PNObjectEventResult> = allEvents.filterIsInstance<PNObjectEventResult>()
    override val files: Flow<PNFileEventResult> = allEvents.filterIsInstance<PNFileEventResult>()
    override val messageAction: Flow<PNMessageActionResult> = allEvents.filterIsInstance<PNMessageActionResult>()
    override val presence: Flow<PNPresenceEventResult> = allEvents.filterIsInstance<PNPresenceEventResult>()
    override val signal: Flow<PNSignalResult> = allEvents.filterIsInstance<PNSignalResult>()


//    fun subscribe(channel: String) {
//        pubnub.subscribe(listOf(channel))
//    }

    fun channel(id: String): ChannelBuilder = ChannelBuilder(this, id)

    companion object {
        fun newBuilder(uuid: String, subscribeKey: String) = PubnubBuilder(uuid, subscribeKey)
    }
}

class PubnubBuilder(val uuid: String, val subscribeKey: String) {
    private var publishKey: String? = null
    fun publishKey(key: String): PubnubBuilder {
        publishKey = key
        return this
    }

    fun build() = PubNubCoroutines(uuid, subscribeKey, publishKey = publishKey)
}

class ChannelBuilder internal constructor(val pubnub: PubNubCoroutines, val id: String) {
    fun build() = Channel(pubnub, id)
}

class Channel(private val pubnub: PubNubCoroutines, val channelId: String) : EventsSource {

    fun subscribe() {
        pubnub.pubnub.subscribe(listOf(channelId))
    }

    suspend fun publish(message: Any) = suspendCancellableCoroutine { continuation ->
        pubnub.pubnub.publish(channelId, message).async { result, status ->
            if (!status.error) {
                continuation.resume(result!!)
            } else {
                continuation.resumeWithException(status.exception!!)
            }
        }
    }

    override val messages: Flow<PNMessageResult>
        get() = pubnub.messages.filter { it.channel == channelId }
    override val objects: Flow<PNObjectEventResult>
        get() = pubnub.objects.filter { it.channel == channelId }
    override val files: Flow<PNFileEventResult>
        get() = pubnub.files.filter { it.channel == channelId }
    override val messageAction: Flow<PNMessageActionResult>
        get() = pubnub.messageAction.filter { it.channel == channelId }
    override val presence: Flow<PNPresenceEventResult>
        get() = pubnub.presence.filter { it.channel == channelId }
    override val signal: Flow<PNSignalResult>
        get() = pubnub.signal.filter { it.channel == channelId }
}


suspend fun main() {
    try {
        val pn = PubNubCoroutines.newBuilder("abc", "")
            .publishKey("")
            .build()
        val chatChannel = pn.channel("chat").build()
        chatChannel.subscribe()

        coroutineScope {


            launch {
                chatChannel.messages.collect {
                    println(it.message)
                }
            }

            launch {
                println(chatChannel.publish("123"))
                delay(1000)

                println(chatChannel.publish("456"))
                delay(1000)
            }
        }

        pn.destroy()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}