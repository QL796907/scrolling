package com.hy.autoswipe

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class IntervalPreset(
    val id: String,
    val name: String,
    val min: Int,
    val max: Int,
) {
    fun label(): String = name.ifBlank { "${min}–${max}秒" }
}

class IntervalStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_PRESETS)) {
            savePresets(listOf(defaultPreset()))
            prefs.edit()
                .putString(KEY_SELECTED, DEFAULT_ID)
                .putInt(KEY_MIN, DEFAULT_MIN)
                .putInt(KEY_MAX, DEFAULT_MAX)
                .apply()
        }
    }

    fun currentMin(): Int = prefs.getInt(KEY_MIN, DEFAULT_MIN).coerceAtLeast(1)

    fun currentMax(): Int = prefs.getInt(KEY_MAX, DEFAULT_MAX).coerceAtLeast(currentMin())

    fun currentRange(): Pair<Int, Int> {
        val min = currentMin()
        val max = currentMax().coerceAtLeast(min)
        return min to max
    }

    fun setCurrent(min: Int, max: Int) {
        val safeMin = min.coerceIn(1, 3600)
        val safeMax = max.coerceIn(safeMin, 3600)
        prefs.edit()
            .putInt(KEY_MIN, safeMin)
            .putInt(KEY_MAX, safeMax)
            .apply()
    }

    fun selectedId(): String? = prefs.getString(KEY_SELECTED, DEFAULT_ID)

    fun presets(): List<IntervalPreset> {
        val raw = prefs.getString(KEY_PRESETS, null)
        if (raw.isNullOrBlank()) return listOf(defaultPreset())
        return try {
            val array = JSONArray(raw)
            if (array.length() == 0) listOf(defaultPreset()) else {
                (0 until array.length()).map { index ->
                    val obj = array.getJSONObject(index)
                    IntervalPreset(
                        id = obj.getString("id"),
                        name = obj.optString("name"),
                        min = obj.getInt("min"),
                        max = obj.getInt("max"),
                    )
                }
            }
        } catch (_: Exception) {
            listOf(defaultPreset())
        }
    }

    fun addPreset(name: String, min: Int, max: Int): IntervalPreset {
        val safeMin = min.coerceIn(1, 3600)
        val safeMax = max.coerceIn(safeMin, 3600)
        val preset = IntervalPreset(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "${safeMin}–${safeMax}秒" },
            min = safeMin,
            max = safeMax,
        )
        savePresets(presets() + preset)
        return preset
    }

    fun deletePreset(id: String) {
        val remaining = presets().filterNot { it.id == id }.ifEmpty { listOf(defaultPreset()) }
        savePresets(remaining)
        if (selectedId() == id) {
            val first = remaining.first()
            selectPreset(first.id)
        }
    }

    fun updatePreset(id: String, name: String, min: Int, max: Int): IntervalPreset? {
        val safeMin = min.coerceIn(1, 3600)
        val safeMax = max.coerceIn(safeMin, 3600)
        val updated = IntervalPreset(
            id = id,
            name = name.trim().ifBlank { "${safeMin}–${safeMax}秒" },
            min = safeMin,
            max = safeMax,
        )
        val list = presets().map { if (it.id == id) updated else it }
        if (list.none { it.id == id }) return null
        savePresets(list)
        if (selectedId() == id) {
            selectPreset(id)
        }
        return updated
    }

    fun selectPreset(id: String) {
        val preset = presets().find { it.id == id } ?: return
        prefs.edit()
            .putString(KEY_SELECTED, preset.id)
            .putInt(KEY_MIN, preset.min)
            .putInt(KEY_MAX, preset.max)
            .apply()
    }

    fun markCustomSelected() {
        prefs.edit().remove(KEY_SELECTED).apply()
    }

    private fun savePresets(list: List<IntervalPreset>) {
        val array = JSONArray()
        list.forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("name", preset.name)
                    .put("min", preset.min)
                    .put("max", preset.max),
            )
        }
        prefs.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    companion object {
        const val DEFAULT_MIN = 60
        const val DEFAULT_MAX = 120
        private const val DEFAULT_ID = "default"
        private const val PREFS = "interval"
        private const val KEY_MIN = "min"
        private const val KEY_MAX = "max"
        private const val KEY_PRESETS = "presets"
        private const val KEY_SELECTED = "selected"

        fun defaultPreset(): IntervalPreset = IntervalPreset(
            id = DEFAULT_ID,
            name = "默认 60–120秒",
            min = DEFAULT_MIN,
            max = DEFAULT_MAX,
        )
    }
}
