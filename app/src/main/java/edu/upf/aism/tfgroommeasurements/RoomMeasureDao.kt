package edu.upf.aism.tfgroommeasurements

import androidx.room.*

@Dao
interface RoomMeasureDao {
    @Query("SELECT * FROM roomMeasures")
    fun getAll(): List<RoomMeasure>

    @Query("SELECT * FROM roomMeasures WHERE filename LIKE :query")
    fun searchDatabase(query: String):  List<RoomMeasure>

    @Insert
    fun insert(vararg roomMeasure: RoomMeasure)

    @Delete
    fun delete(roomMeasure: Array<RoomMeasure>)

    @Update
    fun update(roomMeasure: RoomMeasure)
}