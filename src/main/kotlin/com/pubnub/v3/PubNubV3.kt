package com.pubnub.v3

import com.pubnub.api.PNConfiguration
import com.pubnub.api.PubNub
import com.pubnub.api.UserId
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult
import com.pubnub.api.models.consumer.pubsub.PNSignalResult
import com.pubnub.v3.impl.PubNubV3Impl
import com.pubnub.v3.impl.SubscriptionSetImpl


interface PubNubV3 : EventEmitter, StatusEmitter {
    fun channel(id: String): Channel
    fun channelGroup(id: String): ChannelGroup
}

interface EventListener {
    fun onMessage(result: PNMessageResult) {}
    fun onSignal(result: PNSignalResult) {}
    fun onPresence(result: PNPresenceEventResult) {}
//    fun onObject()
//    fun onMessageReaction()
//    fun onFile()
}

fun interface StatusListener {
    fun onStatus(status: PNStatus)
//    fun onSignal()
//    fun onPresence()
//    fun onObject()
//    fun onMessageReaction()
//    fun onFile()
}

interface EventEmitter {
    fun addListener(listener: EventListener)
    fun removeListener(listener: EventListener)

    fun onMessage(action: (PNMessageResult) -> Unit) {
        addListener(object : EventListener {
            override fun onMessage(result: PNMessageResult) {
                action(result)
            }
        })
    }

    fun onSignal(action: (PNSignalResult) -> Unit) {
        addListener(object : EventListener {
            override fun onSignal(result: PNSignalResult) {
                action(result)
            }
        })
    }

    fun onPresence(action: (PNPresenceEventResult) -> Unit) {
        addListener(object : EventListener {
            override fun onPresence(result: PNPresenceEventResult) {
                action(result)
            }
        })
    }
}

interface StatusEmitter {
    fun addListener(listener: StatusListener)
    fun removeListener(listener: StatusListener)
}

// throw an error if found inconsistency in added channels: subscribed vs unsubscribed channels in set
interface SubscriptionSet : EventEmitter, SubscribeCapable {
    fun add(subscription: Subscription)
    fun remove(subscription: Subscription)
}

interface Subscription : EventEmitter, SubscribeCapable {
    val withPresence: Boolean
}

interface ChannelSubscription : Subscription {
    val channel: Channel
}

interface ChannelGroupSubscription : Subscription {
    val channelGroup: ChannelGroup
}

interface Channel : HasId, PublishCapable {
    fun subscription(withPresence: Boolean = false): ChannelSubscription
}

interface ChannelGroup : HasId, PublishCapable {
    fun subscription(): ChannelGroupSubscription
}

interface PublishCapable {
    fun publish()
//    fun signal()
//    fun fire()
}

interface SubscribeCapable {
    fun subscribe()
    fun unsubscribe()
}

fun main() {
    val pubnub = PubNubV3()
    val channel = pubnub.channel("my_channel")
    val myChannelSub = channel.subscription(false)


//    myChannelSub.messages.collect { Event ->
//
//    }
//
//    myChannelSub.presence.collect { Event ->
//
//    }

    myChannelSub.addListener(object : EventListener {
        override fun onMessage(result: PNMessageResult) {
            println(result)
        }

    })
    myChannelSub.subscribe()
    // do stuff ...
    myChannelSub.unsubscribe()
    // myChannelSub = null

}

fun main2() {
    val pubnub = PubNubV3()

    val sub1 = pubnub.channel("my_channel").subscription(true)
    val sub2 = pubnub.channel("other_channel").subscription()
    pubnub.subscriptionSetOf(sub1, sub2).let { set ->
        set.onMessage { message ->
            println(message)
        }

        set.subscribe()

        // do stuff ...

        set.unsubscribe()
    }
}










interface HasId { // do we even need this interface? maybe not
    val id: String
}

//temporary helper
fun PubNubV3(): PubNubV3 = PubNubV3Impl(PubNub(PNConfiguration(UserId("aaa"))))

//temporary helper
fun PubNubV3.subscriptionSetOf(vararg subscriptions: Subscription) =
    SubscriptionSetImpl(this).apply {
        subscriptions.forEach { add(it) }
    }