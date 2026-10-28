package com.spectra.twin.render

/** Validate the GLB container header before passing untrusted data to native code. */
object GlbHeader {
    fun validate(data: ByteArray) {
        require(data.size >= 20) { "Слишком короткий GLB" }
        require(data[0] == 103.toByte() && data[1] == 108.toByte() &&
            data[2] == 84.toByte() && data[3] == 70.toByte()) { "Не GLB" }
        fun u32(o: Int) = (data[o].toInt() and 255) or
            ((data[o+1].toInt() and 255) shl 8) or
            ((data[o+2].toInt() and 255) shl 16) or
            ((data[o+3].toInt() and 255) shl 24)
        require(u32(4) == 2) { "Требуется glTF 2.0" }
        require(u32(8) == data.size) { "Повреждена длина GLB" }
        require(u32(12) in 1..(data.size - 20)) { "Нет JSON-чанка" }
        require(data[16] == 74.toByte() && data[17] == 83.toByte() &&
            data[18] == 79.toByte() && data[19] == 78.toByte()) { "Нет JSON-чанка" }
    }
}
