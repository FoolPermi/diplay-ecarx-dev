package com.shilapi.xcertplay.hud

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EcarxNavigationMqttObserverTest {
    @Test fun subscribesReadOnlyAndDistinguishesRetainedFromLiveWithoutLoggingAddress() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { broker ->
            broker.soTimeout = 5000
            val lines = LinkedBlockingQueue<String>()
            val observer = EcarxNavigationMqttObserver({ lines.add(it) }, broker.localPort)
            observer.start()
            try {
                broker.accept().use { client ->
                    client.soTimeout = 5000
                    val input = client.getInputStream()
                    val out = client.getOutputStream()
                    assertEquals(0x10, EcarxMqtt.readPacket(input)!!.first)
                    out.write(byteArrayOf(0x20, 2, 0, 0))
                    val (header, body) = EcarxMqtt.readPacket(input)!!
                    assertEquals(0x82, header)
                    val subscription = java.io.DataInputStream(body.inputStream())
                    assertEquals(1, subscription.readUnsignedShort())
                    val topics = mutableListOf<String>()
                    while (subscription.available() > 0) {
                        topics.add(subscription.readUTF())
                        assertEquals(0, subscription.readUnsignedByte())
                    }
                    assertTrue("dim/actions/navigation/map" in topics)
                    assertFalse(topics.any { '#' in it || '+' in it })
                    out.write(EcarxMqtt.frame(0x90, byteArrayOf(0, 1) + ByteArray(topics.size)))
                    assertTrue(lines.poll(5, TimeUnit.SECONDS).startsWith("mqtt subscribed="))
                    for (retained in listOf(true, false)) {
                        val message = ByteArrayOutputStream()
                        EcarxMqtt.mqttString(message, "ihu/events/navigation/ongoing")
                        message.write("""{"naviEvent":"ongoingNavi","destination":"Private Place"}""".toByteArray())
                        out.write(EcarxMqtt.frame(if (retained) 0x31 else 0x30, message.toByteArray()))
                        val line = lines.poll(5, TimeUnit.SECONDS)
                        assertTrue(line.contains("retained=$retained"))
                        assertTrue(line.endsWith("naviEvent=ongoingNavi destinationPresent=true"))
                        assertFalse(line.contains("Private"))
                    }
                    observer.stop()
                    assertEquals(-1, input.read())
                }
            } finally { observer.stop() }
        }
    }
}
