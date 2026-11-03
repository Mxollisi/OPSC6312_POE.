package com.example.fithub

import com.example.fithub.data.remote.WgerExercisePage
import com.example.fithub.data.remote.WgerMapper
import com.example.fithub.util.HtmlText
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses a realistic wger API response and checks it maps safely to app models. */
class WgerMapperTest {

    private val sampleJson = """
    {
      "count": 4,
      "next": null,
      "results": [
        {
          "id": 73,
          "category": {"id": 11, "name": "Chest"},
          "muscles": [{"id": 4, "name": "Pectoralis major", "name_en": "Chest"}],
          "equipment": [{"id": 1, "name": "Barbell"}],
          "images": [{"image": "https://wger.de/media/bench.png", "is_main": true}],
          "translations": [
            {"name": "Bankdrücken", "description": "<p>DE</p>", "language": 1},
            {"name": "Bench Press", "description": "<p>Lie on a <b>flat</b> bench&nbsp;and press.</p>", "language": 2}
          ]
        },
        {
          "id": 74,
          "category": {"id": 9, "name": "Legs"},
          "muscles": [],
          "equipment": [],
          "images": [],
          "translations": [{"name": "Kniebeuge", "description": "", "language": 1}]
        },
        {
          "id": 75,
          "category": null,
          "muscles": null,
          "equipment": null,
          "images": null,
          "exercises": [{"name": "Air squat", "description": null, "language": 2}]
        },
        { "id": null, "translations": [{"name": "No id", "language": 2}] }
      ]
    }
    """.trimIndent()

    private fun parse() = Gson().fromJson(sampleJson, WgerExercisePage::class.java)

    @Test
    fun mapsEnglishExercisesAndSkipsUnusableOnes() {
        val list = WgerMapper.toExercises(parse())
        assertEquals(2, list.size) // German-only and id-less items are skipped
        assertEquals(listOf("Air squat", "Bench Press"), list.map { it.name }) // sorted
    }

    @Test
    fun mapsAllFieldsOfAFullItem() {
        val bench = WgerMapper.toExercises(parse()).first { it.id == 73 }
        assertEquals("Chest", bench.category)
        assertEquals("Chest", bench.muscles)
        assertEquals("Barbell", bench.equipment)
        assertEquals("https://wger.de/media/bench.png", bench.imageUrl)
        assertEquals("Lie on a flat bench and press.", bench.description)
    }

    @Test
    fun missingFieldsFallBackSafely() {
        val squat = WgerMapper.toExercises(parse()).first { it.id == 75 }
        assertEquals("Other", squat.category)
        assertEquals("", squat.muscles)
        assertNull(squat.imageUrl)
        assertEquals("", squat.description)
    }

    @Test
    fun nullPageGivesEmptyList() {
        assertTrue(WgerMapper.toExercises(null).isEmpty())
    }

    @Test
    fun htmlIsStripped() {
        assertEquals("Hello world & more", HtmlText.strip("<p>Hello <i>world</i></p>&amp; more"))
        assertEquals("", HtmlText.strip(null))
    }
}
