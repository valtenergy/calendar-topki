package com.valtenergy.calendartopki

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
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

data class BurnDay(var period: String = "morning", var buckets: Int = 0, var bucketKg: Int = 20)

class MainActivity : ComponentActivity() {
    private val records = mutableMapOf<String, BurnDay>()
    private var selectedDate = LocalDate.now()
    private lateinit var root: LinearLayout
    private lateinit var burnCheck: CheckBox
    private lateinit var countText: TextView
    private lateinit var morningButton: Button
    private lateinit var eveningButton: Button
    private lateinit var weight10Button: Button
    private lateinit var weight20Button: Button
    private var burnCount = 0
    private var bucketKg = 20
    private var period = "morning"

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
        root.addView(label("Записывай только фактическую растопку", 14f, muted).apply {
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
                    val marked = rec != null && rec.buckets > 0
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

        val rec = records[selectedDate.toString()]
        burnCount = rec?.buckets ?: 0
        bucketKg = rec?.bucketKg ?: 20
        period = rec?.period ?: "morning"
        val dateTitle = selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))).replaceFirstChar { it.uppercase() }
        root.addView(label("ФАКТИЧЕСКАЯ РАСТОПКА", 11f, orange, true).apply { setPadding(dp(2), dp(20), 0, dp(5)) })
        root.addView(label(dateTitle, 21f, white, true).apply { setPadding(0, 0, 0, dp(12)) })
        val burnCard = card()
        burnCheck = check("В этот день растапливал котёл", rec != null, orange)
        burnCard.addView(burnCheck)
        burnCard.addView(label("Когда растапливал?", 14f, muted, true).apply { setPadding(0, dp(12), 0, dp(6)) })
        val periodRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        morningButton = smallButton("☀ Утром").apply { textSize = 15f }
        eveningButton = smallButton("◐ Вечером").apply { textSize = 15f }
        periodRow.addView(morningButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
        periodRow.addView(eveningButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(5) })
        burnCard.addView(periodRow)
        morningButton.setOnClickListener { period = "morning"; updateEditorButtons() }
        eveningButton.setOnClickListener { period = "evening"; updateEditorButtons() }
        burnCard.addView(label("Вес одного ведра", 14f, muted, true).apply { setPadding(0, dp(14), 0, dp(6)) })
        val weightRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        weight10Button = smallButton("10 кг")
        weight20Button = smallButton("20 кг")
        weightRow.addView(weight10Button, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) })
        weightRow.addView(weight20Button, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) })
        burnCard.addView(weightRow)
        weight10Button.setOnClickListener { bucketKg = 10; updateEditorButtons() }
        weight20Button.setOnClickListener { bucketKg = 20; updateEditorButtons() }
        burnCard.addView(counterRow())
        root.addView(burnCard)
        updateEditorButtons()
        root.addView(card().apply {
            addView(label("РАСХОД ЗА ЭТУ РАСТОПКУ", 11f, muted, true))
            addView(label((burnCount * bucketKg).toString() + " кг", 25f, orange, true))
        })
        val save = actionButton("СОХРАНИТЬ ЗАПИСЬ", orange, bg)
        root.addView(save, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(4) })
        save.setOnClickListener { saveCurrent(); render(); toast("Запись сохранена") }

        val monthRecords = records.filterKeys { it.startsWith(ym.toString()) }.values
        val yearRecords = records.filterKeys { it.startsWith(selectedDate.year.toString()) }.values
        val totalFires = monthRecords.size
        val totalBuckets = monthRecords.sumOf { it.buckets }
        val totalKg = monthRecords.sumOf { it.buckets * it.bucketKg }
        val yearFires = yearRecords.size
        val yearBuckets = yearRecords.sumOf { it.buckets }
        val yearKg = yearRecords.sumOf { it.buckets * it.bucketKg }
        val allFires = records.size
        val allBuckets = records.values.sumOf { it.buckets }
        val allKg = records.values.sumOf { it.buckets * it.bucketKg }
        val stats = card()
        stats.addView(label("ИТОГО ЗА МЕСЯЦ", 11f, muted, true))
        val statRow = LinearLayout(this).apply { setPadding(0, dp(12), 0, 0) }
        statRow.addView(statBlock(totalFires.toString(), "растопок", orange), LinearLayout.LayoutParams(0, -2, 1f))
        statRow.addView(statBlock(totalBuckets.toString(), "вёдер", red), LinearLayout.LayoutParams(0, -2, 1f))
        statRow.addView(statBlock(totalKg.toString() + " кг", "угля", orange), LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(statRow)
        root.addView(stats)
        val yearStats = card()
        yearStats.addView(label("ИТОГО ЗА " + selectedDate.year + " ГОД", 11f, muted, true))
        val yr = LinearLayout(this).apply { setPadding(0, dp(10), 0, 0) }
        yr.addView(statBlock(yearFires.toString(), "растопок", orange), LinearLayout.LayoutParams(0, -2, 1f))
        yr.addView(statBlock(yearBuckets.toString(), "вёдер", red), LinearLayout.LayoutParams(0, -2, 1f))
        yr.addView(statBlock(yearKg.toString() + " кг", "угля", orange), LinearLayout.LayoutParams(0, -2, 1f))
        yearStats.addView(yr)
        root.addView(yearStats)
        val allStats = card()
        allStats.addView(label("ЗА ВСЁ ВРЕМЯ", 11f, muted, true))
        val ar = LinearLayout(this).apply { setPadding(0, dp(10), 0, 0) }
        ar.addView(statBlock(allFires.toString(), "растопок", orange), LinearLayout.LayoutParams(0, -2, 1f))
        ar.addView(statBlock(allBuckets.toString(), "вёдер", red), LinearLayout.LayoutParams(0, -2, 1f))
        ar.addView(statBlock(allKg.toString() + " кг", "угля", orange), LinearLayout.LayoutParams(0, -2, 1f))
        allStats.addView(ar)
        root.addView(allStats)
        root.addView(monthlyBreakdownCard(selectedDate.year))

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
        root.addView(label("Оранжевая точка — дата фактической растопки. Котёл может гореть несколько дней.", 12f, muted)
            .apply { setPadding(dp(2), dp(12), 0, 0) })
    }

    private fun monthlyBreakdownCard(year: Int): LinearLayout {
        val monthData = (1..12).map { month ->
            val key = YearMonth.of(year, month).toString()
            val items = records.filterKeys { it.startsWith(key) }.values
            Triple(
                YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("LLLL", Locale("ru"))).replaceFirstChar { it.uppercase() },
                items.size,
                items.sumOf { it.buckets * it.bucketKg }
            )
        }
        val maxKg = (monthData.maxOfOrNull { it.third } ?: 0).coerceAtLeast(1)
        return card().apply {
            addView(label("РАСХОД ПО МЕСЯЦАМ · $year", 12f, orange, true))
            addView(label("Сколько угля ушло в каждом месяце", 13f, muted).apply {
                setPadding(0, dp(3), 0, dp(8))
            })
            addView(object : View(this@MainActivity) {
                private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = orange }
                private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = panel2 }
                private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = muted
                    textSize = dp(9).toFloat()
                    textAlign = Paint.Align.CENTER
                }
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val w = width.toFloat()
                    val h = height.toFloat()
                    val bottom = h - dp(23)
                    val top = dp(8).toFloat()
                    val slot = w / 12f
                    val barW = slot * 0.56f
                    monthData.forEachIndexed { index, item ->
                        val left = slot * index + (slot - barW) / 2f
                        val barH = (bottom - top) * item.third.toFloat() / maxKg.toFloat()
                        canvas.drawRoundRect(left, top, left + barW, bottom, dp(4).toFloat(), dp(4).toFloat(), emptyPaint)
                        if (item.third > 0) {
                            canvas.drawRoundRect(left, bottom - barH, left + barW, bottom, dp(4).toFloat(), dp(4).toFloat(), barPaint)
                        }
                        val shortName = listOf("Я","Ф","М","А","М","И","И","А","С","О","Н","Д")[index]
                        canvas.drawText(shortName, left + barW / 2f, h - dp(5).toFloat(), textPaint)
                    }
                }
            }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(142)) })
            addView(space(6))
            monthData.forEachIndexed { index, item ->
                val monthRow = LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(7), 0, dp(7))
                }
                monthRow.addView(label(item.first, 14f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
                monthRow.addView(label(item.second.toString() + " топ.", 12f, muted).apply { gravity = Gravity.END },
                    LinearLayout.LayoutParams(dp(62), -2))
                monthRow.addView(label(item.third.toString() + " кг", 14f, if (item.third > 0) orange else muted, true).apply {
                    gravity = Gravity.END
                }, LinearLayout.LayoutParams(dp(76), -2))
                addView(monthRow)
                if (index != 11) addView(View(this@MainActivity).apply {
                    setBackgroundColor(panel2)
                    layoutParams = LinearLayout.LayoutParams(-1, dp(1))
                })
            }
        }
    }

    private fun counterRow(): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, 0) }
        row.addView(label("Количество вёдер", 14f, muted), LinearLayout.LayoutParams(0, dp(44), 1f))
        countText = label(burnCount.toString(), 20f, white, true).apply { gravity = Gravity.CENTER }
        val minus = smallButton("−")
        val plus = smallButton("+")
        minus.setOnClickListener { burnCount = (burnCount - 1).coerceAtLeast(0); countText.text = burnCount.toString() }
        plus.setOnClickListener { burnCount++; countText.text = burnCount.toString() }
        row.addView(minus, LinearLayout.LayoutParams(dp(42), dp(40)).apply { marginEnd = dp(8) })
        row.addView(countText, LinearLayout.LayoutParams(dp(34), dp(40)))
        row.addView(plus, LinearLayout.LayoutParams(dp(42), dp(40)).apply { marginStart = dp(8) })
        return row
    }

    private fun updateEditorButtons() {
        if (!::morningButton.isInitialized) return
        morningButton.background = shape(if (period == "morning") orange else panel2, 12)
        morningButton.setTextColor(if (period == "morning") bg else white)
        eveningButton.background = shape(if (period == "evening") red else panel2, 12)
        eveningButton.setTextColor(if (period == "evening") bg else white)
        weight10Button.background = shape(if (bucketKg == 10) orange else panel2, 12)
        weight10Button.setTextColor(if (bucketKg == 10) bg else white)
        weight20Button.background = shape(if (bucketKg == 20) orange else panel2, 12)
        weight20Button.setTextColor(if (bucketKg == 20) bg else white)
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
        if (!::burnCheck.isInitialized) return
        val key = selectedDate.toString()
        if (burnCheck.isChecked && burnCount > 0) records[key] = BurnDay(period, burnCount, bucketKg)
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
            arr.put(JSONObject().put("date", date).put("period", d.period).put("buckets", d.buckets).put("bucketKg", d.bucketKg))
        }
        obj.put("version", 2).put("records", arr)
        return obj.toString(2)
    }

    private fun importJson(raw: String) {
        val arr = JSONObject(raw).getJSONArray("records")
        val restored = mutableMapOf<String, BurnDay>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val date = item.getString("date")
            LocalDate.parse(date)
            if (item.has("period")) {
                restored[date] = BurnDay(item.optString("period", "morning"), item.optInt("buckets"), item.optInt("bucketKg", 20))
            } else {
                val morning = item.optBoolean("morning")
                val evening = item.optBoolean("evening")
                val useMorning = morning || !evening
                val buckets = if (useMorning) item.optInt("morningBuckets") else item.optInt("eveningBuckets")
                val kg = if (useMorning) item.optInt("morningKg", 20) else item.optInt("eveningKg", 20)
                if ((morning || evening) && buckets > 0) restored[date] = BurnDay(if (useMorning) "morning" else "evening", buckets, kg)
            }
        }
        records.clear()
        records.putAll(restored)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}