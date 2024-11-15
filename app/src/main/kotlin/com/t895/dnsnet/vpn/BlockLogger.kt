package com.t895.dnsnet.vpn

import com.t895.dnsnet.FileHelper
import com.t895.dnsnet.loge
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

@Serializable
data class LoggedConnection(
    val allowed: Boolean,
    var attempts: Long,
) {
    fun attempt() = attempts++
}

data class LoggedConnectionState(
    val name: String,
    val allowed: Boolean,
    var attempts: Long,
)

@Serializable
data class BlockLogger(val connections: MutableMap<String, LoggedConnection> = HashMap()) {
    @Transient
    private var onConnection: ((connections: Map<String, LoggedConnection>) -> Unit)? = null

    fun setOnConnectionListener(listener: ((connections: Map<String, LoggedConnection>) -> Unit)?) {
        onConnection = listener
    }

    fun newConnection(name: String, allowed: Boolean) {
        val connection = connections[name]
        if (connection != null) {
            if (connection.allowed != allowed) {
                connections.remove(name)
                connections[name] = LoggedConnection(allowed, 1)
            } else {
                connection.attempt()
            }
        } else {
            connections[name] = LoggedConnection(allowed, 1)
        }
        onConnection?.invoke(connections)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun save(name: String = DEFAULT_LOG_FILENAME) {
        val outputStream = FileHelper.openWrite(name)
        if (outputStream == null) {
            loge("Failed to write connection history")
            return
        }

        try {
            Json.encodeToStream(this, outputStream)
        } catch (e: Exception) {
            loge("Failed to write connection history", e)
        }
    }

    fun clear() {
        connections.clear()
        save(DEFAULT_LOG_FILENAME)
    }

    companion object {
        private const val DEFAULT_LOG_FILENAME = "connections.json"

        @OptIn(ExperimentalSerializationApi::class)
        fun load(name: String = DEFAULT_LOG_FILENAME): BlockLogger {
            val inputStream =
                FileHelper.openRead(name) ?: return BlockLogger()
            return try {
                Json.decodeFromStream<BlockLogger>(inputStream)
            } catch (e: Exception) {
                loge("Failed to load connection history", e)
                BlockLogger()
            }
        }
    }
}
