package com.shilapi.xcertplay.hud

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** A separate clean session subscribes at QoS 0. Observation never sends navigation commands. */
internal class EcarxNavigationMqttObserver(
    private val report: (String) -> Unit,
    private val port: Int = EcarxMqtt.DEFAULT_PORT,
) {
    private val topics = listOf(
        "dim/actions/navigation/map", "dim/actions/navigation/fullscreenmap", "dim/actions/navigation/ongoing",
        "ihu/events/navigation/fullscreenmap", "ihu/events/navigation/ongoing",
        "ihu/info/navigation/turn-by-turn/next-turn", "ihu/info/navigation/turn-by-turn/icon",
        "ihu/info/navigation/turn-by-turn/lanesupport",
    )
    private var worker: Thread? = null
    private var activeSocket: Socket? = null

    @Synchronized fun start() {
        if (worker != null) return
        worker = Thread(::observe, "diplay-navigation-mqtt").apply { isDaemon = true; start() }
    }

    @Synchronized fun stop() {
        worker?.interrupt()
        worker = null
        activeSocket?.close()
        activeSocket = null
    }

    private fun observe() {
        val current = Thread.currentThread()
        var lastFailure = ""
        while (!current.isInterrupted) {
            val socket = synchronized(this) {
                if (worker !== current) return
                Socket().also { activeSocket = it }
            }
            try {
                socket.use {
                    it.connect(InetSocketAddress(EcarxMqtt.DEFAULT_HOST, port), 1500)
                    it.soTimeout = 3000
                    val output = it.getOutputStream()
                    val input = it.getInputStream()
                    output.write(EcarxMqtt.connectPacket("diplay-nav-observer"))
                    val ack = EcarxMqtt.readPacket(input, 2)
                    if (ack?.first != 0x20 || !ack.second.contentEquals(byteArrayOf(0, 0))) throw IOException("connack")
                    val body = ByteArrayOutputStream()
                    body.write(0)
                    body.write(1)
                    for (topic in topics) {
                        EcarxMqtt.mqttString(body, topic)
                        body.write(0)
                    }
                    output.write(EcarxMqtt.frame(0x82, body.toByteArray()))
                    var sequence = 0L
                    while (!current.isInterrupted) {
                        val (header, bytes) = EcarxMqtt.readPacket(input, 1024 * 1024) ?: throw IOException("ended-or-oversize")
                        if (header == 0x90) {
                            if (bytes.size != topics.size + 2 || bytes[0].toInt() != 0 || bytes[1].toInt() != 1)
                                throw IOException("suback")
                            val denied = topics.filterIndexed { i, _ -> bytes[i + 2].toInt() != 0 }
                            report("mqtt subscribed=${topics.size - denied.size}/${topics.size} denied=${denied.joinToString(",").ifEmpty { "none" }}")
                            if (denied.size == topics.size) throw IOException("denied")
                            it.soTimeout = 0
                            lastFailure = ""
                        } else if (header ushr 4 == 3 && header and 6 == 0 && bytes.size >= 2) {
                            val end = 2 + ((bytes[0].toInt() and 255) shl 8) + (bytes[1].toInt() and 255)
                            if (end > bytes.size) throw IOException("topic-length")
                            val topic = String(bytes, 2, end - 2, Charsets.UTF_8)
                            if (topic !in topics) continue
                            val payload = String(bytes, end, bytes.size - end, Charsets.UTF_8)
                            val summary = if (payload.isEmpty()) "empty" else try {
                                summarize(topic, JSONObject(payload))
                            } catch (_: org.json.JSONException) { "invalid-json" }
                            report("mqtt rx=${++sequence} retained=${header and 1 != 0} topic=$topic $summary")
                        } else throw IOException("unexpected-packet")
                    }
                }
            } catch (e: IOException) {
                if (current.isInterrupted) return
                val failure = e.javaClass.simpleName + ":" + (e.message ?: "")
                if (failure != lastFailure) report("mqtt unavailable reason=$failure retryMs=5000")
                lastFailure = failure
            }
            try { Thread.sleep(5000) } catch (_: InterruptedException) { return }
        }
    }

    private fun summarize(topic: String, json: JSONObject): String {
        fun present(key: String) = !json.isNull(key) && json.optString(key).isNotBlank()
        fun enum(key: String, vararg allowed: String): String =
            "$key=" + (json.optString(key).takeIf { it in allowed } ?: "unknown")
        return when (topic) {
            "dim/actions/navigation/map" -> enum("statusType", "always", "routeSet", "never")
            "dim/actions/navigation/fullscreenmap" -> enum("action", "switchMap")
            "dim/actions/navigation/ongoing" -> enum("action", "iconLeftClicked", "iconCenterClicked", "iconRightClicked")
            "ihu/events/navigation/fullscreenmap" -> enum("mapRequest", "showMap", "hideMap", "noRequest")
            "ihu/events/navigation/ongoing" -> enum("naviEvent", "ongoingNavi", "idle", "firstFrameReady", "naviAppOff") +
                " destinationPresent=${present("destination")}"
            "ihu/info/navigation/turn-by-turn/next-turn" -> "distancePresent=${present("distance")} roadPresent=${present("address")}"
            "ihu/info/navigation/turn-by-turn/icon" -> "iconPresent=${present("iconId")} imagePresent=${present("imageData")}"
            else -> {
                val lanes = json.optJSONArray("lanes")
                val count = (0 until (lanes?.length() ?: 0)).count { !lanes?.optJSONObject(it)?.optString("icon").isNullOrBlank() }
                "lanes=$count"
            }
        }
    }
}
