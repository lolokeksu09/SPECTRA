package com.spectra.twin.render

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reject oversize streams without first allocating their entire contents. */
object BoundedInput {
    fun read(input: InputStream, limit: Int): ByteArray {
        require(limit >= 0)
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - total + 1))
            if (count == -1) break
            if (count == 0) continue
            require(count <= limit - total) { "GLB превышает 16 МБ" }
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }
}
