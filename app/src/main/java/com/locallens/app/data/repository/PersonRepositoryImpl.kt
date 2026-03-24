package com.locallens.app.data.repository

import com.locallens.app.data.clustering.DbscanClusterer
import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.Person
import com.locallens.app.util.EmbeddingUtils
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore
) : PersonRepository {

    private val personBox: Box<Person> by lazy { boxStore.boxFor(Person::class.java) }
    private val faceBox: Box<FaceDetection> by lazy { boxStore.boxFor(FaceDetection::class.java) }

    override fun getAll(): List<Person> = personBox.all

    override fun getById(id: Long): Person? = personBox.get(id)

    override fun getConfirmedPersons(): Flow<List<Person>> = flow {
        emit(personBox.all.filter { it.isConfirmed })
    }

    override fun getAllPersons(): Flow<List<Person>> = flow {
        emit(personBox.all.sortedByDescending { it.faceCount })
    }

    override fun createPerson(name: String): Person {
        val person = Person(
            displayName = name,
            isConfirmed = name.isNotBlank(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        personBox.put(person)
        return person
    }

    override fun rename(id: Long, newName: String) {
        val person = personBox.get(id) ?: return
        person.displayName = newName
        person.isConfirmed = newName.isNotBlank()
        person.updatedAt = System.currentTimeMillis()
        personBox.put(person)
    }

    override fun mergePeople(sourceId: Long, targetId: Long) {
        boxStore.runInTx {
            val source = personBox.get(sourceId) ?: return@runInTx
            val target = personBox.get(targetId) ?: return@runInTx

            val sourceFaces = faceBox.all.filter { it.person.targetId == sourceId }
            for (face in sourceFaces) {
                face.person.targetId = targetId
                faceBox.put(face)
            }

            target.faceCount += source.faceCount
            target.updatedAt = System.currentTimeMillis()
            personBox.put(target)
            personBox.remove(source)
        }
    }

    override fun splitFace(faceDetectionId: Long): Person {
        val face = faceBox.get(faceDetectionId)
            ?: throw IllegalArgumentException("FaceDetection not found: $faceDetectionId")
        
        val newPerson = createPerson("")
        val oldPersonId = face.person.targetId

        face.person.targetId = newPerson.id
        faceBox.put(face)

        // Update old person's face count
        if (oldPersonId > 0) {
            val oldPerson = personBox.get(oldPersonId)
            if (oldPerson != null) {
                oldPerson.faceCount = maxOf(0, oldPerson.faceCount - 1)
                oldPerson.updatedAt = System.currentTimeMillis()
                if (oldPerson.faceCount == 0) {
                    personBox.remove(oldPerson)
                } else {
                    personBox.put(oldPerson)
                }
            }
        }

        newPerson.faceCount = 1
        newPerson.coverFaceDetectionId = faceDetectionId
        personBox.put(newPerson)
        return newPerson
    }

    override fun deletePerson(id: Long) {
        boxStore.runInTx {
            val faces = faceBox.all.filter { it.person.targetId == id }
            for (face in faces) {
                face.person.targetId = 0
                faceBox.put(face)
            }
            personBox.remove(id)
        }
    }

    override fun applyClusteringResult(result: DbscanClusterer.ClusteringResult) {
        boxStore.runInTx {
            // Handle new clusters: create new Person records
            val clusterIdToPersonId = mutableMapOf<Long, Long>()
            for ((clusterId, centroid) in result.newClusters) {
                val person = createPerson("")
                clusterIdToPersonId[clusterId] = person.id
            }

            // Apply assignments
            for ((faceDetectionId, assignedId) in result.assignments) {
                val face = faceBox.get(faceDetectionId) ?: continue
                val personId = if (assignedId < 0) {
                    clusterIdToPersonId[assignedId] ?: continue
                } else {
                    assignedId
                }
                face.person.targetId = personId
                faceBox.put(face)

                // Update person face count
                val person = personBox.get(personId)
                if (person != null) {
                    person.faceCount++
                    if (person.coverFaceDetectionId == 0L) {
                        person.coverFaceDetectionId = faceDetectionId
                    }
                    person.updatedAt = System.currentTimeMillis()
                    personBox.put(person)
                }
            }
        }
    }

    override fun updateCoverPhoto(personId: Long, faceDetectionId: Long) {
        val person = personBox.get(personId) ?: return
        person.coverFaceDetectionId = faceDetectionId
        person.updatedAt = System.currentTimeMillis()
        personBox.put(person)
    }

    override fun getPersonCentroids(): Map<Long, FloatArray> {
        val result = mutableMapOf<Long, FloatArray>()
        for (person in personBox.all) {
            val faces = faceBox.all.filter { it.person.targetId == person.id }
            val embeddings = faces.map { it.embedding }.filter { it.isNotEmpty() }
            val centroid = EmbeddingUtils.computeCentroid(embeddings)
            if (centroid != null) {
                result[person.id] = centroid
            }
        }
        return result
    }
}
