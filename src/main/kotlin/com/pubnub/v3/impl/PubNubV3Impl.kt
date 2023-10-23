package com.pubnub.v3.impl

import com.pubnub.api.PubNub
import com.pubnub.api.callbacks.SubscribeCallback
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.api.models.consumer.pubsub.PubSubResult
import com.pubnub.v3.Channel
import com.pubnub.v3.ChannelGroup
import com.pubnub.v3.ChannelGroupSubscription
import com.pubnub.v3.ChannelSubscription
import com.pubnub.v3.EventEmitter
import com.pubnub.v3.EventListener
import com.pubnub.v3.PubNubV3
import com.pubnub.v3.StatusEmitter
import com.pubnub.v3.StatusListener
import com.pubnub.v3.Subscription
import com.pubnub.v3.SubscriptionSet
import java.util.concurrent.atomic.AtomicReference

class PubNubV3Impl internal constructor(
    private val pubNub: PubNub,
    private val eventEmitter: EventEmitterImpl = EventEmitterImpl(),
    private val statusEmitter: StatusEmitterImpl = StatusEmitterImpl()
    ) : PubNubV3, EventEmitter by eventEmitter, StatusEmitter by statusEmitter {

    private val listener = object : SubscribeCallback() {
        override fun status(pubnub: PubNub, pnStatus: PNStatus) {
            statusEmitter.onStatus(pnStatus)
        }

        override fun message(pubnub: PubNub, pnMessageResult: PNMessageResult) {
            eventEmitter.onMessage(pnMessageResult)
        }
    }

    init {
        pubNub.addListener(listener)
    }

    fun shutdown() {
        pubNub.removeListener(listener)
        pubNub.disconnect()
    }

    private val channelSubscriptions = mutableMapOf<Channel, MutableSet<Subscription>>()

    internal fun subscribe(vararg subscriptions: ChannelSubscription) {
        synchronized(channelSubscriptions) {
            val toSubscribe = mutableSetOf<String>()
            subscriptions.forEach { subscription ->
                val set = channelSubscriptions.computeIfAbsent(subscription.channel) { mutableSetOf() }
                set.add(subscription)
                if (set.size == 1) { //TODO what about presence
                    toSubscribe += subscription.channel.id
                }
            }
            pubNub.subscribe(channels = toSubscribe.toList())
        }
    }

    internal fun unsubscribe(subscription: ChannelSubscription) {
        synchronized(channelSubscriptions) {
            val toUnsubscribe = mutableSetOf<String>()
            channelSubscriptions.putIfAbsent(subscription.channel, mutableSetOf())
            val set = channelSubscriptions[subscription.channel]
            set!!
            set.remove(subscription)
            if (set.size == 0) { //TODO what about presence
                toUnsubscribe += subscription.channel.id
            }
            pubNub.unsubscribe(channels = toUnsubscribe.toList())
        }
    }

    private val channelGroupSubscriptions = mutableMapOf<ChannelGroup, MutableSet<Subscription>>()

    internal fun subscribe(subscription: ChannelGroupSubscription) {
        synchronized(channelGroupSubscriptions) {
            val toSubscribe = mutableSetOf<String>()
            channelGroupSubscriptions.putIfAbsent(subscription.channelGroup, mutableSetOf())
            val set = channelGroupSubscriptions[subscription.channelGroup]
            set!!
            set.add(subscription)
            if (set.size > 0) { //TODO what about presence
                toSubscribe += subscription.channelGroup.id
            }
            pubNub.subscribe(channelGroups = toSubscribe.toList())
        }
    }

    internal fun unsubscribe(subscription: ChannelGroupSubscription) {
        synchronized(channelGroupSubscriptions) {
            val toUnsubscribe = mutableSetOf<String>()
            channelGroupSubscriptions.putIfAbsent(subscription.channelGroup, mutableSetOf())
            val set = channelGroupSubscriptions[subscription.channelGroup]
            set!!
            set.remove(subscription)
            if (set.size == 0) { //TODO what about presence
                toUnsubscribe += subscription.channelGroup.id
            }
            pubNub.unsubscribe(channelGroups = toUnsubscribe.toList())
        }
    }


    override fun channel(id: String): Channel {
        return ChannelImpl(this, id)
    }

    override fun channelGroup(id: String): ChannelGroup {
        return ChannelGroupImpl(this, id)
    }
}

class SubscriptionSetImpl internal constructor(
    private val pubnub: PubNubV3,
    private val eventEmitter: EventEmitterImpl = EventEmitterImpl()
) : SubscriptionSet,
    EventEmitter by eventEmitter,
    FilterableSubscription {

    private var unsubscribed: Boolean = false
    private val subscriptions: AtomicReference<Set<Subscription>> = AtomicReference(emptySet())

    init {
        eventEmitter.filter = this
        pubnub.addListener(eventEmitter)
    }

    override fun add(subscription: Subscription) {
        subscriptions.updateAndGet {
            it.toMutableSet().apply { add(subscription) }
        }
    }

    override fun remove(subscription: Subscription) {
        subscriptions.updateAndGet {
            it.toMutableSet().apply { remove(subscription) }
        }
    }

    override fun subscribe() {
        if (unsubscribed) {
            error("Cannot reuse a SubscriptionSet that was already unsubscribed.")
        }
        subscriptions.get().forEach {
            it.subscribe()
        }
    }

    override fun unsubscribe() {
        unsubscribed = true
        subscriptions.get().forEach {
            it.unsubscribe()
        }
        pubnub.removeListener(eventEmitter)
        eventEmitter.removeAllListeners()
    }

//    override fun close() {
//        unsubscribe()
//    }

    override fun accept(result: PubSubResult): Boolean {
        return subscriptions.get().filterIsInstance<FilterableSubscription>().any { it.accept(result) }
    }
}

internal fun interface FilterableSubscription {
    fun accept(result: PubSubResult): Boolean
}

internal class ChannelSubscriptionImpl(
    private val pubNub: PubNubV3Impl,
    override val channel: Channel,
    private val eventEmitter: EventEmitterImpl = EventEmitterImpl()
) : ChannelSubscription, FilterableSubscription, EventEmitter by eventEmitter {

    init {
        eventEmitter.filter = this
        pubNub.addListener(eventEmitter) // filter events
    }

    override fun subscribe() {
        pubNub.subscribe(this)
    }

    override fun accept(result: PubSubResult): Boolean {
        return result.channel == channel.id || (channel.id.endsWith("*") && result.subscription == channel.id)
    }

    override val withPresence: Boolean
        get() = TODO("Not yet implemented")

    override fun unsubscribe() {
        pubNub.unsubscribe(this)
    }
}

internal class ChannelGroupSubscriptionImpl(
    private val pubNub: PubNubV3Impl,
    override val channelGroup: ChannelGroup,
    private val eventEmitter: EventEmitterImpl = EventEmitterImpl()
) : ChannelGroupSubscription, FilterableSubscription, EventEmitter by eventEmitter {

    init {
        pubNub.addListener(eventEmitter) // filter events
    }

    override fun subscribe() {
        pubNub.subscribe(this)
    }

    override fun accept(result: PubSubResult): Boolean {
        return result.subscription == channelGroup.id
    }

    override val withPresence: Boolean
        get() = TODO("Not yet implemented")

    override fun unsubscribe() {
        pubNub.unsubscribe(this)
    }

}


internal data class ChannelImpl(private val pubNub: PubNubV3Impl, override val id: String) : Channel {

    override fun subscription(withPresence: Boolean): ChannelSubscription {
        return ChannelSubscriptionImpl(pubNub, this) //TODO withpresence
    }

    override fun publish() {
        TODO("Not yet implemented")
    }
}

internal data class ChannelGroupImpl(private val pubNub: PubNubV3Impl, override val id: String) : ChannelGroup {
    override fun subscription(): ChannelGroupSubscription {
        return ChannelGroupSubscriptionImpl(pubNub, this)
    }

    override fun publish() {
        TODO("Not yet implemented")
    }

}

internal class EventEmitterImpl : EventEmitter, EventListener {
    var filter: FilterableSubscription = FilterableSubscription { true }
    private var eventListeners = AtomicReference(setOf<EventListener>())

    override fun addListener(listener: EventListener) {
        eventListeners.updateAndGet {
            it.toMutableSet().apply { add(listener) }
        }
    }

    override fun removeListener(listener: EventListener) {
        eventListeners.updateAndGet {
            it.toMutableSet().apply { remove(listener) }
        }
    }

    override fun onMessage(result: PNMessageResult) {
        if (!filter.accept(result)) {
            return
        }
        eventListeners.get().forEach {
            it.onMessage(result)
        }
    }

    fun removeAllListeners() {
        eventListeners.updateAndGet { emptySet() }
    }
}

internal class StatusEmitterImpl : StatusEmitter, StatusListener {
    private var statusListeners = AtomicReference(setOf<StatusListener>())

    override fun onStatus(status: PNStatus) {
        statusListeners.get().forEach {
            it.onStatus(status)
        }
    }

    override fun addListener(listener: StatusListener) {
        statusListeners.updateAndGet {
            it.toMutableSet().apply { add(listener) }
        }
    }

    override fun removeListener(listener: StatusListener) {
        statusListeners.updateAndGet {
            it.toMutableSet().apply { remove(listener) }
        }
    }
}
