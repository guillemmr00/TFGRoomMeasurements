package edu.upf.aism.tfgroommeasurements

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromShortArray(value: ShortArray?): String? {
        if (value == null || value.isEmpty()) return null

        val result = StringBuilder(value.size * 6) // Assuming an average of 6 characters per element
        val lastIndex = value.size - 1

        for (i in 0 until lastIndex) {
            result.append(value[i])
            result.append(',')
        }

        result.append(value[lastIndex])

        return result.toString()
    }

    @TypeConverter
    fun toShortArray(value: String?): ShortArray? {
        if (value == null || value.isEmpty()) return null

        val elements = value.split(",").toTypedArray()
        val shortArray = ShortArray(elements.size)

        for (i in elements.indices) {
            shortArray[i] = elements[i].toShort()
        }

        return shortArray
    }
}
