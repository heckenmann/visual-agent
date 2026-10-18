package de.heckenmann.visualagent.gradle

import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

/**
 * Creates the UI, Compose, Windows and macOS icons from the canonical SVG.
 * This build task is platform-independent and requires no native image tools.
 */
@CacheableTask
abstract class GenerateBranding : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceSvg: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val destination = outputDirectory.get().asFile
        val sourceUri = sourceSvg.get().asFile.toURI().toString()
        val images = listOf(16, 24, 32, 48, 64, 128, 256, 512, 1024).associateWith { size ->
            ByteArrayOutputStream().use { stream ->
                val transcoder =
                    PNGTranscoder().apply {
                        addTranscodingHint(PNGTranscoder.KEY_WIDTH, size.toFloat())
                        addTranscodingHint(PNGTranscoder.KEY_HEIGHT, size.toFloat())
                    }
                transcoder.transcode(TranscoderInput(sourceUri), TranscoderOutput(stream))
                val bytes = stream.toByteArray()
                val decoded = checkNotNull(ImageIO.read(bytes.inputStream())) {
                    "SVG rasterizer did not produce a PNG for ${size}px."
                }
                check(decoded.width == size && decoded.height == size) {
                    "SVG rasterizer produced ${decoded.width}x${decoded.height}, expected ${size}x${size}."
                }
                bytes
            }
        }
        val png = images.getValue(1024)
        destination.resolve("ui/icons/visual-agent.png").writeAsset(png)
        destination.resolve("compose/drawable/visual_agent.png").writeAsset(png)
        destination.resolve("packaging/visual-agent.ico").writeAsset(createIco(images))
        destination.resolve("packaging/visual-agent.icns").writeAsset(createIcns(images))
    }

    private fun java.io.File.writeAsset(content: ByteArray) {
        parentFile.mkdirs()
        writeBytes(content)
    }

    private fun createIco(images: Map<Int, ByteArray>): ByteArray {
        val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
        val directory = ByteBuffer.allocate(6 + sizes.size * 16).order(ByteOrder.LITTLE_ENDIAN)
        directory.putShort(0.toShort())
        directory.putShort(1.toShort())
        directory.putShort(sizes.size.toShort())
        var offset = directory.capacity()
        sizes.forEach { size ->
            val png = images.getValue(size)
            directory.put(if (size == 256) 0.toByte() else size.toByte())
            directory.put(if (size == 256) 0.toByte() else size.toByte())
            directory.put(0.toByte())
            directory.put(0.toByte())
            directory.putShort(1.toShort())
            directory.putShort(32.toShort())
            directory.putInt(png.size)
            directory.putInt(offset)
            offset += png.size
        }
        return ByteArrayOutputStream().use { stream ->
            stream.write(directory.array())
            sizes.forEach { size -> stream.write(images.getValue(size)) }
            stream.toByteArray()
        }
    }

    private fun createIcns(images: Map<Int, ByteArray>): ByteArray {
        val sizes =
            linkedMapOf(
                "icp4" to 16,
                "icp5" to 32,
                "icp6" to 64,
                "ic07" to 128,
                "ic08" to 256,
                "ic09" to 512,
                "ic10" to 1024,
            )
        return ByteArrayOutputStream().use { stream ->
            DataOutputStream(stream).use { output ->
                output.writeBytes("icns")
                output.writeInt(8 + sizes.values.sumOf { size -> 8 + images.getValue(size).size })
                sizes.forEach { (type, size) ->
                    val png = images.getValue(size)
                    output.writeBytes(type)
                    output.writeInt(8 + png.size)
                    output.write(png)
                }
            }
            stream.toByteArray()
        }
    }
}
