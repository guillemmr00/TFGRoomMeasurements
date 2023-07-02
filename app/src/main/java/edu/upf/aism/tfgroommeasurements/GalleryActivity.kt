package edu.upf.aism.tfgroommeasurements

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GalleryActivity : AppCompatActivity(), OnItemClickListener {

    private lateinit var measures: ArrayList<RoomMeasure>
    private lateinit var mAdapter: Adapter
    private lateinit var db: AppDatabase
    private lateinit var toolbar : MaterialToolbar

    private var allChecked = false

    private lateinit var editBar : View
    private lateinit var btnClose : ImageButton
    private lateinit var btnSelectAll : ImageButton

    private lateinit var searchInput : TextInputEditText
    private lateinit var bottomSheet: LinearLayout
    private lateinit var bottomSheetBehavior : BottomSheetBehavior<LinearLayout>

    private lateinit var btnRename : ImageButton
    private lateinit var btnDelete : ImageButton
    private lateinit var tvRename : TextView
    private lateinit var tvDelete : TextView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)


        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        btnRename = findViewById(R.id.btnEdit)
        btnDelete= findViewById(R.id.btnDelete)
        tvRename = findViewById(R.id.tvEdit)
        tvDelete = findViewById(R.id.tvDelete)



        editBar = findViewById(R.id.editBar)
        btnClose = findViewById(R.id.btnClose)
        btnSelectAll = findViewById(R.id.btnSelectAll)

        bottomSheet = findViewById(R.id.bottomSheet2)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        measures = ArrayList()

        db = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "roomMeasures"
        ).build()

        mAdapter = Adapter(measures, this)

        var recyclerview = findViewById<RecyclerView>(R.id.recyclerview)
        recyclerview.apply {
            addItemDecoration(DividerItemDecoration(recyclerview.context, DividerItemDecoration.VERTICAL))
            adapter = mAdapter
            layoutManager = LinearLayoutManager(context)
        }

        fetchAll()

        searchInput = findViewById(R.id.search_input)
        searchInput.addTextChangedListener(object : TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                var query = s.toString()
                searchDatabase(query)
            }

            override fun afterTextChanged(s: Editable?) {}

        })

        btnClose.setOnClickListener {
            leaveEditMode()
        }

        btnSelectAll.setOnClickListener {
            allChecked = !allChecked
            measures.map{it.isChecked = allChecked}
            mAdapter.notifyDataSetChanged()

            if(allChecked){
                disableRename()
                enableDelete()
            }else{
                disableRename()
                disabldeDelete()
            }
        }

        btnDelete.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Delete record?")
            val nbRecords = measures.count{it.isChecked}
            builder.setMessage("Are you sure you want to delete $nbRecords record(s) ?")

            builder.setPositiveButton("Delete"){_, _ ->
                val toDelete = measures.filter{it.isChecked}.toTypedArray()
                GlobalScope.launch {
                    db.roomMeasureDao().delete(toDelete)
                    runOnUiThread {
                        measures.removeAll(toDelete)
                        mAdapter.notifyDataSetChanged()
                        leaveEditMode()
                    }
                }
            }

            builder.setNegativeButton("Cancel"){_, _ ->
                //it does nothing
            }

            val dialog = builder.create()
            dialog.show()
        }

        btnRename.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            val dialogView = this.layoutInflater.inflate(R.layout.rename_layout, null)
            builder.setView(dialogView)
            val dialog = builder.create()

            val measure = measures.filter{it.isChecked}.get(0)
            val textInput = dialogView.findViewById<TextInputEditText>(R.id.filenameInput)
            textInput.setText(measure.filename)

            dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {
                val input = textInput.text.toString()
                if(input.isEmpty()){
                    Toast.makeText(this, "A name is required", Toast.LENGTH_LONG).show()
                }else{
                    measure.filename = input
                    GlobalScope.launch {
                        db.roomMeasureDao().update(measure)
                        runOnUiThread {
                            mAdapter.notifyItemChanged(measures.indexOf(measure))
                            dialog.dismiss()
                            leaveEditMode()
                        }
                    }
                }

            }
            dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
        }
    }

    private fun leaveEditMode(){
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        editBar.visibility = View.GONE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.state= BottomSheetBehavior.STATE_HIDDEN


        measures.map{it.isChecked = false}
        mAdapter.setEditMode(false)
    }

    private fun disableRename(){
        btnRename.isClickable = false
        btnRename.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.greyDarkDisabled, theme)
        tvRename.setTextColor(ResourcesCompat.getColorStateList(resources, R.color.greyDarkDisabled, theme))
    }

    private fun disabldeDelete(){
        btnDelete.isClickable = false
        btnDelete.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.greyDarkDisabled, theme)
        tvDelete.setTextColor(ResourcesCompat.getColorStateList(resources, R.color.greyDarkDisabled, theme))
    }

    private fun enableRename(){
        btnRename.isClickable = true
        btnRename.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.greyDark, theme)
        tvRename.setTextColor(ResourcesCompat.getColorStateList(resources, R.color.greyDark, theme))
    }

    private fun enableDelete(){
        btnDelete.isClickable = true
        btnDelete.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.greyDark, theme)
        tvDelete.setTextColor(ResourcesCompat.getColorStateList(resources, R.color.greyDark, theme))
    }

    private fun searchDatabase(query: String) {
        GlobalScope.launch {
            measures.clear()
            var queryResult = db.roomMeasureDao().searchDatabase("%$query%")
            measures.addAll(queryResult)

            runOnUiThread {
                mAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun fetchAll() {
        GlobalScope.launch {
            measures.clear()
            var queryResult = db.roomMeasureDao().getAll()
            measures.addAll(queryResult)

            runOnUiThread{mAdapter.notifyDataSetChanged()}
        }

    }

    override fun onItemClickListener(position: Int) {
        var roomMeasure = measures[position]

        if(mAdapter.isEditMode()){
            measures[position].isChecked =!measures[position].isChecked
            mAdapter.notifyItemChanged(position)

            var nbSelected = measures.count{it.isChecked}
            when(nbSelected){
                0 ->{
                    disableRename()
                    disabldeDelete()
                }
                1 ->{
                    enableRename()
                    enableDelete()
                }
                else ->{
                    disableRename()
                    enableDelete()
                }
            }
        }else{
            var intent = Intent(this, Visualizer::class.java)

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

    override fun onItemLongClickListener(position: Int) {
        mAdapter.setEditMode(true)
        measures[position].isChecked =!measures[position].isChecked
        mAdapter.notifyItemChanged(position)

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

        if(mAdapter.isEditMode() && editBar.visibility == View.GONE){
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            supportActionBar?.setDisplayShowHomeEnabled(false)

            editBar.visibility = View.VISIBLE

            enableDelete()
            enableRename()
        }
    }
}