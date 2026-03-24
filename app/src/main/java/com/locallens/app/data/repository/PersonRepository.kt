package com.locallens.app.data.repository

import com.locallens.app.data.clustering.DbscanClusterer
import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.FaceDetection_
import com.locallens.app.data.db.entities.Person
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepository @Inject constructor(private val boxStore: BoxStore) {

    private val personBox get() = boxStore.boxFor(Person::class.java)
    private val faceBox get() = boxStore.boxFor(FaceDetection::class.java)

    fun getAll(): List<Person> = personBox.all

    fun getById(id: Long): Person? = personBox.get(id)

    fun getConfirmedPersons(): Flow<List<Person>> = flow {
        emit(personBox.all)
    }

    fun createPerson(name: String): Person {
        val person = Person(
            displayName = name,
            isConfirmed = name.isNotEmpty(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        person.id = personBox.put(person)
        return person
    }

    fun rename(id: Long, newName: String) {
        val person = personBox.get(id) ?: return
        person.displayName = newName
        person.isConfirmed = true
        person.updatedAt = System.currentTimeMillis()
        personBox.put(person)
    }

    fun mergePeople(sourceId: Long, targetId: Long) {
        val sourceFaces = faceBox.query(FaceDetection_.personId.equal(sourceId)).build().find()
        boxStore.runInTx {
            for (face in sourceFaces) {
                face.person.targetId = targetId
                faceBox.put(face)
            }
            val target = personBox.get(targetId) ?: return@runInTx
            target.faceCount += sourceFaces.size
            target.updatedAt = System.currentTimeMillis()
            personBox.put(target)
            personBox.remove(sourceId)
        }
    }

    fun splitFace(faceDetectionId: Long): Person {
        val face = faceBox.get(faceDetectionId) ?: throw IllegalArgumentException("Face not found")
        val newPerson = createPerson("")
        val oldPersonId = face.person.targetId
        face.person.targetId = newPerson.id
        faceBox.put(face)

        val oldPerson = personBox.get(oldPersonId)
        if (oldPerson != null) {
            oldPerson.faceCount = maxOf(0, oldPerson.faceCount - 1)
            if (oldPerson.faceCount == 0) {
                personBox.remove(oldPersonId)
            } else {
                personBox.put(oldPerson)
            }
        }
        newPerson.faceCount = 1
        personBox.put(newPerson)
        return newPerson
    }

    fun deletePerson(id: Long) {
        val faces = faceBox.query(FaceDetection_.personId.equal(id)).build().find()
        boxStore.runInTx {
            for (face in faces) {
                face.person.targetId = 0
                faceBox.put(face)
            }
            personBox.remove(id)
        }
    }

    fun applyClusteringResult(result: DbscanClusterer.ClusteringResult) {
        boxStore.runInTx {
            val newPersonsByClusterId = mutableMapOf<Long, Long>()
            for ((faceId, clusterId) in result.assignments) {
                val face = faceBox.get(faceId) ?: continue
                val personId = if (clusterId > 0) {
                    clusterId
                } else {
                    newPersonsByClusterId.getOrPut(clusterId) {
                        val p = Person(
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        personBox.put(p)
                    }
                }
                face.person.targetId = personId
                faceBox.put(face)

                val person = personBox.get(personId) ?: continue
                person.faceCount++
                personBox.put(person)
            }
        }
    }

    fun updateCoverPhoto(personId: Long, faceDetectionId: Long) {
        val person = personBox.get(personId) ?: return
        person.coverFaceDetectionId = faceDetectionId
        person.updatedAt = System.currentTimeMillis()
        personBox.put(person)
    }
}
