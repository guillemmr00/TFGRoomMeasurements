package edu.upf.aism.tfgroommeasurements

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "roomMeasures")
data class RoomMeasure(
    var filename : String,
    var date : String,
    var notes : String,
    var sampleRate : Int,
    var f0 : Double,
    var f1 : Double,
    var duration : Double,
    var mode : String,
    var outGain : Double,
    var impulseResponsePath : String
){
    @PrimaryKey(autoGenerate = true)
    var id = 0
    @Ignore
    var isChecked = false
}
