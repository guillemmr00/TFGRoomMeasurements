package edu.upf.aism.tfgroommeasurements

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.room.Room
import com.github.psambit9791.jdsp.filter.Butterworth
import com.google.android.material.bottomsheet.BottomSheetBehavior
import edu.upf.aism.tfgroommeasurements.databinding.ActivityMainBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.*
import java.lang.Math.min
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.random.Random

const val REQUEST_CODE = 200
class MainActivity : AppCompatActivity() {
    private var permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
    private var permissionGranted = false

    private lateinit var dirPath : String
    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var sweep: Sweep
    private lateinit var recording : ShortArray

    private lateinit var pinkNoise : ShortArray
    private var outLevel = 0.0

    private var filename = ""

    private lateinit var db : AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionGranted = ActivityCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED

        if(!permissionGranted)
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)

        dirPath = "${externalCacheDir?.absolutePath}"

        val builder = AlertDialog.Builder(this)
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.progress_dialog, null)
        builder.setView(view)
        builder.setCancelable(false)
        val dialog = builder.create()
        val alertTv = view.findViewById<TextView>(R.id.alertTV)


        db = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "roomMeasures"
        ).build()

        val simpledateFormat = SimpleDateFormat("dd-MM-yyyy_hh:mm:ss")
        val date = simpledateFormat.format(Date())
        filename = "roomMeasure$date"

        binding.bottomSheet.filenameInput.setText(filename)

        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet.bottomSheetSignalConfig)
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.isDraggable = false

        binding.bottomSheet.startFreqScroll.minValue = 20
        binding.bottomSheet.startFreqScroll.maxValue = 20000
        binding.bottomSheet.startFreqScroll.value = 100
        binding.bottomSheet.startFreqScroll.nestedScrollAxes
        binding.bottomSheet.endFreqScroll.minValue = 100
        binding.bottomSheet.endFreqScroll.maxValue = 25000
        binding.bottomSheet.endFreqScroll.value = 10000

        binding.bottomSheet.seekBarLevel.progress = (invDBFS(-6.00)*1000).toInt()
        binding.bottomSheet.sweepMode.selectButton(R.id.toggleBtnExpMode)
        binding.bottomSheet.toggleGroupSampleRate.selectButton(R.id.toggleBtn44hz)

        GlobalScope.launch{
            runOnUiThread { alertTv.text = "Creating Sweep..."
                dialog.show()}
            //sweep = Sweep()
            sweep = Sweep(
                44100,
                binding.bottomSheet.startFreqScroll.value.toDouble(),
                binding.bottomSheet.endFreqScroll.value.toDouble(),
                binding.bottomSheet.seekBarLenght.progress.toDouble(),
                binding.bottomSheet.seekBarLevel.progress/1000.0,
                //check if there is a button selected
                binding.bottomSheet.sweepMode.selectedButtons[0].text.lowercase()
            )
            pinkNoise = pinkNoise(binding.bottomSheet.seekBarLenght.progress.toDouble(),
                44100,
                Short.MAX_VALUE,
                binding.bottomSheet.startFreqScroll.value.toDouble(),
                binding.bottomSheet.endFreqScroll.value.toDouble(),
            )
            runOnUiThread { dialog.dismiss() }
        }

        binding.bottomSheet.seekBarLenght.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.bottomSheet.seekBarTv.setText("${progress.toDouble()} secs.")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.bottomSheet.seekBarLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.bottomSheet.levelBarTv.setText("${BigDecimal(dBFS(progress/1000.0)).setScale(2, RoundingMode.HALF_EVEN)} dBFS")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })


        binding.btnRecord.setOnClickListener {
            thread {
                runOnUiThread { alertTv.text = "Performing measurement..."
                dialog.show()}
                recording = performMeasurement(sweep.sweep)

                runOnUiThread { alertTv.text = "Inserting data into DB..."}
                //savePcmToFile2(recording, "$dirPath/recording.pcm")

                var roomMeasure = RoomMeasure(
                    binding.bottomSheet.filenameInput.text.toString(),
                    date,
                    binding.bottomSheet.textInputNotes.text.toString(),
                    sweep.sampleRate,
                    sweep.f0,
                    sweep.f1,
                    sweep.duration,
                    sweep.mode,
                    outLevel,
                    savePcmToFile(sweep.computeIR(recording), "$dirPath/${binding.bottomSheet.filenameInput.text.toString()}.pcm")
                )

                db.roomMeasureDao().insert(roomMeasure)



                runOnUiThread { dialog.dismiss() }
                var intent = Intent(this@MainActivity, Visualizer::class.java)

                intent.putExtra("filename", roomMeasure.filename)
                intent.putExtra("date", roomMeasure.date)
                intent.putExtra("notes", roomMeasure.notes)
                intent.putExtra("sampleRate", roomMeasure.sampleRate)
                intent.putExtra("f0", roomMeasure.f0)
                intent.putExtra("f1", roomMeasure.f1)
                intent.putExtra("duration", roomMeasure.duration)
                intent.putExtra("mode", roomMeasure.mode)
                intent.putExtra("amplitude", roomMeasure.outGain)
                intent.putExtra("impulseResponsePath", roomMeasure.impulseResponsePath)

                startActivity(intent)
            }
        }

        binding.btnSettings.setOnClickListener {
                bottomSheetBehavior.state=BottomSheetBehavior.STATE_EXPANDED
                binding.bottomSheetBG.visibility = View.VISIBLE
        }

        binding.bottomSheetBG.setOnClickListener {
            //dismiss()
        }

        binding.btnList.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        binding.bottomSheet.btnCancel.setOnClickListener{
            dismiss()
        }

        binding.bottomSheet.btnOk.setOnClickListener {
            dismiss()
            GlobalScope.launch{
                runOnUiThread {
                    alertTv.text = "Creating Sweep"
                    dialog.show()  }
                sweep = Sweep(
                    44100,
                    binding.bottomSheet.startFreqScroll.value.toDouble(),
                    binding.bottomSheet.endFreqScroll.value.toDouble(),
                    binding.bottomSheet.seekBarLenght.progress.toDouble(),
                    binding.bottomSheet.seekBarLevel.progress/1000.0,
                    //check if there is a button selected
                    binding.bottomSheet.sweepMode.selectedButtons[0].text.lowercase()
                )
                pinkNoise = pinkNoise(3.0,
                    44100,
                    ((binding.bottomSheet.seekBarLevel.progress/100.0)*Short.MAX_VALUE).toInt().toShort(),
                    binding.bottomSheet.startFreqScroll.value.toDouble(),
                    binding.bottomSheet.endFreqScroll.value.toDouble(),
                )
                runOnUiThread {
                    dialog.dismiss() }
            }
        }

        binding.bottomSheet.btnCloseBottomSheet.setOnClickListener {
            dismiss()
        }

        binding.btnCheckLevels.setOnClickListener {
            thread {
                runOnUiThread { alertTv.text = "Performing calibration..."
                    dialog.show()}
                outLevel = performCalibration(pinkNoise)
                val dbRms = 20 * Math.log10(outLevel / Short.MAX_VALUE)

                runOnUiThread { dialog.dismiss()
                if (dbRms>=-30.0){
                    binding.btnRecord.setImageResource(R.drawable.ic_record)
                    binding.btnRecord.isClickable = true
                    binding.levelTV.text = "${BigDecimal(dbRms).setScale(2, RoundingMode.HALF_EVEN)} dBFS"
                    binding.levelTV.setTextColor(getColor(R.color.white))

                } else{
                    binding.btnRecord.setImageResource(R.drawable.ic_record_disabled)
                    binding.btnRecord.isClickable = false
                    binding.levelTV.text = "${BigDecimal(dbRms).setScale(2, RoundingMode.HALF_EVEN)} dBFS"
                    binding.levelTV.setTextColor(getColor(R.color.orange))

                }}
            }
        }

        binding.btnRecord.isClickable = false

    }


    fun pinkNoise(duration: Double, sampleRate : Int, amplitude : Short, f0 : Double, f1 : Double): ShortArray {
        val length = (duration * sampleRate).toInt()
        val random = Random(System.currentTimeMillis())
        val result = DoubleArray(length)
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        var b3 = 0.0
        var b4 = 0.0
        var b5 = 0.0
        var b6 = 0.0

        for (i in 0 until length) {
            val white = random.nextDouble() * 2 - 1
            b0 = 0.99886 * b0 + white * 0.0555179
            b1 = 0.99332 * b1 + white * 0.0750759
            b2 = 0.96900 * b2 + white * 0.1538520
            b3 = 0.86650 * b3 + white * 0.3104856
            b4 = 0.55000 * b4 + white * 0.5329522
            b5 = -0.7616 * b5 - white * 0.0168980
            var noise = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
            noise *= 0.11
            result[i] = noise*amplitude
            b6 = white * 0.115926
        }
        val filter = Butterworth(sampleRate.toDouble())
        val filtNoise = filter.bandPassFilter(result, 4, f0, f1)
        val shortNoise = doubleArrayToShortArray(filtNoise)

        return shortNoise
    }

    @SuppressLint("MissingPermission")
    private fun performMeasurement(inputSweep: ShortArray) : ShortArray{
        if(!permissionGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
            return ShortArray(inputSweep.size)
        }


        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSizeShort = bufferSize / 2

        var recordData = ShortArray(bufferSizeShort)

        recording = shortArrayOf()

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager


        val audioTrack = AudioTrack.Builder().setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        val outDevices =  audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val inDevices =  audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        //audioTrack.setPreferredDevice(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)[0])


        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            audioFormat,
            bufferSize
        )

        audioTrack.play()
        audioRecord.startRecording()

        Thread.sleep(50)
        var read = 0
        while (read < inputSweep.size) {
            val trackShortRead = min(inputSweep.size - read, bufferSizeShort)
            audioTrack.write(inputSweep, read, trackShortRead)
            audioRecord.read(recordData, 0, trackShortRead)
            if (trackShortRead != recordData.size){
                recordData = recordData.slice(0 until trackShortRead).toShortArray()
            }
            recording+=recordData
            read += trackShortRead
        }

        audioRecord.stop()
        audioRecord.release()
        audioTrack.stop()
        audioTrack.release()

        return recording
    }

    @SuppressLint("MissingPermission")
    private fun performCalibration(pinkNoise: ShortArray) : Double {
        if(!permissionGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
            return 0.0
        }

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSizeShort = bufferSize / 2

        var recordData = ShortArray(bufferSizeShort)

        recording = shortArrayOf()

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = true
        audioManager.mode = AudioManager.MODE_NORMAL


        val audioTrack = AudioTrack.Builder().setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()



        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            audioFormat,
            bufferSize
        )
        audioTrack.play()
        audioRecord.startRecording()


        var read = 0
        var sumSquared = 0.0

        Thread.sleep(50)
        while (read < pinkNoise.size) {
            val trackShortRead = min(pinkNoise.size - read, bufferSizeShort)
            audioTrack.write(pinkNoise, read, trackShortRead)
            audioRecord.read(recordData, 0, trackShortRead)
            if (trackShortRead != recordData.size){
                recordData = recordData.slice(0 until trackShortRead).toShortArray()
            }
            read += trackShortRead

            for (i in 0 until trackShortRead){
                val sample = recordData[i].toDouble()
                sumSquared +=sample*sample
            }
        }

        audioRecord.stop()
        audioRecord.release()
        audioTrack.stop()
        audioTrack.release()

        val rms = sqrt(sumSquared / read)
        return rms
    }


    private fun dismiss(){
        binding.bottomSheetBG.visibility = View.GONE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        hideKeyboard(binding.bottomSheet.filenameInput)

    }

    private fun hideKeyboard(view : View){
        val imm : InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

    }


}