package com.valtenergy.calendartopki

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
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
    private lateinit var morningCountText: TextView
    private lateinit var eveningCountText: TextView
    private var morningCount = 0
    private var eveningCount = 0

    private val bg = Color.rgb(17, 20, 27)
    private val panel = Color.rgb(29, 34, 44)
    private val panel2 = Color.rgb(38, 44, 56)
    private val orange = Color.rgb(255, 145, 48)
    private val red = Color.rgb(239, 78, 67)
    private val white = Color.rgb(245, 246, 249)
    private val muted = Color.rgb(157, 166, 181)

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            contentResolver.openOutputStream(uri)?.use { it.write(exportJson().toByteArray(Charsets.UTF_8)) }
            toast("Резервная копия сохранена")
        } catch (_: Exception) { toast("Не удалось сохранить копию") }
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            importJson(json)
            persist()
            render()
            toast("Резервная копия восстановлена")
        } catch (_: Exception) { toast("Не удалось прочитать файл копии") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        loadSaved()
        render()
    }

    private fun render() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); clipToPadding = false }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(label("ДОМ  •  ОТОПЛЕНИЕ", 11f, orange, true).apply {
            letterSpacing = 0.12f
            setPadding(dp(2), dp(4), 0, dp(5))
        })
        root.addView(label("Календарь топки", 28f, white, true))
        root.addView(label("Учёт утренней и вечерней топки", 14f, muted).apply {
            setPadding(0, dp(3), 0, dp(18))
        })

        val monthCard = card()
        val monthRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val prev = smallButton("‹")
        val monthTitle = label(
            selectedDate.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))).replaceFirstChar { it.uppercase() },
            18f, white, true
        ).apply { gravity = Gravity.CENTER }
        val next = smallButton("›")
        monthRow.addView(prev, LinearLayout.LayoutParams(dp(46), dp(44)))
        monthRow.addView(monthTitle, LinearLayout.LayoutParams(0, dp(44), 1f))
        monthRow.addView(next, LinearLayout.LayoutParams(dp(46), dp(44)))
        monthCard.addView(monthRow)
        monthCard.addView(space(8))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val weekNames = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
        val header = LinearLayout(this)
        weekNames.forEach { name ->
            header.addView(label(name, 10f, muted, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, dp(30), 1f))
        }
        grid.addView(header)
        val ym = YearMonth.from(selectedDate)
        val offset = ym.atDay(1).dayOfWeek.value - 1
        val cells = ((offset + ym.lengthOfMonth() + 6) / 7) * 7
        for (week in 0 until cells / 7) {
            val row = LinearLayout(this)
            for (col in 0..6) {
                val dayNum = week * 7 + col - offset + 1
                val cell = if (dayNum in 1..ym.lengthOfMonth()) {
                    val date = ym.atDay(dayNum)
                    val rec = records[date.toString()]
                    val marked = rec != null && (rec.morning || rec.evening || rec.morningBuckets > 0 || rec.eveningBuckets > 0)
                    val selected = date == selectedDate
                    val dayView = TextView(this).apply {
                        text = dayNum.toString() + if (marked) "\n●" else ""
                        gravity = Gravity.CENTER
                        textSize = if (marked) 14f else 15f
                        setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                        setTextColor(if (selected) bg else if (marked) orange else white)
                        background = shape(if (selected) orange else Color.TRANSPARENT, 12)
                        setPadding(0, dp(4), 0, dp(4))
                        setOnClickListener { saveCurrent(); selectedDate = date; render() }
                    }
                    dayView
                } else TextView(this)
                row.addView(cell, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                })
            }
            grid.addView(row)
        }
        monthCard.addView(grid)
        root.addView(monthCard)
        prev.setOnClickListener { saveCurrent(); selectedDate = selectedDate.minusMonths(1); render() }
        next.setOnClickListener { saveCurrent(); selectedDate = selectedDate.plusMonths(1); render() }

        val rec = records[selectedDate.toString()] ?: BurnDay()
        morningCount = rec.morningBuckets
        eveningCount = rec.eveningBuckets
        val dateTitle = selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
            .replaceFirstChar { it.uppercase() }
        root.addView(label("ЗАПИСЬ ЗА ДЕНЬ", 11f, orange, true).apply {
            setPadding(dp(2), dp(20), 0, dp(5))
        })
        root.addView(label(dateTitle, 21f, white, true).apply { setPadding(0, 0, 0, dp(12)) })

        val morningCard = card()
        morningCard.addView(sectionHeader("☀", "Утро", orange))
        morningCheck = check("Топил утром", rec.morning, orange)
        morningCard.addView(morningCheck)
        morningCard.addView(counterRow("Вёдер угля", true))
        root.addView(morningCard)

        val eveningCard = card()
        eveningCard.addView(sectionHeader("◐", "Вечер", red))
        eveningCheck = check("Топил вечером", rec.evening, red)
        eveningCard.addView(eveningCheck)
        eveningCard.addView(counterRow("Вёдер угля", false))
        root.addView(eveningCard)

        val save = actionButton("СОХРАНИТЬ ЗАПИСЬ", orange, bg)
        root.addView(save, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(14) })
        save.setOnClickListener { saveCurrent(); toast("Запись сохранена") }

        val monthRecords = records.values
        val activeDays = records.filterKeys { it.startsWith(ym.toString()) }.values
        val totalFires = activeDays.sumOf { (if (it.morning) 1 else 0) + (if (it.evening) 1 else 0) }
        val totalBuckets = activeDays.sumOf { it.morningBuckets + it.eveningBuckets }
        val stats = card()
        stats.addView(label("ИТОГО ЗА МЕСЯЦ", 11f, muted, true))
        val statRow = LinearLayout(this).apply { setPadding(0, dp(12), 0, 0) }
        statRow.addView(statBlock(totalFires.toString(), "топок", orange), LinearLayout.LayoutParams(0, -2, 1f))
        statRow.addView(statBlock(totalBuckets.toString(), "вёдер угля", red), LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(statRow)
        root.addView(stats)

        root.addView(label("РЕЗЕРВНОЕ КОПИРОВАНИЕ", 11f, muted, true).apply {
            setPadding(dp(2), dp(20), 0, dp(8))
        })
        val backupRow = LinearLayout(this)
        val export = actionButton("СОЗДАТЬ КОПИЮ", panel2, white)
        val restore = actionButton("ВОССТАНОВИТЬ", panel2, white)
        backupRow.addView(export, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(5) })
        backupRow.addView(restore, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(5) })
        root.addView(backupRow)
        export.setOnClickListener { saveCurrent(); createBackup.launch("kalendar-topki-backup.json") }
        restore.setOnClickListener { openBackup.launch(arrayOf("application/json", "text/*")) }
        root.addView(label("Оранжевая точка в календаре — в этот день была топка.", 12f, muted)
            .apply { setPadding(dp(2), dp(12), 0, 0) })
    }

    private fun counterRow(caption: String, morning: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        row.addView(label(caption, 14f, muted), LinearLayout.LayoutParams(0, dp(44), 1f))
        val value = label((if (morning) morningCount else eveningCount).toString(), 20f, white, true)
            .apply { gravity = Gravity.CENTER }
        if (morning) morningCountText = value else eveningCountText = value
        val minus = smallButton("−")
        val plus = smallButton("+")
        minus.setOnClickListener { changeCount(morning, -1) }
        plus.setOnClickListener { changeCount(morning, 1) }
        row.addView(minus, LinearLayout.LayoutParams(dp(42), dp(40)).apply { marginEnd = dp(8) })
        row.addView(value, LinearLayout.LayoutParams(dp(34), dp(40)))
        row.addView(plus, LinearLayout.LayoutParams(dp(42), dp(40)).apply { marginStart = dp(8) })
        return row
    }

    private fun changeCount(morning: Boolean, delta: Int) {
        if (morning) {
            morningCount = (morningCount + delta).coerceAtLeast(0)
            morningCountText.text = morningCount.toString()
        } else {
            eveningCount = (eveningCount + delta).coerceAtLeast(0)
            eveningCountText.text = eveningCount.toString()
        }
    }

    private fun sectionHeader(symbol: String, title: String, accent: Int): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(8)) }
        row.addView(label(symbol, 23f, accent, true), LinearLayout.LayoutParams(dp(38), dp(34)))
        row.addView(label(title, 18f, white, true))
        return row
    }

    private fun check(textValue: String, checked: Boolean, tint: Int) = CheckBox(this).apply {
        text = textValue
        isChecked = checked
        textSize = 15f
        setTextColor(white)
        buttonTintList = ColorStateList.valueOf(tint)
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun statBlock(value: String, caption: String, accent: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(value, 25f, accent, true))
            addView(label(caption, 12f, muted))
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = shape(panel, 18)
        elevation = dp(2).toFloat()
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(10)
        layoutParams = lp
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun smallButton(value: String) = Button(this).apply {
        text = value
        textSize = 22f
        setTextColor(white)
        background = shape(panel2, 12)
        isAllCaps = false
        stateListAnimator = null
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
    }

    private fun actionButton(value: String, color: Int, textColor: Int) = Button(this).apply {
        text = value
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        setTextColor(textColor)
        background = shape(color, 14)
        isAllCaps = false
        stateListAnimator = null
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun space(height: Int) = TextView(this).apply { this.height = dp(height) }

    private fun saveCurrent() {
        if (!::morningCheck.isInitialized) return
        val key = selectedDate.toString()
        val value = BurnDay(morningCheck.isChecked, morningCount, eveningCheck.isChecked, eveningCount)
        if (value.morning || value.evening || value.morningBuckets > 0 || value.eveningBuckets > 0) records[key] = value
        else records.remove(key)
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