package com.valtenergy.calendartopki

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BurnDay(
    var morning: Boolean = false,
    var morningBuckets: Int = 0,
    var evening: Boolean = false,
    var eveningBuckets: Int = 0
)

class MainActivity : ComponentActivity() {
    private val records = mutableMapOf<String, BurnDay>()
    private var selectedDate = LocalDate.now()
    private lateinit var root: LinearLayout
    private lateinit var morningCheck: CheckBox
    private lateinit var eveningCheck: CheckBox
    private lateinit var morningBuckets: EditText
    private lateinit var eveningBuckets: EditText

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(exportJson().toByteArray(Charsets.UTF_8)) }
                toast("Резервная копия сохранена")
            } catch (e: Exception) { toast("Не удалось сохранить копию") }
        }
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                importJson(json)
                persist()
                render()
                toast("Резервная копия восстановлена")
            } catch (e: Exception) { toast("Не удалось прочитать файл копии") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSaved()
        render()
    }

    private fun render() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "Календарь топки"
            textSize = 26f
            setTextColor(Color.rgb(28, 43, 62))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        })

        val monthRow = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        val prev = Button(this).apply { text = "‹"; textSize = 22f }
        val monthTitle = TextView(this).apply {
            text = selectedDate.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))).replaceFirstChar { it.uppercase() }
            textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.DKGRAY)
        }
        val next = Button(this).apply { text = "›"; textSize = 22f }
        monthRow.addView(prev, LinearLayout.LayoutParams(dp(56), dp(48)))
        monthRow.addView(monthTitle, LinearLayout.LayoutParams(0, dp(48), 1f))
        monthRow.addView(next, LinearLayout.LayoutParams(dp(56), dp(48)))
        root.addView(monthRow)
        prev.setOnClickListener { saveCurrent(); selectedDate = selectedDate.minusMonths(1); render() }
        next.setOnClickListener { saveCurrent(); selectedDate = selectedDate.plusMonths(1); render() }

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(12)) }
        val weekNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        val header = LinearLayout(this)
        weekNames.forEach { name -> header.addView(TextView(this).apply {
            text = name; gravity = Gravity.CENTER; textSize = 13f; setTextColor(Color.GRAY)
        }, LinearLayout.LayoutParams(0, dp(32), 1f)) }
        grid.addView(header)
        val ym = YearMonth.from(selectedDate)
        val firstOffset = ym.atDay(1).dayOfWeek.value - 1
        val cells = ((firstOffset + ym.lengthOfMonth() + 6) / 7) * 7
        for (week in 0 until cells / 7) {
            val row = LinearLayout(this)
            for (col in 0..6) {
                val index = week * 7 + col
                val dayNum = index - firstOffset + 1
                val cell = if (dayNum in 1..ym.lengthOfMonth()) {
                    val date = ym.atDay(dayNum)
                    val rec = records[date.toString()]
                    val mark = when {
                        rec == null -> ""
                        rec.morning && rec.evening -> "●●"
                        rec.morning || rec.evening -> "●"
                        else -> ""
                    }
                    TextView(this).apply {
                        text = "$dayNum\n$mark"
                        gravity = Gravity.CENTER
                        textSize = 15f
                        setTextColor(if (date == selectedDate) Color.WHITE else Color.rgb(40, 52, 67))
                        setBackgroundColor(if (date == selectedDate) Color.rgb(39, 103, 166) else Color.WHITE)
                        setPadding(0, dp(5), 0, dp(5))
                        setOnClickListener { saveCurrent(); selectedDate = date; render() }
                    }
                } else TextView(this)
                val lp = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                row.addView(cell, lp)
            }
            grid.addView(row)
        }
        root.addView(grid)

        root.addView(TextView(this).apply {
            text = "Запись за " + selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
            textSize = 18f; setTextColor(Color.rgb(28, 43, 62)); setPadding(0, dp(8), 0, dp(8))
        })

        val rec = records[selectedDate.toString()] ?: BurnDay()
        morningCheck = CheckBox(this).apply { text = "Топил утром"; isChecked = rec.morning; textSize = 16f }
        morningBuckets = EditText(this).apply {
            hint = "Вёдер угля утром"; inputType = 2; setText(if (rec.morningBuckets > 0) rec.morningBuckets.toString() else "")
        }
        eveningCheck = CheckBox(this).apply { text = "Топил вечером"; isChecked = rec.evening; textSize = 16f }
        eveningBuckets = EditText(this).apply {
            hint = "Вёдер угля вечером"; inputType = 2; setText(if (rec.eveningBuckets > 0) rec.eveningBuckets.toString() else "")
        }
        root.addView(morningCheck)
        root.addView(morningBuckets, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(eveningCheck)
        root.addView(eveningBuckets, LinearLayout.LayoutParams(-1, dp(52)))

        val save = Button(this).apply { text = "Сохранить запись" }
        root.addView(save, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        save.setOnClickListener { saveCurrent(); toast("Запись сохранена") }

        val backupRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val export = Button(this).apply { text = "Сделать копию"; textSize = 13f }
        val restore = Button(this).apply { text = "Восстановить"; textSize = 13f }
        backupRow.addView(export, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(0, dp(8), dp(4), 0) })
        backupRow.addView(restore, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), dp(8), 0, 0) })
        root.addView(backupRow)
        export.setOnClickListener { saveCurrent(); createBackup.launch("kalendar-topki-backup.json") }
        restore.setOnClickListener { openBackup.launch(arrayOf("application/json", "text/*")) }

        root.addView(TextView(this).apply {
            text = "Отметки в календаре: одна точка — одна топка, две точки — утро и вечер."
            textSize = 12f; setTextColor(Color.GRAY); setPadding(0, dp(12), 0, 0)
        })
    }

    private fun saveCurrent() {
        if (!::morningCheck.isInitialized) return
        val key = selectedDate.toString()
        val m = morningBuckets.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0
        val e = eveningBuckets.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0
        val value = BurnDay(morningCheck.isChecked, m, eveningCheck.isChecked, e)
        if (value.morning || value.evening || m > 0 || e > 0) records[key] = value else records.remove(key)
        persist()
    }

    private fun persist() {
        getSharedPreferences("burn_calendar", MODE_PRIVATE).edit().putString("records", exportJson()).apply()
    }

    private fun loadSaved() {
        val raw = getSharedPreferences("burn_calendar", MODE_PRIVATE).getString("records", null) ?: return
        try { importJson(raw) } catch (_: Exception) { }
    }

    private fun exportJson(): String {
        val obj = JSONObject()
        val arr = JSONArray()
        records.toSortedMap().forEach { (date, d) ->
            arr.put(JSONObject().put("date", date).put("morning", d.morning)
                .put("morningBuckets", d.morningBuckets).put("evening", d.evening)
                .put("eveningBuckets", d.eveningBuckets))
        }
        obj.put("version", 1).put("records", arr)
        return obj.toString(2)
    }

    private fun importJson(raw: String) {
        val arr = JSONObject(raw).getJSONArray("records")
        val restored = mutableMapOf<String, BurnDay>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val date = item.getString("date")
            LocalDate.parse(date)
            restored[date] = BurnDay(item.optBoolean("morning"), item.optInt("morningBuckets"),
                item.optBoolean("evening"), item.optInt("eveningBuckets"))
        }
        records.clear()
        records.putAll(restored)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
