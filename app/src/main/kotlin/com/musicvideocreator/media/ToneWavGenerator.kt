package com.musicvideocreator.media

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

/** Generates a mono 16-bit PCM WAV tone file, used as a stand-in test source for the composition spike. */
object ToneWavGenerator {

    private const val SAMPLE_RATE = 44100

    fun generate(context: Context, fileName: String, frequencyHz: Double, durationSec: Double): File {
        val file = File(context.getExternalFilesDir(null), fileName)
        val sampleCount = (SAMPLE_RATE * durationSec).toInt()
        val dataSize = sampleCount * 2
        val totalSize = 36 + dataSize

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            writeWavHeader(raf, totalSize, dataSize)

            val amplitude = Short.MAX_VALUE * 0.8
            for (i in 0 until sampleCount) {
                val angle = 2.0 * PI * frequencyHz * i / SAMPLE_RATE
                val sample = (sin(angle) * amplitude).toInt()
                raf.writeByte(sample and 0xFF)
                raf.writeByte((sample shr 8) and 0xFF)
            }
        }
        return file
    }

    private fun writeWavHeader(raf: RandomAccessFile, totalSize: Int, dataSize: Int) {
        raf.writeBytes("RIFF")
        raf.write(intToLE(totalSize))
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.write(intToLE(16))
        raf.write(shortToLE(1))
        raf.write(shortToLE(1))
        raf.write(intToLE(SAMPLE_RATE))
        raf.write(intToLE(SAMPLE_RATE * 2))
        raf.write(shortToLE(2))
        raf.write(shortToLE(16))
        raf.writeBytes("data")
        raf.write(intToLE(dataSize))
    }

    private fun intToLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )
}
