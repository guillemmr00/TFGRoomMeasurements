package edu.upf.aism.tfgroommeasurements

import com.github.psambit9791.jdsp.transform.DiscreteFourier
import com.github.psambit9791.jdsp.transform.FastFourier
import java.io.*
import java.lang.Math.pow
import kotlin.math.*


fun bit8_to16bit(bit8_low : Int, bit8_high : Int) : Short{
    val lowByteDecimal = bit8_low and 0xff
    val highByteDecimal = bit8_high and 0xff // example high 8-bit value in decimal
    // example low 8-bit value in decimal

    return ((highByteDecimal shl 8) or lowByteDecimal).toShort()

}

fun convolve(a: ShortArray, b: ShortArray, mode: String = "same"): ShortArray {
    val m = a.size
    val n = b.size

    var result = ShortArray(m+n-1)
    for (i in 0 until m+n-1) {
        var sum = 0.0
        for (j in max(0, i - n + 1) until min(i + 1, m)) {
            sum += a[j] * b[i - j]
        }
        result[i] = sum.toInt().toShort()
    }
    result = when (mode) {
        "full" -> result
        "same" -> takeMiddle(result, max(m, n))
        "valid" -> takeMiddle(result, max(m, n) - min(m, n) + 1)
        else -> result
    }

    return result
}

fun convolve(a: DoubleArray, b: DoubleArray, mode: String = "same"): DoubleArray {
    val m = a.size
    val n = b.size

    var result = DoubleArray(m+n-1)
    for (i in 0 until m+n-1) {
        var sum = 0.0
        for (j in max(0, i - n + 1) until min(i + 1, m)) {
            sum += a[j] * b[i - j]
        }
        result[i] = sum
    }
    result = when (mode) {
        "full" -> result
        "same" -> takeMiddle(result, max(m, n))
        "valid" -> takeMiddle(result, max(m, n) - min(m, n) + 1)
        else -> result
    }

    return result
}


fun takeMiddle(a: ShortArray, x: Int) : ShortArray{
    val start = (a.size - x) / 2
    val end = start + x
    val slice = a.slice(start until end)
    return slice.toShortArray() // Output: [4, 5, 6]
}

fun takeMiddle(a: DoubleArray, x: Int) : DoubleArray{
    val start = (a.size - x) / 2
    val end = start + x
    val slice = a.slice(start until end)
    return slice.toDoubleArray() // Output: [4, 5, 6]
}


fun decibelsToGain(decibels : Double, dbType : String ="dBFS") : Double{
    val factor = if (dbType=="dBu"){
        0.775
    } else if(dbType == "dBFS" || dbType=="dBV"){
        1.0
    } else {
        1.0
    }
    return Math.pow(10.0, decibels / 20.0) * factor
}

fun decibelsToGain(decibels: DoubleArray, dbType: String = "dBFS"): DoubleArray {
    val factor = if (dbType == "dBu") {
        0.775
    } else if (dbType == "dBFS" || dbType == "dBV") {
        1.0
    } else {
        1.0
    }
    return DoubleArray(decibels.size) { Math.pow(10.0, decibels[it] / 20.0) * factor }
}

fun gainToDecibels(gain : Double) : Double{

    return 20.0 * log10(gain/Short.MAX_VALUE)
}

fun gainToDecibels(gain : DoubleArray) : DoubleArray{
    var peak = 0.0
    for (i in gain.indices) {
        if (gain[i] > peak) {
            peak = gain[i]
        }
    }
    return DoubleArray(gain.size){20.0 * log10(gain[it]/peak)}
}

fun dBFS(gain : Double) : Double{
    return 20* log10(gain/1.0)
}

fun invDBFS(db : Double) : Double{
    return Math.pow(10.0, db/20)
}

fun findIndexOfMaxValue(numbers: DoubleArray): Int {
    var maxIndex = -1
    var maxValue = Double.MIN_VALUE

    for (i in numbers.indices) {
        if (numbers[i] > maxValue) {
            maxValue = numbers[i]
            maxIndex = i
        }
    }

    return maxIndex
}

fun normalizeShortArray(array: ShortArray): DoubleArray {
    val max = array.maxOrNull()?.toDouble() ?: 1.0 // Find the maximum value, default to 1 if the array is empty
    val normalizedArray = DoubleArray(array.size)
    val maxInv = 1.0 / max // Calculate the inverse of the maximum value outside the loop for efficiency

    for (i in array.indices) {
        normalizedArray[i] = array[i].toDouble() * maxInv // Multiply each element by the inverse of the maximum value to normalize
    }

    return normalizedArray
}

fun normalizeDoubleArray(array: DoubleArray): DoubleArray {
    val max = array.maxOrNull() ?: 1.0 // Find the maximum value, default to 1 if the array is empty
    val normalizedArray = DoubleArray(array.size)
    val maxInv = 1.0 / max // Calculate the inverse of the maximum value outside the loop for efficiency

    for (i in array.indices) {
        normalizedArray[i] = array[i] * maxInv // Multiply each element by the inverse of the maximum value to normalize
    }

    return normalizedArray
}

fun doubleArrayToShortArray(doubleArray: DoubleArray): ShortArray {
    val shortArray = ShortArray(doubleArray.size)
    for (i in doubleArray.indices) {
        val value = doubleArray[i].toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        shortArray[i] = value.toShort()
    }
    return shortArray
}

fun shortArrayToDoubleArray(shortArray: ShortArray): DoubleArray {
    val doubleArray = DoubleArray(shortArray.size)
    for (i in shortArray.indices) {
        doubleArray[i] = shortArray[i].toDouble()
    }
    return doubleArray
}

fun savePcmToFile2(pcmData: ShortArray, filePath: String) : String{
    var filePathDefinitive = filePath
    var file = File(filePath)
    var counter = 1
    while (file.exists()){
        val fileName = file.nameWithoutExtension
        val fileExtension = file.extension
        val newFileName = "$fileName-$counter.$fileExtension"
        val parentDirectory = file.parent
        filePathDefinitive = "$parentDirectory/$newFileName"

        file = File(filePathDefinitive)
        counter++
    }

    val outputStream = FileOutputStream(file)
    val dataOutputStream = DataOutputStream(outputStream)

    try {
        for (sample in pcmData) {

            dataOutputStream.writeShort(sample.toInt())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        dataOutputStream.close()
    }
    return filePathDefinitive
}

fun loadPcmFromFile2(filePath: String): ShortArray? {
    val file = File(filePath)
    if (!file.exists()) {
        println("File does not exist.")
        return null
    }

    val inputStream = FileInputStream(file)
    val dataInputStream = DataInputStream(inputStream)
    val pcmData = mutableListOf<Short>()

    try {
        while (dataInputStream.available() > 0) {
            val sample = dataInputStream.readShort()
            pcmData.add(sample)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        dataInputStream.close()
    }

    return pcmData.toShortArray()
}

fun savePcmToFile(pcmData: DoubleArray, filePath: String) : String{
    var filePathDefinitive = filePath
    var file = File(filePath)
    var counter = 1
    while (file.exists()){
        val fileName = file.nameWithoutExtension
        val fileExtension = file.extension
        val newFileName = "$fileName-$counter.$fileExtension"
        val parentDirectory = file.parent
        filePathDefinitive = "$parentDirectory/$newFileName"

        file = File(filePathDefinitive)
        counter++
    }

    val outputStream = FileOutputStream(file)
    val dataOutputStream = DataOutputStream(outputStream)

    try {
        for (sample in pcmData) {

            dataOutputStream.writeDouble(sample)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        dataOutputStream.close()
    }
    return filePathDefinitive
}

fun loadPcmFromFile(filePath: String): DoubleArray? {
    val file = File(filePath)
    if (!file.exists()) {
        println("File does not exist.")
        return null
    }

    val inputStream = FileInputStream(file)
    val dataInputStream = DataInputStream(inputStream)
    val pcmData = mutableListOf<Double>()

    try {
        while (dataInputStream.available() > 0) {
            val sample = dataInputStream.readDouble()
            pcmData.add(sample)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        dataInputStream.close()
    }

    return pcmData.toDoubleArray()
}

fun fadeLin(size : Int, fadeInEndSample : Int) : DoubleArray{

    val fadeOutStartSample = size - fadeInEndSample
    val array = DoubleArray(size)

    for (i in 0 until size) {
        val value = when {
            i < fadeInEndSample -> i.toDouble() / fadeInEndSample.toDouble()
            i >= fadeOutStartSample -> (size - i).toDouble() / (size - fadeOutStartSample).toDouble()
            else -> 1.0
        }
        array[i] = value
    }

    return array
}

fun fadeLog(size : Int, fadeInEndSample : Int) : DoubleArray{

    val fadeOutStartSample = size - fadeInEndSample
    val array = DoubleArray(size)

    for (i in 0 until size) {
        val value = when {
            i < fadeInEndSample ->1.0 - 10.0.pow(-i.toDouble() / fadeInEndSample.toDouble())
            i >= fadeOutStartSample ->1.0 - 10.0.pow(-(size - i).toDouble() / (size - fadeOutStartSample).toDouble())
            else -> 1.0
        }
        array[i] = value
    }

    return array
}

fun signalRms(signal : DoubleArray) : Double {
    val sigSquared = DoubleArray(signal.size){signal[it]*signal[it]}
    val meanSquared = sigSquared.sum()/signal.size
    return sqrt(meanSquared)
}

fun signalRms(signal : ShortArray) : Double {
    val sigSquared = DoubleArray(signal.size){signal[it].toDouble()*signal[it]}
    val meanSquared = sigSquared.sum()/signal.size
    return sqrt(meanSquared)
}

fun scaleSignal(signal : DoubleArray, outGain : Double) : Double{
    val energy = DoubleArray(signal.size){pow(signal[it], 2.0)}.sum()
    return sqrt((signal.size*pow(outGain, 2.0))/energy)
}