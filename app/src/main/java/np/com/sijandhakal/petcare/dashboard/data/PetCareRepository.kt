package np.com.sijandhakal.petcare.dashboard.data

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Firestore access for the signed-in user. Everything lives under users/{uid}:
 *   users/{uid}            name, email
 *   users/{uid}/pets/{id}
 *   users/{uid}/tasks/{id} (petId links a task to its pet)
 *   users/{uid}/places/{id}
 * Firestore's offline cache makes writes show up in listeners immediately, even without a connection.
 */
object PetCareRepository {

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    private fun userDoc(): DocumentReference {
        val uid = requireNotNull(auth.currentUser?.uid) { "No signed-in user" }
        return db.collection("users").document(uid)
    }

    private fun pets(): CollectionReference = userDoc().collection("pets")
    private fun tasks(): CollectionReference = userDoc().collection("tasks")
    private fun places(): CollectionReference = userDoc().collection("places")

    fun saveUserProfile(name: String, email: String): Task<Void> =
        userDoc().set(mapOf("name" to name, "email" to email, "createdAt" to FieldValue.serverTimestamp()))

    // Pets

    fun listenPets(onError: (Exception) -> Unit, onChange: (List<Pet>) -> Unit): ListenerRegistration =
        pets().addSnapshotListener { snapshot, error ->
            if (error != null) onError(error)
            else onChange(snapshot!!.documents.map(::toPet).sortedBy { it.name.lowercase() })
        }

    /** A fresh document id, so a new pet can be selected in the UI before the write completes. */
    fun newPetId(): String = pets().document().id

    /** Creates or overwrites the pet with [Pet.id] (use [newPetId] for a new pet). */
    fun savePet(pet: Pet): Task<Void> {
        require(pet.id.isNotBlank()) { "Pet id is required; use newPetId() for new pets" }
        return pets().document(pet.id).set(
            mapOf(
                "name" to pet.name,
                "species" to pet.species.name,
                "breed" to pet.breed,
                "age" to pet.age,
                "weight" to pet.weight,
                "microchip" to pet.microchip,
                "diet" to pet.diet,
                "allergies" to pet.allergies,
                "vetName" to pet.vetName,
                "vetPhone" to pet.vetPhone,
                "photoBase64" to pet.photoBase64
            )
        )
    }

    /** Deletes the pet together with all of its care tasks. */
    fun deletePet(petId: String): Task<Void> =
        tasks().whereEqualTo("petId", petId).get().continueWithTask { query ->
            val batch = db.batch()
            query.result.documents.forEach { batch.delete(it.reference) }
            batch.delete(pets().document(petId))
            batch.commit()
        }

    private fun toPet(doc: DocumentSnapshot) = Pet(
        id = doc.id,
        name = doc.getString("name").orEmpty(),
        species = enumOrDefault(doc.getString("species"), Species.OTHER),
        breed = doc.getString("breed").orEmpty(),
        age = doc.getString("age").orEmpty(),
        weight = doc.getString("weight").orEmpty(),
        microchip = doc.getString("microchip").orEmpty(),
        diet = doc.getString("diet").orEmpty(),
        allergies = doc.getString("allergies").orEmpty(),
        vetName = doc.getString("vetName").orEmpty(),
        vetPhone = doc.getString("vetPhone").orEmpty(),
        photoBase64 = doc.getString("photoBase64").orEmpty()
    )

    // Care tasks

    fun listenTasks(onError: (Exception) -> Unit, onChange: (List<CareTask>) -> Unit): ListenerRegistration =
        tasks().addSnapshotListener { snapshot, error ->
            if (error != null) onError(error)
            else onChange(snapshot!!.documents.map(::toTask).sortedWith(compareBy({ it.hour }, { it.minute })))
        }

    fun getTask(id: String): Task<CareTask?> =
        tasks().document(id).get().continueWith { it.result.takeIf { doc -> doc.exists() }?.let(::toTask) }

    /** Creates the task when [CareTask.id] is blank, otherwise overwrites it (also used to undo a delete). */
    fun saveTask(task: CareTask): Task<Void> {
        val doc = if (task.id.isBlank()) tasks().document() else tasks().document(task.id)
        return doc.set(
            mapOf(
                "petId" to task.petId,
                "title" to task.title,
                "category" to task.category.name,
                "hour" to task.hour,
                "minute" to task.minute,
                "startDateMillis" to task.startDateMillis,
                "frequency" to task.frequency,
                "supplies" to task.supplies,
                "notes" to task.notes,
                "includeInSitterChecklist" to task.includeInSitterChecklist,
                "doneOn" to task.doneOn
            )
        )
    }

    fun setTaskDone(id: String, done: Boolean): Task<Void> =
        tasks().document(id).update("doneOn", if (done) CareTask.today() else null)

    fun deleteTask(id: String): Task<Void> = tasks().document(id).delete()

    private fun toTask(doc: DocumentSnapshot) = CareTask(
        id = doc.id,
        petId = doc.getString("petId").orEmpty(),
        title = doc.getString("title").orEmpty(),
        category = enumOrDefault(doc.getString("category"), CareCategory.FEEDING),
        hour = doc.getLong("hour")?.toInt() ?: 8,
        minute = doc.getLong("minute")?.toInt() ?: 0,
        startDateMillis = doc.getLong("startDateMillis"),
        frequency = doc.getString("frequency").orEmpty(),
        supplies = doc.getString("supplies").orEmpty(),
        notes = doc.getString("notes").orEmpty(),
        includeInSitterChecklist = doc.getBoolean("includeInSitterChecklist") ?: true,
        doneOn = doc.getString("doneOn")
    )

    // Places

    fun listenPlaces(onError: (Exception) -> Unit, onChange: (List<Place>) -> Unit): ListenerRegistration =
        places().addSnapshotListener { snapshot, error ->
            if (error != null) onError(error)
            else onChange(snapshot!!.documents.map(::toPlace).sortedBy { it.name.lowercase() })
        }

    fun savePlace(place: Place): Task<Void> {
        val doc = if (place.id.isBlank()) places().document() else places().document(place.id)
        return doc.set(
            mapOf(
                "name" to place.name,
                "category" to place.category.name,
                "address" to place.address,
                "notes" to place.notes,
                "latitude" to place.latitude,
                "longitude" to place.longitude
            )
        )
    }

    fun deletePlace(id: String): Task<Void> = places().document(id).delete()

    private fun toPlace(doc: DocumentSnapshot) = Place(
        id = doc.id,
        name = doc.getString("name").orEmpty(),
        category = enumOrDefault(doc.getString("category"), PlaceCategory.VET),
        address = doc.getString("address").orEmpty(),
        notes = doc.getString("notes").orEmpty(),
        latitude = doc.getDouble("latitude"),
        longitude = doc.getDouble("longitude")
    )

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
