package edu.upf.aism.tfgroommeasurements

import android.media.*
import com.github.psambit9791.jdsp.signal.Convolution
import kotlinx.coroutines.*
import java.lang.Math.PI
import java.lang.Math.sin
import kotlin.math.exp
import kotlin.math.log


class Sweep(sampleRate : Int = 44100,
            f0: Double = 100.0,
            f1: Double = 10000.0,
            duration: Double = 3.0,
            gain: Double = 1.0,
            mode: String = "exponential") {

    val sampleRate = sampleRate
    val f0 = f0
    val f1 = f1
    val duration = duration
    val mode = mode
    val numSamples = (sampleRate * duration).toInt()
    val sweep = ShortArray(numSamples)
    val amplitude = gain*Short.MAX_VALUE
    var inverseFilter = ShortArray(numSamples)
    val fade =  fadeLog(numSamples, (0.5*sampleRate).toInt())


    init {
        if (mode == "linear"){
            val frequencyIncrement = (f1 - f0) / numSamples
            var frequency = f0

            for (i in 0 until numSamples) {
                val sampleValue = amplitude * sin(2 * PI * frequency * i / sampleRate)
                sweep[i] = (sampleValue*fade[i]).toInt().toShort()
                frequency += frequencyIncrement
            }
            inverseFilter = sweep.reversedArray()
        }
        else if (mode=="exponential"){
            val t = DoubleArray((duration * sampleRate).toInt()) { it.toDouble() / sampleRate }
            val R = log(f1 / f0, Math.E)
            var x = DoubleArray(t.size) { sin((2 * PI * f0 * duration / R) * (exp(t[it] * R / duration) - 1)) }
            val k =DoubleArray(t.size) { exp(t[it] * R / duration)}

            for (i in x.indices){
                sweep[i] = (amplitude * x[i] * fade[i]).toInt().toShort()
            }
            val reversedSweep = sweep.reversedArray()
            for (i in x.indices){
                inverseFilter[i] = (reversedSweep[i] / k[i]).toInt().toShort()
                }
            }



        }

    fun computeIR2(recording : ShortArray) : ShortArray{
        val normalizedRecording = shortArrayToDoubleArray(recording)//normalizeShortArray(recording)
        val normalizedInverseFilter = shortArrayToDoubleArray(this.inverseFilter)//normalizeShortArray(this.inverseFilter)

        // Perform the convolution using an optimized library or algorithm
        val conv = Convolution(normalizedRecording, normalizedInverseFilter)
        val result = conv.convolve("same")

        val normalizedResult = normalizeDoubleArray(result)
        return doubleArrayToShortArray(normalizedResult)
    }

    fun computeIR(recording : ShortArray) : DoubleArray{
        val normalizedRecording = shortArrayToDoubleArray(recording)//normalizeShortArray(recording)
        val normalizedInverseFilter = shortArrayToDoubleArray(this.inverseFilter)//normalizeShortArray(this.inverseFilter)

        // Perform the convolution using an optimized library or algorithm
        val conv = Convolution(normalizedRecording, normalizedInverseFilter)
        val result = conv.convolve("same")

        //val normalizedResult = normalizeDoubleArray(result)

        val normalizedResult = normalizeDoubleArray(result.sliceArray((findIndexOfMaxValue(result)-0.1*44100).toInt() until result.size))

        return normalizedResult
    }

}



