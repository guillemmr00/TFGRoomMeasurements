package edu.upf.aism.tfgroommeasurements

import android.app.AlertDialog
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.isVisible
import com.anychart.AnyChart
import com.anychart.AnyChartView
import com.anychart.chart.common.dataentry.DataEntry
import com.anychart.chart.common.dataentry.ValueDataEntry
import com.anychart.enums.MarkerType
import com.github.psambit9791.jdsp.filter.Butterworth
import com.github.psambit9791.jdsp.transform.FastFourier
import com.google.android.material.bottomsheet.BottomSheetBehavior
import edu.upf.aism.tfgroommeasurements.databinding.ActivityVisualizerBinding
import kotlinx.coroutines.*
import kotlin.math.log10
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
    private lateinit var octaveParams : Array<DoubleArray>
    private lateinit var thirdOctaveParams : Array<DoubleArray>

    private lateinit var alertTv : TextView
    private lateinit var dialog : AlertDialog


    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

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

        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheetGraph.bottomSheetGraphInfo)
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.isDraggable = false


        val modes = resources.getStringArray(R.array.modes)
        val arrayAdapter = ArrayAdapter(this, R.layout.dropdown_item, modes)
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


        CoroutineScope(Dispatchers.IO).launch{ computeAll()
            runOnUiThread{Toast.makeText(this@Visualizer, "Computations finished", Toast.LENGTH_LONG).show()}
        }

        binding.bottomSheetGraph.graphFilename.setText("${filename}")
        binding.bottomSheetGraph.tvDate.setText("Date: ${date.replace("_", " ").replace("-", "/")}")
        binding.bottomSheetGraph.tvMode.setText("Mode: ${mode}")
        binding.bottomSheetGraph.tvDuration.setText("Duration: ${duration} seconds")
        binding.bottomSheetGraph.tvStartFreq.setText("Starting Frequency: ${f0} Hz")
        binding.bottomSheetGraph.tvEndFreq.setText("Ending Frequency: ${f1} Hz")
        binding.bottomSheetGraph.textInputNotes.setText("${notes}")


        Thread.sleep(1000)
        plot("rt", third = true)

        autoCompleteTextView.setOnItemClickListener { parent, view, position, id ->
            val selectedMode = parent.getItemAtPosition(position).toString().lowercase()
            binding.normToggleBtn.isVisible = false
            binding.smoothToggleBtn.isVisible = false
            binding.normToggleBtn.isSelected = false
            binding.smoothToggleBtn.isSelected = false
            binding.octaveConfigLayout.isVisible = false
            binding.thirdOctaveSwitch.isChecked = false

            when(selectedMode){
                "impulse resp." -> {
                    plotMode="ir"
                    binding.normToggleBtn.isVisible = true
                }
                "frequency resp." -> {
                    plotMode="fr"
                    binding.smoothToggleBtn.isVisible= true
                }
                "reverb. times" -> {
                    plotMode="rt"
                    binding.octaveConfigLayout.isVisible= true
                }
                "clarity" -> {
                    plotMode="clarity"
                    binding.octaveConfigLayout.isVisible = true
                }
                "definition" -> {
                    plotMode="definition"
                    binding.octaveConfigLayout.isVisible = true
                }
                "dtt & cte" -> {
                    plotMode="dtt & cte"
                }
            }
            plot(plotMode)
        }

        binding.normToggleBtn.setOnClickListener {
            if (binding.normToggleBtn.isChecked){
            plot(plotMode, false)
        }else{
            plot(plotMode, true)
        } }


        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        binding.thirdOctaveSwitch.setOnClickListener{
            if(binding.normToggleBtn.isChecked){
                plot(plotMode, third=true)
            }
            else{
                plot(plotMode)
            }
        }

        binding.btnInfo.setOnClickListener {
            bottomSheetBehavior.state=BottomSheetBehavior.STATE_EXPANDED
            binding.bottomSheetBGGraph.visibility = View.VISIBLE
        }

        binding.bottomSheetGraph.btnCloseBottomSheetGraph.setOnClickListener {
            dismiss()
        }
        binding.bottomSheetGraph.btnOkGraph.setOnClickListener {
            dismiss()
        }
        binding.bottomSheetGraph.btnCancelGraph.setOnClickListener {
            dismiss()
        }

        binding.smoothToggleBtn.isVisible = false
        binding.octaveConfigLayout.isVisible = false

    }

    private fun plot(mode: String, norm : Boolean=false, smooth: Boolean = false, third : Boolean = false){
        val chartView = findViewById<AnyChartView>(R.id.chart_view)

        val line = AnyChart.line()

        if (mode == "ir"){
            val data = ArrayList<DataEntry>()
            tArray = (0 until impulseResponse.size).map { it.toDouble() / sampleRate }.toDoubleArray()

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

            val irLine = line.line(data)
            irLine.name("IR")

        }

        else if(mode == "fr"){
            val data = ArrayList<DataEntry>()
            for (i in f0.toInt() until f1.toInt()){
                data.add(ValueDataEntry(freqs[i], freqResponse[i]))
            }
            val frLine = line.line(data)
            frLine.name("FR")
        }

        else if(mode=="rt"){
            val edt = ArrayList<DataEntry>()
            val t20 = ArrayList<DataEntry>()
            val t30 = ArrayList<DataEntry>()
            val t60 = ArrayList<DataEntry>()

            if (third){
                for (i in 0 until thirdOctaveParams[0].size) {
                    edt.add(ValueDataEntry(thirdOctaveParams[0][i], thirdOctaveParams[1][i]))
                    t20.add(ValueDataEntry(thirdOctaveParams[0][i], thirdOctaveParams[2][i]))
                    t30.add(ValueDataEntry(thirdOctaveParams[0][i], thirdOctaveParams[3][i]))
                    t60.add(ValueDataEntry(thirdOctaveParams[0][i], thirdOctaveParams[4][i]))
                }
            } else{
                for (i in 0 until octaveParams[0].size) {
                    edt.add(ValueDataEntry(octaveParams[0][i], octaveParams[1][i]))
                    t20.add(ValueDataEntry(octaveParams[0][i], octaveParams[2][i]))
                    t30.add(ValueDataEntry(octaveParams[0][i], octaveParams[3][i]))
                    t60.add(ValueDataEntry(octaveParams[0][i], octaveParams[4][i]))
                }
            }

            val edtLine = line.line(edt)
            edtLine.name("EDT")
            edtLine.markers().enabled(true).type(MarkerType.CIRCLE).fill("#000500")
            val t20Line = line.line(t20)
            t20Line.name("RT20")
            t20Line.markers().enabled(true).type(MarkerType.CIRCLE).fill("#003000")
            val t30Line = line.line(t30)
            t30Line.name("RT30")
            t30Line.markers().enabled(true).type(MarkerType.CIRCLE).fill("#030000")
            val t60Line = line.line(t60)
            t60Line.name("RT60")
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
            c50Line.name("C50")
            c50Line.markers().enabled(true).type(MarkerType.CIRCLE).fill("#000500")
            val c80Line = line.line(c80)
            c80Line.name("C80")
            c80Line.markers().enabled(true).type(MarkerType.CIRCLE).fill("#003000")
        }

        else if(mode =="definition"){
            val d50 = ArrayList<DataEntry>()
            for (i in 0 until octaveParams[0].size) {
                d50.add(ValueDataEntry(octaveParams[0][i], octaveParams[7][i]))
            }

            val d50Line = line.line(d50)
            d50Line.name("D50")
            d50Line.markers().enabled(true).type(MarkerType.CIRCLE).fill("#000500")

        }

        else if(mode =="dtt_cte"){}

        line.legend().enabled(true);
        line.legend().fontSize(13);
        line.legend().padding(0, 0, 10, 0);

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

    suspend fun computeParametersThird(octave : Double = (1.0/3.0).toDouble()) = coroutineScope{
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
        thirdOctaveParams = arrayOf(freqs, edt, t20, t30, t60, c50, c80, d50)
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
            ::fft,
            ::computeParametersThird
        )
    }

    private fun dismiss(){
        binding.bottomSheetBGGraph.visibility = View.GONE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        hideKeyboard(binding.bottomSheetGraph.graphFilename)

    }
    private fun hideKeyboard(view : View){
        val imm : InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

    }
}