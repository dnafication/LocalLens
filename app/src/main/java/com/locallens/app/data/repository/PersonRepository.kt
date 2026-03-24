package com.locallens.app.data.repository

import com.locallens.app.data.clustering.DbscanClusterer
import com.locallens.app.data.db.entities.Person
import kotlinx.coroutines.flow.Flow

interface PersonRepository {
    fun getAll(): List<Person>
    fun getById(id: Long): Person?
    fun getConfirmedPersons(): Flow<List<Person>>
    fun getAllPersons(): Flow<List<Person>>
    fun createPerson(name: String): Person
    fun rename(id: Long, newName: String)
    fun mergePeople(sourceId: Long, targetId: Long)
    fun splitFace(faceDetectionId: Long): Person
    fun deletePerson(id: Long)
    fun applyClusteringResult(result: DbscanClusterer.ClusteringResult)
    fun updateCoverPhoto(personId: Long, faceDetectionId: Long)
    fun getPersonCentroids(): Map<Long, FloatArray>
}
