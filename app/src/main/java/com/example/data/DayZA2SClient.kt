package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DayZA2SClient(
    val host: String = "198.73.57.157",
    val port: Int = 2303,
    private val timeoutMs: Int = 3500
) {
    suspend fun query(): DayZServerStatus = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var socket: DatagramSocket? = null
        try {
            val address = InetAddress.getByName(host)
            socket = DatagramSocket().apply {
                soTimeout = timeoutMs
            }

            // Standard Steam A2S_INFO query:
            // 0xFF 0xFF 0xFF 0xFF 'T' "Source Engine Query\0"
            val queryHeader = byteArrayOf(
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                0x54.toByte(), // 'T'
                'S'.code.toByte(), 'o'.code.toByte(), 'u'.code.toByte(), 'r'.code.toByte(),
                'c'.code.toByte(), 'e'.code.toByte(), ' '.code.toByte(), 'E'.code.toByte(),
                'n'.code.toByte(), 'g'.code.toByte(), 'i'.code.toByte(), 'n'.code.toByte(),
                'e'.code.toByte(), ' '.code.toByte(), 'Q'.code.toByte(), 'u'.code.toByte(),
                'e'.code.toByte(), 'r'.code.toByte(), 'y'.code.toByte(), 0x00.toByte()
            )

            var packetToSend = DatagramPacket(queryHeader, queryHeader.size, address, port)
            socket.send(packetToSend)

            val receiveBuffer = ByteArray(4096)
            var receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            var data = receivePacket.data
            var length = receivePacket.length

            if (length < 5) {
                return@withContext DayZServerStatus.offline()
            }

            // Check if response is A2S_SERVERQUERY_GETCHALLENGE (0x41)
            if (data[4] == 0x41.toByte() && length >= 9) {
                val challenge = ByteArray(4)
                System.arraycopy(data, 5, challenge, 0, 4)

                // Resend A2S_INFO query with challenge appended
                val queryWithChallenge = ByteArray(queryHeader.size + 4)
                System.arraycopy(queryHeader, 0, queryWithChallenge, 0, queryHeader.size)
                System.arraycopy(challenge, 0, queryWithChallenge, queryHeader.size, 4)

                packetToSend = DatagramPacket(queryWithChallenge, queryWithChallenge.size, address, port)
                socket.send(packetToSend)

                receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                socket.receive(receivePacket)
                data = receivePacket.data
                length = receivePacket.length
            }

            val rttMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

            // Verify A2S_INFO response header: 0xFF 0xFF 0xFF 0xFF 0x49 ('I')
            if (length < 5 || data[4] != 0x49.toByte()) {
                return@withContext DayZServerStatus.offline()
            }

            parseA2SInfo(data, length, rttMs)
        } catch (e: Exception) {
            DayZServerStatus.offline()
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun parseA2SInfo(data: ByteArray, length: Int, pingMs: Long): DayZServerStatus {
        val buffer = ByteBuffer.wrap(data, 5, length - 5).order(ByteOrder.LITTLE_ENDIAN)

        // Protocol version
        if (buffer.remaining() < 1) return DayZServerStatus.offline()
        val protocol = buffer.get()

        // Server name
        val rawName = readNullTerminatedString(buffer)
        val name = if (rawName.isNotBlank()) rawName else "HOD DayZ Heaven S16 | PVE ONLY"

        // Map
        val rawMap = readNullTerminatedString(buffer)
        val map = when (rawMap.lowercase()) {
            "chernarusplus" -> "ChernarusPlus"
            "enoch", "livonia" -> "Livonia"
            "sakhal" -> "Sakhal"
            else -> if (rawMap.isNotBlank()) rawMap else "ChernarusPlus"
        }

        // Folder
        val folder = readNullTerminatedString(buffer)
        // Game
        val game = readNullTerminatedString(buffer)

        // Steam App ID (short)
        if (buffer.remaining() < 2) return DayZServerStatus.offline()
        val appId = buffer.short

        // Players, Max Players, Bots
        if (buffer.remaining() < 3) return DayZServerStatus.offline()
        val players = buffer.get().toInt() and 0xFF
        val maxPlayersRaw = buffer.get().toInt() and 0xFF
        val maxPlayers = if (maxPlayersRaw > 0) maxPlayersRaw else 60
        val bots = buffer.get().toInt() and 0xFF

        // Server type, environment, visibility, VAC
        if (buffer.remaining() < 4) return DayZServerStatus.offline()
        val serverType = buffer.get()
        val environment = buffer.get()
        val visibility = buffer.get()
        val vac = buffer.get()

        // Version string
        val version = readNullTerminatedString(buffer)

        // Extra Data Flags (EDF)
        var keywords = ""
        if (buffer.hasRemaining()) {
            val edf = buffer.get().toInt() and 0xFF
            // 0x80: Port (2 bytes)
            if ((edf and 0x80) != 0 && buffer.remaining() >= 2) {
                buffer.short
            }
            // 0x10: SteamID (8 bytes)
            if ((edf and 0x10) != 0 && buffer.remaining() >= 8) {
                buffer.long
            }
            // 0x40: SourceTV Port (2 bytes) + Name (null-terminated string)
            if ((edf and 0x40) != 0 && buffer.remaining() >= 2) {
                buffer.short
                readNullTerminatedString(buffer)
            }
            // 0x20: Keywords / Tags (null-terminated string)
            if ((edf and 0x20) != 0) {
                keywords = readNullTerminatedString(buffer)
            }
        }

        val (timeStr, phase) = DayZTimeHelper.parseInGameTime(keywords)

        return DayZServerStatus(
            isOnline = true,
            serverName = name,
            players = players,
            maxPlayers = maxPlayers,
            map = map,
            inGameTime = timeStr,
            inGamePhase = phase,
            rawKeywords = keywords,
            pingMs = pingMs,
            lastUpdatedMillis = System.currentTimeMillis(),
            errorMessage = null
        )
    }

    private fun readNullTerminatedString(buffer: ByteBuffer): String {
        val bytes = mutableListOf<Byte>()
        while (buffer.hasRemaining()) {
            val b = buffer.get()
            if (b == 0x00.toByte()) break
            bytes.add(b)
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
