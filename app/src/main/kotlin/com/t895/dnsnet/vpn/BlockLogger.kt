package com.t895.dnsnet.vpn

import android.util.Log
import com.t895.dnsnet.FileHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

@Serializable
data class LoggedConnection(
    val name: String,
    val allowed: Boolean,
    var attempts: Long,
) {
    fun attempt() = attempts++
}

@Serializable
data class BlockLogger(
    private val connections: MutableMap<String, LoggedConnection> = HashMap(),
) {
    fun newConnection(name: String, allowed: Boolean) {
        if (connections.containsKey(name)) {
            connections[name]?.attempt()
        } else {
            connections[name] = LoggedConnection(name, allowed, 1)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun save(name: String) {
        val outputStream = FileHelper.openWrite(name)
        if (outputStream == null) {
            Log.e(TAG, "Failed to write connection history")
            return
        }

        try {
            Json.encodeToStream(this, outputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write connection history")
        }
    }

    companion object {
        const val TAG = "BlockLogger"

        @OptIn(ExperimentalSerializationApi::class)
        fun load(name: String): BlockLogger {
            val inputStream = FileHelper.openRead(name) ?: return BlockLogger()
            return try {
                Json.decodeFromStream<BlockLogger>(inputStream)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load connection history - ${e.message}")
                BlockLogger()
            }
        }
    }
}
