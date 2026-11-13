package com.valtenergy.calendartopki

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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

    private val bg = 0xFF101319.toInt()
    private val surface = 0xFF1A2029.toInt()
    private val surface2 = 0xFF27313D.toInt()
    private val surface3 = 0xFF394451.toInt()
    private val orange = 0xFFFF8A32.toInt()
    private val red = 0xFFEF5148.toInt()
    private val white = 0xFFF4F6F8.toInt()
    private val muted = 0xFF9BA7B5.toInt()
    private val green = 0xFF64C995.toInt()

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            contentResolver.openOutputStream(uri)?.use { it.write(exportJson().toByteArray(Charsets.UTF_8)) }
            toast("Резервная копия сохранена")
        } catch (_: Exception) { toast("Не удалось сохранить копию") }
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            importJson(json); persist(); render(); toast("Резервная копия восстановлена")
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
            setPadding(dp(18), dp(14), dp(18), dp(30))
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        setContentView(scroll)

        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "🔥"; textSize = 25f; gravity = Gravity.CENTER
            background = gradient(0xFF49271D.toInt(), 0xFF29232A.toInt(), 15)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, 0, 0) }
        brand.addView(label("ЖУРНАЛ КОТЛА", 11f, orange, true))
        brand.addView(label("УЧЁТ ОТОПЛЕНИЯ", 10f, muted, true).apply { setPadding(0, dp(3), 0, 0) })
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)
        root.addView(space(18))

        val today = records[LocalDate.now().toString()]
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient(0xFF43291F.toInt(), 0xFF25252C.toInt(), 20)
            setPadding(dp(18), dp(17), dp(18), dp(17))
            addView(label("СЕГОДНЯ  ·  " + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))).uppercase(Locale("ru")), 10f, 0xFFFFB06A.toInt(), true))
            addView(label("Отопление под контролем", 21f, white, true).apply { setPadding(0, dp(9), 0, dp(4)) })
            addView(label("Записывай растопки и следи за расходом угля", 12f, 0xFFC2C5CC.toInt()))
            addView(label(if (today != null) "●  Сегодня была растопка" else "○  Сегодня ещё не отмечено",
                13f, if (today != null) green else 0xFFFFB06A.toInt(), true).apply { setPadding(0, dp(14), 0, 0) })
        })
        root.addView(space(20))

        sectionTitle("КАЛЕНДАРЬ", "История растопок")
        val calendar = card()
        val monthRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val prev = textButton("‹", 27f)
        val next = textButton("›", 27f)
        monthRow.addView(prev, LinearLayout.LayoutParams(dp(42), dp(42)))
        monthRow.addView(label(selectedDate.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))).replaceFirstChar { it.uppercase() },
            17f, white, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(42), 1f))
        monthRow.addView(next, LinearLayout.LayoutParams(dp(42), dp(42)))
        calendar.addView(monthRow)
        prev.setOnClickListener { saveCurrent(); selectedDate = selectedDate.minusMonths(1); render() }
        next.setOnClickListener { saveCurrent(); selectedDate = selectedDate.plusMonths(1); render() }
        val weekdays = LinearLayout(this)
        listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС").forEach {
            weekdays.addView(label(it, 10f, muted, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, dp(30), 1f))
        }
        calendar.addView(weekdays)
        val ym = YearMonth.from(selectedDate)
        val offset = ym.atDay(1).dayOfWeek.value - 1
        val cells = ((offset + ym.lengthOfMonth() + 6) / 7) * 7
        for (week in 0 until cells / 7) {
            val row = LinearLayout(this)
            for (col in 0..6) {
                val day = week * 7 + col - offset + 1
                val cell: View = if (day in 1..ym.lengthOfMonth()) {
                    val date = ym.atDay(day)
                    val marked = (records[date.toString()]?.buckets ?: 0) > 0
                    val selected = date == selectedDate
                    LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                        background = when {
                            selected -> gradient(orange, red, 12)
                            marked -> shape(0xFF3A2B25.toInt(), 12)
                            date == LocalDate.now() -> outline(orange, 12)
                            else -> shape(0x00202020, 12)
                        }
                        addView(label(day.toString(), 14f, if (selected) bg else white, selected))
                        addView(label(if (marked) "●" else " ", 8f, if (selected) bg else orange, true))
                        setOnClickListener { saveCurrent(); selectedDate = date; render() }
                    }
                } else View(this)
                row.addView(cell, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                })
            }
            calendar.addView(row)
        }
        calendar.addView(label("●  растопка      ▢  сегодня", 11f, muted).apply { setPadding(0, dp(9), 0, 0) })
        root.addView(calendar)
        root.addView(space(20))

        val rec = records[selectedDate.toString()]
        burnCount = rec?.buckets ?: 0
        bucketKg = rec?.bucketKg ?: 20
        period = rec?.period ?: "morning"
        sectionTitle("ЗАПИСЬ ЗА ДЕНЬ",
            selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))).replaceFirstChar { it.uppercase() })
        val entry = card()
        burnCheck = CheckBox(this).apply {
            text = "В этот день топил котёл"; isChecked = rec != null; textSize = 15f
            setTextColor(white); buttonTintList = ColorStateList.valueOf(orange)
        }
        entry.addView(burnCheck)
        entry.addView(label("ВРЕМЯ РАСТОПКИ", 10f, muted, true).apply { setPadding(0, dp(15), 0, dp(8)) })
        val times = LinearLayout(this)
        morningButton = selectButton("☀  Утром")
        eveningButton = selectButton("◐  Вечером")
        times.addView(morningButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        times.addView(eveningButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        entry.addView(times)
        morningButton.setOnClickListener { period = "morning"; updateEditorButtons() }
        eveningButton.setOnClickListener { period = "evening"; updateEditorButtons() }
        entry.addView(label("ВЕС ОДНОГО ВЕДРА", 10f, muted, true).apply { setPadding(0, dp(15), 0, dp(8)) })
        val weights = LinearLayout(this)
        weight10Button = selectButton("10 кг"); weight20Button = selectButton("20 кг")
        weights.addView(weight10Button, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) })
        weights.addView(weight20Button, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) })
        entry.addView(weights)
        weight10Button.setOnClickListener { bucketKg = 10; updateEditorButtons(); updateFuelTotal() }
        weight20Button.setOnClickListener { bucketKg = 20; updateEditorButtons(); updateFuelTotal() }
        entry.addView(label("КОЛИЧЕСТВО ВЁДЕР", 10f, muted, true).apply { setPadding(0, dp(15), 0, dp(8)) })
        val counter = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val minus = textButton("−", 24f); val plus = textButton("+", 24f)
        countText = label(burnCount.toString(), 23f, white, true).apply { gravity = Gravity.CENTER }
        counter.addView(minus, LinearLayout.LayoutParams(dp(52), dp(48)))
        counter.addView(countText, LinearLayout.LayoutParams(0, dp(48), 1f))
        counter.addView(plus, LinearLayout.LayoutParams(dp(52), dp(48)))
        entry.addView(counter)
        minus.setOnClickListener { burnCount = (burnCount - 1).coerceAtLeast(0); countText.text = burnCount.toString(); updateFuelTotal() }
        plus.setOnClickListener { burnCount++; countText.text = burnCount.toString(); updateFuelTotal() }
        entry.addView(label((burnCount * bucketKg).toString() + " кг угля за эту растопку", 13f, orange, true).apply {
            tag = "fuelTotal"; setPadding(0, dp(12), 0, 0)
        })
        root.addView(entry)
        updateEditorButtons()
        root.addView(primaryButton("СОХРАНИТЬ ЗАПИСЬ").apply {
            setOnClickListener { saveCurrent(); render(); toast("Запись сохранена") }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10) })
        root.addView(space(22))

        val monthRecords = records.filterKeys { it.startsWith(YearMonth.from(selectedDate).toString()) }.values
        sectionTitle("СТАТИСТИКА", "Итоги за месяц")
        root.addView(card().apply {
            val row = LinearLayout(this@MainActivity)
            row.addView(statTile(monthRecords.size.toString(), "растопок", orange), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(statTile(monthRecords.sumOf { it.buckets }.toString(), "вёдер", red), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(statTile(monthRecords.sumOf { it.buckets * it.bucketKg }.toString(), "кг угля", orange), LinearLayout.LayoutParams(0, -2, 1f))
            addView(row)
        })
        root.addView(yearSummary(selectedDate.year))
        root.addView(monthlyChart(selectedDate.year))

        root.addView(space(10))
        sectionTitle("ДАННЫЕ", "Резервная копия")
        root.addView(card().apply {
            addView(label("Сохрани записи в файл, чтобы перенести их на другой телефон или восстановить позже.", 12f, muted))
            val row = LinearLayout(this@MainActivity).apply { setPadding(0, dp(13), 0, 0) }
            val export = secondaryButton("СОЗДАТЬ КОПИЮ")
            val restore = secondaryButton("ВОССТАНОВИТЬ")
            row.addView(export, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
            row.addView(restore, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
            addView(row)
            export.setOnClickListener { saveCurrent(); createBackup.launch("zhurnal-kotla-backup.json") }
            restore.setOnClickListener { openBackup.launch(arrayOf("application/json", "text/*")) }
        })
        root.addView(label("ЖУРНАЛ КОТЛА  ·  ДАННЫЕ ХРАНЯТСЯ НА ТЕЛЕФОНЕ", 9f, muted, true)
            .apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0) })
    }

    private fun updateFuelTotal() {
        root.findViewWithTag<TextView>("fuelTotal")?.text = (burnCount * bucketKg).toString() + " кг угля за эту растопку"
    }

    private fun yearSummary(year: Int) = card().apply {
        val items = records.filterKeys { it.startsWith(year.toString()) }.values
        addView(label("ИТОГО ЗА " + year + " ГОД", 10f, muted, true))
        val row = LinearLayout(this@MainActivity).apply { setPadding(0, dp(12), 0, 0) }
        row.addView(statTile(items.size.toString(), "растопок", orange), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(statTile(items.sumOf { it.buckets }.toString(), "вёдер", red), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(statTile(items.sumOf { it.buckets * it.bucketKg }.toString(), "кг угля", orange), LinearLayout.LayoutParams(0, -2, 1f))
        addView(row)
    }

    private fun monthlyChart(year: Int) = card().apply {
        val months = (1..12).map { m ->
            val ym = YearMonth.of(year, m)
            val list = records.filterKeys { it.startsWith(ym.toString()) }.values
            Triple(ym.format(DateTimeFormatter.ofPattern("LLL", Locale("ru"))).replace(".", ""), list.size, list.sumOf { it.buckets * it.bucketKg })
        }
        val maxKg = (months.maxOfOrNull { it.third } ?: 0).coerceAtLeast(1)
        addView(label("РАСХОД ПО МЕСЯЦАМ", 10f, muted, true))
        addView(label("Сколько угля использовано", 17f, white, true).apply { setPadding(0, dp(4), 0, dp(12)) })
        addView(object : View(this@MainActivity) {
            private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surface3; strokeWidth = dp(1).toFloat() }
            private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = muted; textSize = dp(10).toFloat(); textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                val left = dp(7).toFloat(); val right = width - dp(7).toFloat()
                val top = dp(10).toFloat(); val bottom = height - dp(27).toFloat()
                val chartH = bottom - top; val slot = (right - left) / 12f
                for (i in 0..3) {
                    val y = top + chartH * i / 3f
                    canvas.drawLine(left, y, right, y, grid)
                }
                months.forEachIndexed { index, item ->
                    val cx = left + slot * (index + .5f); val bw = slot * .58f
                    val barTop = bottom - chartH * item.third.toFloat() / maxKg.toFloat()
                    canvas.drawRoundRect(RectF(cx - bw / 2, top, cx + bw / 2, bottom), dp(4).toFloat(), dp(4).toFloat(), grid)
                    if (item.third > 0) {
                        bar.shader = android.graphics.LinearGradient(0f, barTop, 0f, bottom, orange, red, android.graphics.Shader.TileMode.CLAMP)
                        canvas.drawRoundRect(RectF(cx - bw / 2, barTop, cx + bw / 2, bottom), dp(4).toFloat(), dp(4).toFloat(), bar)
                        bar.shader = null
                    }
                    canvas.drawText(item.first, cx, height - dp(7).toFloat(), txt)
                }
            }
        }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(150)) })
        addView(space(8))
        months.forEachIndexed { i, item ->
            val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(7), 0, dp(7)) }
            row.addView(label(item.first.replaceFirstChar { it.uppercase() }, 13f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label(item.second.toString() + " раст.", 11f, muted).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(66), -2))
            row.addView(label(item.third.toString() + " кг", 13f, if (item.third > 0) orange else muted, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(70), -2))
            addView(row)
            if (i < 11) addView(View(this@MainActivity).apply {
                setBackgroundColor(surface2); layoutParams = LinearLayout.LayoutParams(-1, dp(1))
            })
        }
    }

    private fun updateEditorButtons() {
        if (!::morningButton.isInitialized) return
        setSelected(morningButton, period == "morning", orange)
        setSelected(eveningButton, period == "evening", red)
        setSelected(weight10Button, bucketKg == 10, orange)
        setSelected(weight20Button, bucketKg == 20, orange)
    }

    private fun setSelected(button: Button, selected: Boolean, accent: Int) {
        button.background = if (selected) gradient(accent, accent, 12) else shape(surface2, 12)
        button.setTextColor(if (selected) bg else white)
    }

    private fun sectionTitle(kicker: String, title: String) {
        root.addView(label(kicker, 10f, orange, true).apply { letterSpacing = .12f })
        root.addView(label(title, 20f, white, true).apply { setPadding(0, dp(4), 0, dp(11)) })
    }

    private fun statTile(value: String, caption: String, accent: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(value, 24f, accent, true))
        addView(label(caption, 11f, muted).apply { setPadding(0, dp(2), 0, 0) })
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = shape(surface, 18)
        elevation = dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun textButton(value: String, size: Float) = Button(this).apply {
        text = value; textSize = size; setTextColor(white); background = shape(surface2, 13)
        isAllCaps = false; stateListAnimator = null
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(0, 0, 0, 0)
    }

    private fun selectButton(value: String) = Button(this).apply {
        text = value; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(white)
        background = shape(surface2, 12); isAllCaps = false; stateListAnimator = null
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun primaryButton(value: String) = Button(this).apply {
        text = value; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(bg)
        background = gradient(orange, red, 15); isAllCaps = false; stateListAnimator = null
        minHeight = 0; minimumHeight = 0; setPadding(dp(12), 0, dp(12), 0)
    }

    private fun secondaryButton(value: String) = Button(this).apply {
        text = value; textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(white)
        background = shape(surface2, 12); isAllCaps = false; stateListAnimator = null
        minHeight = 0; minimumHeight = 0; setPadding(dp(6), 0, dp(6), 0)
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun gradient(start: Int, end: Int, radius: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)
    ).apply { cornerRadius = dp(radius).toFloat() }

    private fun outline(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(0x00202020); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), color)
    }

    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }

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
        val obj = JSONObject(); val arr = JSONArray()
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
            val item = arr.getJSONObject(i); val date = item.getString("date"); LocalDate.parse(date)
            if (item.has("period")) {
                restored[date] = BurnDay(item.optString("period", "morning"), item.optInt("buckets"), item.optInt("bucketKg", 20))
            } else {
                val morning = item.optBoolean("morning"); val evening = item.optBoolean("evening")
                val useMorning = morning || !evening
                val buckets = if (useMorning) item.optInt("morningBuckets") else item.optInt("eveningBuckets")
                val kg = if (useMorning) item.optInt("morningKg", 20) else item.optInt("eveningKg", 20)
                if ((morning || evening) && buckets > 0) restored[date] = BurnDay(if (useMorning) "morning" else "evening", buckets, kg)
            }
        }
        records.clear(); records.putAll(restored)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
