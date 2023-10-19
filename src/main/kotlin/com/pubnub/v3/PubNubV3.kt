package com.pubnub.v3

import com.pubnub.api.PNConfiguration
import com.pubnub.api.PubNub
import com.pubnub.api.UserId
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.v3.impl.PubNubV3Impl
import com.pubnub.v3.impl.SubscriptionSetImpl


interface PubNubV3 : EventEmitter {
    fun channel(id: String): Channel
    fun channelGroup(id: String): ChannelGroup
}

interface EventListener {
    fun onMessage(result: PNMessageResult)
    fun onStatus() {}
//    fun onSignal()
//    fun onPresence()
//    fun onObject()
//    fun onMessageReaction()
//    fun onFile()
}

interface EventEmitter {
    fun addListener(listener: EventListener)
    fun removeListener(listener: EventListener)
}

interface SubscriptionSet : EventEmitter { // this could implement Set<Subscription> if we want
    fun add(subscription: Subscription)
    fun remove(subscription: Subscription)
    fun subscribe()
    fun unsubscribe()
}

interface Subscription : EventEmitter {
    val withPresence: Boolean
    fun subscribe()
    fun unsubscribe()
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
    fun subscription(withPresence: Boolean = false): ChannelGroupSubscription
}

interface PublishCapable {
    fun publish()
//    fun signal()
//    fun fire()
}


fun main() {
    val pubnub = PubNubV3()
    val channel = pubnub.channel("my_channel")
    val myChannelSub = channel.subscription(false)

    myChannelSub.addListener(object : EventListener {
        override fun onMessage(result: PNMessageResult) {
            println(result)
        }

        override fun onStatus() {

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
        set.addListener(object : EventListener {
            override fun onMessage(result: PNMessageResult) {
                println(result)
            }
        })

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