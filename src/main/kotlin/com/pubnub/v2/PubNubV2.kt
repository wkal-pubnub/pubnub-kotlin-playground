package com.pubnub.v2

import com.pubnub.api.PubNub
import com.pubnub.api.callbacks.SubscribeCallback
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import java.util.concurrent.atomic.AtomicReference


class PubNubV2 internal constructor(private val pubNub: PubNub, private val emitter: EventEmitterImpl = EventEmitterImpl()) : EventEmitter by emitter {

    private val listener = object : SubscribeCallback() {
        override fun status(pubnub: PubNub, pnStatus: PNStatus) {
            emitter.onStatus()
        }

        override fun message(pubnub: PubNub, pnMessageResult: PNMessageResult) {
            emitter.onMessage()
        }
    }

    init {
        pubNub.addListener(listener)
    }

    fun shutdown() {
        pubNub.removeListener(listener)
        pubNub.disconnect()
    }

    private val subscriptionTracker = mutableMapOf<String, MutableSet<Channel>>()

    internal fun subscribe(vararg channels: Channel) {
        val toSubscribe = mutableSetOf<String>()
        channels.forEach { channel ->
            subscriptionTracker.putIfAbsent(channel.id, mutableSetOf()) // todo needs synchronization
            val set = subscriptionTracker[channel.id]
            if (set != null) {
                synchronized(set) {
                    set.add(channel)
                    if (set.size > 0) {
                        toSubscribe += channel.id
                    }
                }
            }
        }
        pubNub.subscribe(toSubscribe.toList())
    }

    internal fun unsubscribe(vararg channels: Channel) {
        val toUnsubscribe = mutableSetOf<String>()
        channels.forEach { channel ->
            val set = subscriptionTracker[channel.id]
            if (set != null) {
                synchronized(set) {
                    set.remove(channel)
                    if (set.size == 0) {
                        toUnsubscribe += channel.id
                    }
                }
            }
        }
        pubNub.subscribe(toUnsubscribe.toList())
    }
}

interface EventEmitter {
    fun addListener(listener: EventListener)
    fun removeListener(listener: EventListener)
}

class EventEmitterImpl : EventEmitter, EventListener {
    private var listeners = AtomicReference(setOf<EventListener>())

    override fun addListener(listener: EventListener) {
        listeners.updateAndGet {
            it.toMutableSet().apply { add(listener) }
        }
    }

    override fun removeListener(listener: EventListener) {
        listeners.updateAndGet {
            it.toMutableSet().apply { remove(listener) }
        }
    }

    override fun onMessage() {
        listeners.get().forEach {
            it.onMessage()
        }
    }

    override fun onStatus() {
        listeners.get().forEach {
            it.onStatus()
        }
    }

//    override fun onSignal() {
//        TODO("Not yet implemented")
//    }
//
//    override fun onPresence() {
//        TODO("Not yet implemented")
//    }
//
//    override fun onObject() {
//        TODO("Not yet implemented")
//    }
//
//    override fun onMessageReaction() {
//        TODO("Not yet implemented")
//    }
//
//    override fun onFile() {
//        TODO("Not yet implemented")
//    }

}

interface EventListener {
    fun onMessage()
    fun onStatus()
//    fun onSignal()
//    fun onPresence()
//    fun onObject()
//    fun onMessageReaction()
//    fun onFile()
}

interface SubscribeCapable {
    fun subscribe()
    fun unsubscribe()
}

interface HasId {
    val id: String
}


interface SubscriptionSet : MutableSet<Channel>, SubscribeCapable, EventEmitter

class SubscriptionSetImpl private constructor(
    private val pubnub: PubNubV3,
    private val set: MutableSet<Channel> = mutableSetOf(),
    private val eventEmitterImpl: EventEmitterImpl = EventEmitterImpl()
) : SubscriptionSet,
    MutableSet<Channel> by set,
    EventEmitter by eventEmitterImpl,
    EventListener {

    private var subscribed = false

    override fun subscribe() {
        synchronized(this) {
            subscribed = true
            pubnub.subscribe(*this.toTypedArray())
        }
    }

    override fun unsubscribe() {
        synchronized(this) {
            subscribed = false
            pubnub.unsubscribe(*this.toTypedArray())
        }
    }

    override fun add(element: Channel): Boolean {
        return set.add(element).also {
            (element as ChannelImpl).takeOwnership(this)
            if (subscribed) {
                pubnub.subscribe(element)
            } else {
                pubnub.unsubscribe(element)
            }
        }
    }

    override fun addAll(elements: Collection<Channel>): Boolean {
        return set.addAll(elements).also {
            elements.forEach { element ->
                (element as ChannelImpl).takeOwnership(this)
            }
            if (subscribed) {
                pubnub.subscribe(*elements.toTypedArray())
            } else {
                pubnub.unsubscribe(*elements.toTypedArray())
            }
        }
    }

    override fun remove(element: Channel): Boolean {
        if (set.remove(element)) {
            (element as ChannelImpl).release()
            return true
        }
        return false
    }

    override fun onMessage() {
        TODO("Not yet implemented")
    }

    override fun onStatus() {
        TODO("Not yet implemented")
    }

}

interface Channel : SubscribeCapable, EventEmitter, HasId

private class ChannelImpl(private val pubNub: PubNubV3, override val id: String) : Channel,
    EventEmitter by EventEmitterImpl() {

    private var subscribeOwner: Any? = null

    @Synchronized
    private fun checkSubscription() {
        subscribeOwner?.let { owner ->
            error("Channel $this already is managed by another subscription set: $owner")
        }
    }

    @Synchronized
    fun takeOwnership(newOwner: Any) {
        checkSubscription()
        subscribeOwner = newOwner
    }

    fun release() {
        subscribeOwner = null
    }

    override fun subscribe() {
        checkSubscription()
        pubNub.subscribe(this)
    }

    override fun unsubscribe() {
        checkSubscription()
        pubNub.unsubscribe(this)
    }
}
