package edu.uniandes.ecosnap.data.repository

import android.content.Context
import edu.uniandes.ecosnap.data.LocalStorageManager
import edu.uniandes.ecosnap.data.observer.Observable
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.domain.model.RecyclingGuideItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class RecyclingGuideItemDto(
    val id: String,
    val title: String,
    val wasteType: String,
    val category: String,
    val description: String,
    val instructions: String,
    val resourceUrl: String = ""
)

object RecyclingGuideRepository {
    private val observable = Observable<RecyclingGuideItem>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var localStorageManager: LocalStorageManager

    private var isInitialized = false


    private const val API_BASE_URL = "https://ecosnap-back.onrender.com"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()


    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }


    private val sampleRecyclingGuide = listOf(
        RecyclingGuideItem(
            id = "plastic-1",
            title = "Botellas PET",
            wasteType = "Plástico tipo 1 (PET)",
            category = "Plástico",
            description = "Las botellas PET son ampliamente utilizadas para bebidas y son 100% reciclables. Identificables por el número 1 en el símbolo de reciclaje.",
            instructions = "1. Vaciar completamente\n2. Enjuagar\n3. Quitar etiquetas\n4. Aplastar para reducir volumen"
        ),
        RecyclingGuideItem(
            id = "plastic-2",
            title = "Bolsas plásticas",
            wasteType = "Polietileno",
            category = "Plástico",
            description = "Las bolsas plásticas típicas son de polietileno. Su reciclaje es más complejo que otros plásticos, pero posible.",
            instructions = "1. Asegurarse que estén limpias y secas\n2. Agrupar varias bolsas juntas\n3. Llevar a puntos especiales de recolección"
        ),
        RecyclingGuideItem(
            id = "paper-1",
            title = "Papel de oficina",
            wasteType = "Papel blanco",
            category = "Papel",
            description = "El papel de oficina es fácilmente reciclable y puede ser convertido en nuevo papel, reduciendo la tala de árboles.",
            instructions = "1. Quitar grapas, clips y plásticos\n2. Evitar papel sucio con alimentos\n3. Mantener separado de otros tipos de papel"
        ),
        RecyclingGuideItem(
            id = "glass-1",
            title = "Botellas de vidrio",
            wasteType = "Vidrio",
            category = "Vidrio",
            description = "El vidrio es 100% reciclable y puede ser reciclado infinitamente sin perder calidad o pureza.",
            instructions = "1. Vaciar completamente\n2. Enjuagar\n3. Separar por colores si es posible\n4. No incluir vidrios de ventanas o espejos"
        ),
        RecyclingGuideItem(
            id = "metal-1",
            title = "Latas de aluminio",
            wasteType = "Aluminio",
            category = "Metal",
            description = "Las latas de aluminio son altamente reciclables. Reciclar aluminio requiere sólo el 5% de la energía necesaria para producir aluminio nuevo.",
            instructions = "1. Vaciar y enjuagar\n2. Aplastar para ahorrar espacio\n3. No es necesario quitar etiquetas"
        ),
        RecyclingGuideItem(
            id = "organic-1",
            title = "Residuos de alimentos",
            wasteType = "Orgánico",
            category = "Orgánico",
            description = "Los residuos orgánicos pueden ser compostados para crear abono natural para plantas y jardines.",
            instructions = "1. Separar de otros residuos\n2. Usar en compostaje doméstico o municipal\n3. Evitar incluir carne o lácteos en compostaje casero"
        ),
        RecyclingGuideItem(
            id = "electronic-1",
            title = "Teléfonos y computadoras",
            wasteType = "Electrónico",
            category = "Electrónico",
            description = "Los dispositivos electrónicos contienen materiales valiosos y también componentes tóxicos que requieren un manejo especial.",
            instructions = "1. Nunca tirar a la basura regular\n2. Borrar datos personales\n3. Llevar a puntos de recolección de residuos electrónicos\n4. Consultar con el fabricante sobre programas de devolución"
        ),
        RecyclingGuideItem(
            id = "electronic-2",
            title = "Baterías",
            wasteType = "Residuo peligroso",
            category = "Electrónico",
            description = "Las baterías contienen metales pesados y químicos que pueden contaminar suelos y aguas.",
            instructions = "1. Nunca tirar a la basura regular\n2. Almacenar en contenedor no metálico\n3. Llevar a puntos específicos de recolección de baterías"
        )
    )


    fun initialize(context: Context) {
        if (!isInitialized) {
            localStorageManager = LocalStorageManager(context)
            isInitialized = true
        }
    }

    fun addObserver(observer: Observer<RecyclingGuideItem>) {
        observable.addObserver(observer)
    }

    fun removeObserver(observer: Observer<RecyclingGuideItem>) {
        observable.removeObserver(observer)
    }


    fun fetch() {
        scope.launch {
            try {
                if (!isInitialized) {
                    throw IllegalStateException("Repository not initialized. Call initialize(context) first.")
                }

                // Verificar si hay datos en almacenamiento local
                val localItems = localStorageManager.getRecyclingGuideItems()

                if (localItems.isNotEmpty()) {
                    // Usar datos del almacenamiento local
                    localItems.forEach { item ->
                        observable.notifySuccess(item)
                    }
                }

                // Intentar obtener del backend para actualizar los datos
                try {
                    val items = fetchFromBackend()

                    if (items.isNotEmpty() && items != localItems) {
                        // Solo actualizar si los datos son diferentes
                        localStorageManager.saveRecyclingGuideItems(items)

                        // Si no había datos locales, notificar ahora
                        if (localItems.isEmpty()) {
                            items.forEach { item ->
                                observable.notifySuccess(item)
                            }
                        }
                    }

                } catch (e: Exception) {
                    // Si falla el backend pero no hay datos locales, usar datos de muestra
                    if (localItems.isEmpty()) {
                        println("Backend error: ${e.message}, using sample data")

                        // Guardar datos de muestra en storage local
                        localStorageManager.saveRecyclingGuideItems(sampleRecyclingGuide)

                        // Notificar con datos de muestra
                        sampleRecyclingGuide.forEach { item ->
                            observable.notifySuccess(item)
                        }
                    }
                }

            } catch (e: Exception) {
                observable.notifyError(e)
            }
        }
    }

    fun forceUpdate() {
        scope.launch {
            try {
                if (!isInitialized) {
                    throw IllegalStateException("Repository not initialized. Call initialize(context) first.")
                }

                try {
                    val items = fetchFromBackend()

                    if (items.isNotEmpty()) {
                        // Actualizar almacenamiento local
                        localStorageManager.saveRecyclingGuideItems(items)

                        // Notificar a los observadores
                        items.forEach { item ->
                            observable.notifySuccess(item)
                        }
                    }

                } catch (e: Exception) {
                    // Si falla el backend, intentar usar datos locales o datos de muestra
                    val localItems = localStorageManager.getRecyclingGuideItems()

                    if (localItems.isNotEmpty()) {
                        localItems.forEach { item ->
                            observable.notifySuccess(item)
                        }
                    } else {
                        // Como último recurso, usar datos de muestra
                        localStorageManager.saveRecyclingGuideItems(sampleRecyclingGuide)
                        sampleRecyclingGuide.forEach { item ->
                            observable.notifySuccess(item)
                        }
                    }

                    observable.notifyError(e)
                }

            } catch (e: Exception) {
                observable.notifyError(e)
            }
        }
    }


    private suspend fun fetchFromBackend(): List<RecyclingGuideItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API_BASE_URL/api/recycling-guide")
            .build()

        return@withContext try {
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("Backend responded with ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            // Parsear la respuesta JSON
            val dtoList = json.decodeFromString<List<RecyclingGuideItemDto>>(responseBody)

            // Convertir DTOs a objetos de dominio
            dtoList.map { dto ->
                RecyclingGuideItem(
                    id = dto.id,
                    title = dto.title,
                    wasteType = dto.wasteType,
                    category = dto.category,
                    description = dto.description,
                    instructions = dto.instructions,
                    resourceUrl = dto.resourceUrl
                )
            }

        } catch (e: Exception) {
            println("Error fetching from backend: ${e.message}")
            throw e
        }
    }

    fun hasObservers(): Boolean {
        return observable.hasObservers()
    }
}