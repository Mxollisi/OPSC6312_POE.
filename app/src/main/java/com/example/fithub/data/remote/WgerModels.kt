package com.example.fithub.data.remote

import com.example.fithub.data.model.Exercise
import com.example.fithub.util.HtmlText
import com.google.gson.annotations.SerializedName

/*
 * Response models for https://wger.de/api/v2/exerciseinfo/
 * Every field is nullable: the API is community-run and fields are sometimes missing,
 * and a missing field must never crash the app.
 */

data class WgerExercisePage(
    @SerializedName("count") val count: Int?,
    @SerializedName("next") val next: String?,
    @SerializedName("results") val results: List<WgerExerciseInfo>?
)

data class WgerExerciseInfo(
    @SerializedName("id") val id: Int?,
    @SerializedName("category") val category: WgerNamed?,
    @SerializedName("muscles") val muscles: List<WgerMuscle>?,
    @SerializedName("equipment") val equipment: List<WgerNamed>?,
    @SerializedName("images") val images: List<WgerImage>?,
    // Newer API versions call this "translations", older ones "exercises".
    @SerializedName("translations") val translations: List<WgerTranslation>?,
    @SerializedName("exercises") val legacyTranslations: List<WgerTranslation>?
)

data class WgerNamed(
    @SerializedName("id") val id: Int?,
    @SerializedName("name") val name: String?
)

data class WgerMuscle(
    @SerializedName("id") val id: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("name_en") val nameEn: String?
)

data class WgerImage(
    @SerializedName("image") val image: String?,
    @SerializedName("is_main") val isMain: Boolean?
)

data class WgerTranslation(
    @SerializedName("name") val name: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("language") val language: Int?
)

object WgerMapper {
    const val ENGLISH = 2

    /** Converts an API item into an [Exercise], or null if it has no usable English name. */
    fun toExercise(info: WgerExerciseInfo): Exercise? {
        val id = info.id ?: return null
        val all = info.translations.orEmpty() + info.legacyTranslations.orEmpty()
        val translation = all.firstOrNull { it.language == ENGLISH && !it.name.isNullOrBlank() }
            ?: return null
        val name = translation.name!!.trim()

        val muscles = info.muscles.orEmpty()
            .mapNotNull { m -> m.nameEn?.takeIf { it.isNotBlank() } ?: m.name?.takeIf { it.isNotBlank() } }
            .distinct()
            .joinToString(", ")

        val equipment = info.equipment.orEmpty()
            .mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
            .joinToString(", ")

        val image = info.images.orEmpty().let { imgs ->
            (imgs.firstOrNull { it.isMain == true } ?: imgs.firstOrNull())?.image
        }

        return Exercise(
            id = id,
            name = name,
            category = info.category?.name?.trim().orEmpty().ifBlank { "Other" },
            description = HtmlText.strip(translation.description),
            muscles = muscles,
            equipment = equipment,
            imageUrl = image?.takeIf { it.startsWith("http") }
        )
    }

    fun toExercises(page: WgerExercisePage?): List<Exercise> =
        page?.results.orEmpty()
            .mapNotNull { toExercise(it) }
            .distinctBy { it.name.lowercase() }
            .sortedBy { it.name.lowercase() }
}
