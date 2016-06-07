package com.r3corda.demos.protocols

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.serialization.deserialize
import com.r3corda.node.internal.Node
import com.r3corda.node.services.network.MockNetworkMapCache
import java.util.concurrent.TimeUnit


object ExitServerProtocol {

    val TOPIC = "exit.topic"

    // Will only be enabled if you install the Handler
    @Volatile private var enabled = false

    data class ExitMessage(val exitCode: Int)

    object Handler {

        fun register(node: Node) {
            node.net.addMessageHandler("${TOPIC}.0") { msg, registration ->
                // Just to validate we got the message
                if (enabled) {
                    val message = msg.data.deserialize<ExitMessage>()
                    System.exit(message.exitCode)
                }
            }
            enabled = true
        }
    }

    /**
     * This takes a Java Integer rather than Kotlin Int as that is what we end up with in the calling map and currently
     * we do not support coercing numeric types in the reflective search for matching constructors
     */
    class Broadcast(@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") val exitCode: Integer) : ProtocolLogic<Boolean>() {

        @Suspendable
        override fun call(): Boolean {
            if (enabled) {
                val rc = exitCode.toInt()
                val message = ExitMessage(rc)

                for (recipient in serviceHub.networkMapCache.partyNodes) {
                    doNextRecipient(recipient, message)
                }
                // Sleep a little in case any async message delivery to other nodes needs to happen
                Strand.sleep(1, TimeUnit.SECONDS)
                System.exit(rc)
            }
            return enabled
        }

        @Suspendable
        private fun doNextRecipient(recipient: NodeInfo, message: ExitMessage) {
            if (recipient.address is MockNetworkMapCache.MockAddress) {
                // Ignore
            } else {
                // TODO: messaging ourselves seems to trigger a bug for the time being and we continuously receive messages
                if (recipient.identity != serviceHub.storageService.myLegalIdentity) {
                    send(TOPIC, recipient.address, 0, message)
                }
            }
        }
    }

}