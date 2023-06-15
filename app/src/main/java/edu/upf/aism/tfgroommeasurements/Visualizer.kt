package edu.upf.aism.tfgroommeasurements

import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.core.view.isVisible
import com.anychart.AnyChart
import com.anychart.AnyChartView
import com.anychart.chart.common.dataentry.DataEntry
import com.anychart.chart.common.dataentry.ValueDataEntry
import com.anychart.enums.MarkerType
import com.github.psambit9791.jdsp.filter.Butterworth
import com.github.psambit9791.jdsp.transform.DiscreteFourier
import com.github.psambit9791.jdsp.transform.FastFourier
import edu.upf.aism.tfgroommeasurements.databinding.ActivityVisualizerBinding
import kotlinx.coroutines.*
import kotlin.math.log10
import java.util.concurrent.Executors
import kotlin.math.pow

class Visualizer : AppCompatActivity() {

    private lateinit var binding : ActivityVisualizerBinding

    private lateinit var filename : String
    private lateinit var date : String
    private lateinit var notes : String
    private var sampleRate = 0
    private var f0 = 0.0
    private var f1 = 0.0
    private var duration = 0.0
    private lateinit var mode : String
    private var amplitude = 0.0
    private lateinit var impulseResponse : DoubleArray

    private lateinit var irdb : DoubleArray
    private lateinit var freqResponse : DoubleArray
    private lateinit var freqs : DoubleArray
    private var plotMode = "ir"


    private lateinit var tArray : DoubleArray
    //private lateinit var schroeder : DoubleArray
    private lateinit var octaveParams : Array<DoubleArray>
    private lateinit var thirdOctaveParams : Array<DoubleArray>


    private var visualizerMode = "ir"


    private lateinit var alertTv : TextView
    private lateinit var dialog : AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisualizerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val builder = AlertDialog.Builder(this)
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.progress_dialog, null)
        builder.setView(view)
        builder.setCancelable(false)
        dialog = builder.create()
        alertTv = view.findViewById<TextView>(R.id.alertTV)


        val languages = resources.getStringArray(R.array.modes)
        val arrayAdapter = ArrayAdapter(this, R.layout.dropdown_item, languages)
        val autoCompleteTextView = findViewById<AutoCompleteTextView>(R.id.autocompleteTV)
        autoCompleteTextView.setAdapter(arrayAdapter)

        filename = intent.getStringExtra("filename")!!
        date = intent.getStringExtra("date")!!
        notes = intent.getStringExtra("notes")!!
        sampleRate = intent.getIntExtra("sampleRate", 44100)
        f0 = intent.getDoubleExtra("f0", 20.0)
        f1 = intent.getDoubleExtra("f1", 20000.0)
        duration = intent.getDoubleExtra("duration", 5.0)
        mode = intent.getStringExtra("mode")!!
        amplitude = intent.getDoubleExtra("amplitude", 1.0)
        val impulseResponsePath = intent.getStringExtra("impulseResponsePath")!!
        impulseResponse = loadPcmFromFile(impulseResponsePath)!!


        val computationContext = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

        /*CoroutineScope(Dispatchers.Main).launch{
            val time = measureTimeMillis { withContext(computationContext) {
                val ans = fft(impulseResponse)
                freqs = ans.first
                freqResponse = ans.second
                octaveParams = computeParameters(1.0)
                //thirdOctaveParams = computeParameters((1/3).toDouble())

            } }
            runOnUiThread{Toast.makeText(this@Visualizer, "Computations finished", Toast.LENGTH_LONG).show()}
            runOnUiThread { println("computations finished in :${time}") }
        }*/

        CoroutineScope(Dispatchers.IO).launch{ computeAll() }


        plot("ir")

        autoCompleteTextView.setOnItemClickListener { parent, view, position, id ->
            when (parent.getItemAtPosition(position).toString().lowercase()){
                "impulse resp." -> {
                    plotMode="ir"
                    binding.normToggleBtn.apply {
                        isVisible= true
                        isSelected = false
                    }
                    binding.smoothToggleBtn.apply {
                        isVisible= false
                        isSelected = false
                    }
                    plot("ir")
                }
                "frequency resp." -> {
                    plotMode="fr"
                    binding.normToggleBtn.apply {
                        isVisible= false
                        isSelected = false
                    }
                    binding.smoothToggleBtn.apply {
                        isVisible= true
                        isSelected = false
                    }
                    plot("fr")
                }
                "reverb. times" -> {
                    plotMode="rt"
                    binding.normToggleBtn.apply {
                        isVisible= false
                        isSelected = false
                    }
                    binding.smoothToggleBtn.apply {
                        isVisible= false
                        isSelected = false
                    }
                    plot("rt")
                }
                "clarity" -> {
                    plotMode="clarity"
                    binding.normToggleBtn.apply {
                        isVisible= false
                        isSelected = false
                    }
                    binding.smoothToggleBtn.apply {
                        isVisible= false
                        isSelected = false
                    }
                    plot("clarity")
                }
                "definition" -> {
                    plotMode="definition"
                    binding.normToggleBtn.apply {
                        isVisible= false
                        isSelected = false
                    }
                    binding.smoothToggleBtn.apply {
                        isVisible= false
                        isSelected = false
                    }
                    plot("definition")
                }
                "dtt & cte" -> {
                    plotMode="dtt & cte"
                    binding.normToggleBtn.apply {
                        isVisible= false
                        isSelected = false
                    }
                    binding.smoothToggleBtn.apply {
                        isVisible= false
                        isSelected = false
                    }
                    plot("dtt & cte")
                }
            }
        }

        binding.normToggleBtn.setOnClickListener {
            if (binding.normToggleBtn.isChecked){
            plot(plotMode, true)
        }else{
            plot(plotMode, false)
        } }


        binding.backBtn.setOnClickListener {
            onBackPressed()
        }
        binding.smoothToggleBtn.isVisible = false

    }

    private fun plot(mode: String, norm : Boolean=false){
        val chartView = findViewById<AnyChartView>(R.id.chart_view)


        val line = AnyChart.line()
        line.removeAllSeries()
        tArray = (0 until impulseResponse.size).map { it.toDouble() / sampleRate }.toDoubleArray()

        if (mode == "ir"){
            val data = ArrayList<DataEntry>()
            if (norm){

                for (i in 0 until impulseResponse.size){
                    data.add(ValueDataEntry(tArray[i], gainToDecibels(impulseResponse[i]*amplitude)))
                }


                val hor = ArrayList<DataEntry>()
                hor.add(ValueDataEntry(0, amplitude))
                hor.add(ValueDataEntry(tArray.last(), amplitude))
                val horLine = line.line(hor)
                horLine.markers().enabled(false)

            }
            else{
                val max = findIndexOfMaxValue(impulseResponse)
                val start = max-(0.1*44100).toInt()
                val end = max + (0.4*44100).toInt()
                for (i in start until end){
                    data.add(ValueDataEntry(tArray[i], impulseResponse[i]))
                }
            }

            line.line(data)

        }

        else if(mode == "fr"){
            val data = ArrayList<DataEntry>()
            for (i in f0.toInt() until f1.toInt()){
                data.add(ValueDataEntry(freqs[i], freqResponse[i]))
            }
            line.line(data)
        }

        else if(mode=="rt"){
            val edt = ArrayList<DataEntry>()
            val t20 = ArrayList<DataEntry>()
            val t30 = ArrayList<DataEntry>()
            val t60 = ArrayList<DataEntry>()

            for (i in 0 until octaveParams[0].size){
                edt.add(ValueDataEntry(octaveParams[0][i], octaveParams[1][i]))
                t20.add(ValueDataEntry(octaveParams[0][i], octaveParams[2][i]))
                t30.add(ValueDataEntry(octaveParams[0][i], octaveParams[3][i]))
                t60.add(ValueDataEntry(octaveParams[0][i], octaveParams[4][i]))
            }

            val edtLine = line.line(edt)
            edtLine.markers().enabled(true).type(MarkerType.CIRCLE).fill("#000500")
            val t20Line = line.line(t20)
            t20Line.markers().enabled(true).type(MarkerType.CIRCLE).fill("#003000")
            val t30Line = line.line(t30)
            t30Line.markers().enabled(true).type(MarkerType.CIRCLE).fill("#030000")
            val t60Line = line.line(t60)
            t60Line.markers().enabled(true).type(MarkerType.CIRCLE).fill("#090000")
        }

        else if(mode =="clarity"){
            val c50 = ArrayList<DataEntry>()
            val c80 = ArrayList<DataEntry>()

            for (i in 0 until octaveParams[0].size) {
                c50.add(ValueDataEntry(octaveParams[0][i], octaveParams[5][i]))
                c80.add(ValueDataEntry(octaveParams[0][i], octaveParams[6][i]))
            }

            val c50Line = line.line(c50)
            c50Line.markers().enabled(true).type(MarkerType.CIRCLE).fill("#000500")
            val c80Line = line.line(c80)
            c80Line.markers().enabled(true).type(MarkerType.CIRCLE).fill("#003000")
        }

        else if(mode =="definition"){
            val d50 = ArrayList<DataEntry>()
            for (i in 0 until octaveParams[0].size) {
                d50.add(ValueDataEntry(octaveParams[0][i], octaveParams[7][i]))
            }

            val d50Line = line.line(d50)
            d50Line.markers().enabled(true).type(MarkerType.CIRCLE).fill("#000500")

        }

        else if(mode =="dtt_cte"){}

        chartView.setChart(line)



    }



    suspend fun computeParameters(octave : Double = 1.0) = coroutineScope{
        //octave can take either value 1 or 1/3

        var freqs = doubleArrayOf()
        var edt = doubleArrayOf()
        var t20 = doubleArrayOf()
        var t30 = doubleArrayOf()
        var t60 = doubleArrayOf()
        var c50 = doubleArrayOf()
        var c80 = doubleArrayOf()
        var d50 = doubleArrayOf()

        val ir = impulseResponse

        var fCenter = 15.6
        while(fCenter<f1){
            val fLower = fCenter / (2.0.pow(0.5).pow(octave))
            val fUpper = fCenter * (2.0.pow(0.5).pow(octave))
            if (fLower>=f0 && fUpper<=f1){
                val filter = Butterworth(sampleRate.toDouble())
                val band = filter.bandPassFilter(ir, 4, fLower, fUpper)

                val peak = findIndexOfMaxValue(band)
                val schroeder = computeSchroeder(band)
                freqs += fCenter
                edt += async{computeEdt(schroeder, peak)}.await()
                t20 += async{computeT20(schroeder, peak)}.await()
                t30 += async{computeT30(schroeder, peak)}.await()
                t60 += async{computeT60(schroeder, peak)}.await()
                //c50 += async{computeC50(band, peak)}.await()
                //c80 += async{computeC80(band, peak)}.await()
                //d50 += async{computeD50(band, peak)}.await()

            }
            fCenter *= 2.0.pow(octave)
        }
        octaveParams = arrayOf(freqs, edt, t20, t30, t60, c50, c80, d50)
    }


    private fun computeSchroeder(ir: DoubleArray): DoubleArray {
        val reversedIrEnergy = DoubleArray(ir.size) { ir[ir.size - it - 1] * ir[ir.size - it - 1] }
        val edc = DoubleArray(reversedIrEnergy.size) { 0.0 }
        var sum = 0.0
        for (i in reversedIrEnergy.indices) {
            sum += reversedIrEnergy[i]
            edc[edc.size - i - 1] = sum
        }
        val maxEdc = edc.maxOrNull() ?: 1.0 // to avoid division by zero
        val schroeder = DoubleArray(edc.size)
        for (i in schroeder.indices) {
            schroeder[i] = 10 * log10(edc[i] / maxEdc)
        }
        return schroeder // normalized
    }

    private fun computeEdt(schroeder : DoubleArray, peak : Int) : Double {
        val edt = try{
            peak + schroeder.drop(peak).withIndex().firstOrNull() { it.value < schroeder[peak] - 10 }?.index!!
        }catch(e : java.lang.NullPointerException){
            0
        }

        return (edt - peak) / sampleRate.toDouble()
    }

    private fun computeT20(schroeder : DoubleArray, peak : Int): Double {
        //Return edt from schroeder
        var t20 = 0
        var startIdx = 0
        try{
            startIdx = peak + schroeder.drop(peak).withIndex().firstOrNull() { it.value < schroeder[peak] - 5 }?.index!!
            t20 = startIdx + schroeder.drop(startIdx).withIndex().firstOrNull() { it.value < schroeder[startIdx] - 20 }?.index!!}
        catch(_: java.lang.NullPointerException){}

        return (t20 - startIdx) / sampleRate.toDouble()
    }

    private fun computeT30(schroeder : DoubleArray, peak : Int): Double {
        //Return edt from schroeder
        var t30 = 0
        var startIdx = 0
        try{startIdx = peak + schroeder.drop(peak).withIndex().firstOrNull() { it.value < schroeder[peak] - 5 }?.index!!
            t30 = startIdx + schroeder.drop(startIdx).withIndex().firstOrNull() { it.value < schroeder[startIdx] - 30 }?.index!!}
        catch(_: java.lang.NullPointerException){}
        return (t30 - startIdx) / sampleRate.toDouble()
    }

    private fun computeT60(schroeder : DoubleArray, peak : Int): Double {
        //Return index of edt from schroeder
        var t60 = 0
        var startIdx = 0
        try{startIdx = peak + schroeder.drop(peak).withIndex().firstOrNull() { it.value < schroeder[peak] - 5 }?.index!!
            t60 = try {
                startIdx + schroeder.drop(startIdx).withIndex().firstOrNull() { it.value < schroeder[startIdx] - 60 }?.index!!
            } catch (e: IndexOutOfBoundsException) {
                val t30 = computeT30(schroeder, peak)
                startIdx + ((t30.toInt() - startIdx) * 2)
            }}
        catch (_: java.lang.NullPointerException){}

        return (t60 - startIdx) / sampleRate.toDouble()
    }

    private fun computeC50(ir: DoubleArray, peak : Int) : Double {
        //Return edt from schroeder
        val earlyEnergy = ir.slice(peak until peak+(0.5*sampleRate).toInt()).map{it.pow(2)}.sum()
        val lateEnergy = ir.slice(peak+(0.5*sampleRate).toInt() until ir.size).map{it.pow(2)}.sum()

        return 10* log10(earlyEnergy / lateEnergy)
    }

    private fun computeC80(ir: DoubleArray, peak : Int) : Double {
        //Return edt from schroeder
        val earlyEnergy = ir.slice(peak until peak+(0.8*sampleRate).toInt()).map{it.pow(2)}.sum()
        val lateEnergy = ir.slice(peak+(0.8*sampleRate).toInt() until ir.size).map{it.pow(2)}.sum()

        return 10* log10(earlyEnergy / lateEnergy)
    }

    private fun computeD50(ir: DoubleArray, peak : Int) : Double {
        //Return edt from schroeder
        val earlyEnergy = ir.slice(peak until peak+(0.5*sampleRate).toInt()).map{it.pow(2)}.sum()
        val lateEnergy = ir.slice(peak until ir.size).map{it.pow(2)}.sum()

        return earlyEnergy / lateEnergy
    }

    private fun computeDrr(ir : DoubleArray, peak : Int): Double{
        val c = (0.025*sampleRate).toInt()
        val direct = ir.slice(peak-c until peak+c).map { it.pow(2) }.sum()
        val rev = ir.slice(peak+c until ir.size).map { it.pow(2) }.sum()
        return 10* log10(direct/rev)
    }

    private fun computeCte(ir : DoubleArray, peak : Int): Double{
        val c = (0.025*sampleRate).toInt()
        val te = (0.05*sampleRate).toInt()
        val direct = ir.slice(peak-c until peak+te).map { it.pow(2) }.sum()
        val rev = ir.slice(peak+te until ir.size).map { it.pow(2) }.sum()
        return 10* log10(direct/rev)
    }

    suspend fun fft2() = coroutineScope{
        val fftObj = DiscreteFourier(impulseResponse.sliceArray(0 until 44100))
        fftObj.transform()
        freqs = fftObj.getFFTFreq(44100, true)
        val fftCompl = fftObj.getComplex(true)
        val divisor = 2 / impulseResponse.size
        val s_mag = DoubleArray(fftCompl.size){ (fftCompl[it].abs()) * divisor}
        freqResponse = gainToDecibels(s_mag)
    }



    suspend fun fft() = coroutineScope{
        val fftObj = FastFourier(impulseResponse)
        fftObj.transform()
        val frequencies = fftObj.getFFTFreq(44100, true)
        val fftMag = gainToDecibels(fftObj.getMagnitude(true))
        val step = 44100.0/(frequencies.size*2)
        val startBin = (f0/step).toInt()
        val finishBin = (f1/step).toInt()
        freqs = frequencies.sliceArray(startBin .. finishBin)
        freqResponse= fftMag.sliceArray(startBin .. finishBin)

    }

    suspend fun awaitAll(vararg blocks: suspend () -> Unit) = coroutineScope {
        blocks.forEach {
            launch { it() }
        }
    }

    suspend fun computeAll() = coroutineScope {
        awaitAll(
            ::computeParameters,
            ::fft
        )
    }
}