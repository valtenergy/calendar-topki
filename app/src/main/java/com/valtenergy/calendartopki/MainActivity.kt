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
import android.widget.ImageView
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
    private var page = 0
    private var burnCount = 0
    private var bucketKg = 20
    private var period = "morning"
    private lateinit var root: LinearLayout
    private lateinit var burnCheck: CheckBox
    private lateinit var countText: TextView
    private lateinit var totalText: TextView
    private lateinit var morningButton: Button
    private lateinit var eveningButton: Button
    private lateinit var weight10Button: Button
    private lateinit var weight20Button: Button

    private val bg = 0xFF090B0F.toInt()
    private val surface = 0xFF1B1C20.toInt()
    private val surface2 = 0xFF292A2F.toInt()
    private val orange = 0xFFFF6500.toInt()
    private val red = 0xFFE33B20.toInt()
    private val white = 0xFFF5F5F5.toInt()
    private val muted = 0xFF9A9DA4.toInt()
    private val green = 0xFF65C98B.toInt()

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
        loadSaved()
        render()
    }

    private fun render() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            clipToPadding = false
            isFillViewport = true
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(18))
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        outer.addView(bottomNavigation())
        setContentView(outer)

        when (page) {
            0 -> calendarPage()
            1 -> statisticsPage()
            else -> backupPage()
        }
    }

    private fun addHeader(title: String, subtitle: String? = null) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_full)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(surface2, 13)
            clipToOutline = true
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        texts.addView(label(title, 20f, white, true))
        if (subtitle != null) texts.addView(label(subtitle, 10f, muted, true).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        root.addView(space(14))
    }

    private fun calendarPage() {
        addHeader("Журнал котла", "УЧЁТ ОТОПЛЕНИЯ")
        val todayRecord = records[LocalDate.now().toString()]
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF422014.toInt(), 0xFF211A1B.toInt())).apply { cornerRadius = dp(18).toFloat() }
            setPadding(dp(15), dp(13), dp(15), dp(13))
            addView(label("НОЯБРЬ  ·  ОТОПИТЕЛЬНЫЙ СЕЗОН", 10f, 0xFFFFB27C.toInt(), true))
            addView(label(if (todayRecord != null) "Сегодня котёл топили 🔥" else "Отопление под контролем", 19f, white, true).apply { setPadding(0, dp(7), 0, dp(3)) })
            addView(label(if (todayRecord != null) "${todayRecord.buckets} вёдер · ${todayRecord.buckets * todayRecord.bucketKg} кг угля" else "Отмечай растопки и следи за расходом угля", 12f, 0xFFD0C6C1.toInt()))
        })
        root.addView(space(17))
        root.addView(label("КАЛЕНДАРЬ", 10f, orange, true))
        root.addView(label("История растопок", 20f, white, true).apply { setPadding(0, dp(3), 0, dp(8)) })
        val calendar = card()
        val monthRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val prev = textButton("‹", 27f)
        val next = textButton("›", 27f)
        monthRow.addView(prev, LinearLayout.LayoutParams(dp(42), dp(42)))
        monthRow.addView(label(selectedDate.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))).replaceFirstChar { it.uppercase() }, 17f, white, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(42), 1f))
        monthRow.addView(next, LinearLayout.LayoutParams(dp(42), dp(42)))
        calendar.addView(monthRow)
        prev.setOnClickListener { selectedDate = selectedDate.minusMonths(1); render() }
        next.setOnClickListener { selectedDate = selectedDate.plusMonths(1); render() }
        val weekdays = LinearLayout(this)
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
            weekdays.addView(label(it, 11f, muted, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(30), 1f))
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
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        background = when {
                            selected -> gradient(orange, red, 11)
                            marked -> shape(0xFF482719.toInt(), 11)
                            date == LocalDate.now() -> outline(orange, 11)
                            else -> shape(0x00202020, 11)
                        }
                        addView(label(day.toString(), 15f, if (selected) white else white, selected))
                        addView(label(if (marked) "🔥" else " ", 9f, orange, true))
                        setOnClickListener { selectedDate = date; page = 0; render() }
                    }
                } else View(this)
                row.addView(cell, LinearLayout.LayoutParams(0, dp(49), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
            calendar.addView(row)
        }
        calendar.addView(label("🔥  была растопка     ◉  выбранный день", 10f, muted).apply { setPadding(0, dp(8), 0, 0) })
        root.addView(calendar)
        root.addView(space(10))
        root.addView(primaryButton("＋   Добавить запись").apply {
            setOnClickListener { page = 3; render() }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(space(14))
        val monthRecords = records.filterKeys { it.startsWith(YearMonth.from(selectedDate).toString()) }.values
        root.addView(statStrip(monthRecords.sumOf { it.buckets * it.bucketKg }, monthRecords.size))
    }

    private fun entryPage() {
        addHeader("Новая запись", selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))))
        val rec = records[selectedDate.toString()]
        burnCount = rec?.buckets ?: 0
        bucketKg = rec?.bucketKg ?: 20
        period = rec?.period ?: "morning"
        val entry = card()
        entry.addView(label("ДАТА ЗАПИСИ", 10f, muted, true))
        entry.addView(label(selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))), 17f, white, true).apply { setPadding(0, dp(6), 0, dp(14)) })
        burnCheck = CheckBox(this).apply {
            text = "Топил котёл"; isChecked = rec != null; textSize = 16f
            setTextColor(white); buttonTintList = ColorStateList.valueOf(orange)
        }
        entry.addView(burnCheck)
        entry.addView(label("ВРЕМЯ РАСТОПКИ", 10f, muted, true).apply { setPadding(0, dp(14), 0, dp(8)) })
        val times = LinearLayout(this)
        morningButton = selectButton("☀  Утро")
        eveningButton = selectButton("☾  Вечер")
        times.addView(morningButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(5) })
        times.addView(eveningButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(5) })
        entry.addView(times)
        morningButton.setOnClickListener { period = "morning"; updateEditorButtons() }
        eveningButton.setOnClickListener { period = "evening"; updateEditorButtons() }
        entry.addView(label("ВЕС ОДНОГО ВЕДРА", 10f, muted, true).apply { setPadding(0, dp(16), 0, dp(8)) })
        val weights = LinearLayout(this)
        weight10Button = selectButton("10 кг")
        weight20Button = selectButton("20 кг")
        weights.addView(weight10Button, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
        weights.addView(weight20Button, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(5) })
        entry.addView(weights)
        weight10Button.setOnClickListener { bucketKg = 10; updateEditorButtons(); updateFuelTotal() }
        weight20Button.setOnClickListener { bucketKg = 20; updateEditorButtons(); updateFuelTotal() }
        entry.addView(label("КОЛИЧЕСТВО ВЁДЕР", 10f, muted, true).apply { setPadding(0, dp(16), 0, dp(8)) })
        val counter = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val minus = textButton("−", 24f)
        val plus = textButton("+", 24f)
        countText = label(burnCount.toString(), 24f, white, true).apply { gravity = Gravity.CENTER }
        counter.addView(minus, LinearLayout.LayoutParams(dp(54), dp(50)))
        counter.addView(countText, LinearLayout.LayoutParams(0, dp(50), 1f))
        counter.addView(plus, LinearLayout.LayoutParams(dp(54), dp(50)))
        entry.addView(counter)
        totalText = label("", 15f, orange, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(2)) }
        entry.addView(totalText)
        root.addView(entry)
        updateEditorButtons()
        updateFuelTotal()
        root.addView(primaryButton("✓   Сохранить").apply {
            setOnClickListener {
                val key = selectedDate.toString()
                if (burnCheck.isChecked && burnCount > 0) records[key] = BurnDay(period, burnCount, bucketKg) else records.remove(key)
                persist()
                page = 0
                render()
                toast("Запись сохранена")
            }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
        root.addView(secondaryButton("Отмена").apply { setOnClickListener { page = 0; render() } }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        minus.setOnClickListener { burnCount = (burnCount - 1).coerceAtLeast(0); countText.text = burnCount.toString(); updateFuelTotal() }
        plus.setOnClickListener { burnCount++; countText.text = burnCount.toString(); updateFuelTotal() }
    }

    private fun statisticsPage() {
        addHeader("Статистика", "РАСХОД УГЛЯ И РАСТОПКИ")
        val year = selectedDate.year
        val items = records.filterKeys { it.startsWith(year.toString()) }.values
        val totalKg = items.sumOf { it.buckets * it.bucketKg }
        val totalBuckets = items.sumOf { it.buckets }
        val top = card()
        top.addView(label("ВСЕГО ЗА ${year} ГОД", 10f, muted, true))
        top.addView(label("${totalKg} кг", 34f, orange, true).apply { setPadding(0, dp(5), 0, 0) })
        top.addView(label("Растопок: ${items.size}     Вёдер: ${totalBuckets}", 13f, white).apply { setPadding(0, dp(3), 0, 0) })
        root.addView(top)
        val best = (1..12).map { m ->
            val ym = YearMonth.of(year, m)
            val values = records.filterKeys { it.startsWith(ym.toString()) }.values
            Triple(ym.format(DateTimeFormatter.ofPattern("LLLL", Locale("ru"))).replaceFirstChar { it.uppercase() }, values.sumOf { it.buckets * it.bucketKg }, values.size)
        }.maxByOrNull { it.second }
        if (best != null && best.second > 0) {
            root.addView(card().apply {
                addView(label("РЕКОРД МЕСЯЦА", 10f, orange, true))
                addView(label(best.first, 16f, white, true).apply { setPadding(0, dp(4), 0, 0) })
                addView(label("${best.second} кг угля", 23f, red, true))
            })
        }
        root.addView(monthlyChart(year))
        root.addView(space(8))
        root.addView(label("ПО МЕСЯЦАМ", 10f, orange, true))
        (1..12).forEach { m ->
            val ym = YearMonth.of(year, m)
            val values = records.filterKeys { it.startsWith(ym.toString()) }.values
            val kg = values.sumOf { it.buckets * it.bucketKg }
            root.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = shape(surface, 10)
                addView(label(ym.format(DateTimeFormatter.ofPattern("LLLL", Locale("ru"))).replaceFirstChar { it.uppercase() }, 13f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(label("${kg} кг   ·   ${values.size} раз", 12f, if (kg > 0) orange else muted, true))
            })
            root.addView(space(4))
        }
    }

    private fun backupPage() {
        addHeader("Резервная копия", "СОХРАННОСТЬ ВАШИХ ДАННЫХ")
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF40200F.toInt(), surface)).apply { cornerRadius = dp(22).toFloat() }
            setPadding(dp(20), dp(30), dp(20), dp(30))
            addView(label("☁", 72f, orange, true).apply { gravity = Gravity.CENTER })
            addView(label("Сохраните данные о топках и статистику", 18f, white, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(8)) })
            addView(label("Резервная копия позволит восстановить все записи при переустановке приложения или переносе на другой телефон.", 13f, muted).apply { gravity = Gravity.CENTER })
        })
        root.addView(space(16))
        root.addView(primaryButton("↓   Создать копию").apply {
            setOnClickListener { createBackup.launch("zhurnal-kotla-backup.json") }
        }, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(secondaryButton("↑   Восстановить").apply {
            setOnClickListener { openBackup.launch(arrayOf("application/json", "text/*")) }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(9) })
        root.addView(space(18))
        root.addView(card().apply {
            addView(label("Записей сохранено", 12f, muted))
            addView(label(records.size.toString(), 28f, orange, true).apply { setPadding(0, dp(4), 0, 0) })
            addView(label("Данные также автоматически хранятся на этом телефоне.", 11f, muted).apply { setPadding(0, dp(4), 0, 0) })
        })
    }

    private fun statStrip(kg: Int, fires: Int) = card().apply {
        addView(label("ИТОГО ЗА МЕСЯЦ", 10f, muted, true))
        val row = LinearLayout(this@MainActivity).apply { setPadding(0, dp(8), 0, 0) }
        row.addView(statTile(kg.toString(), "кг угля", orange), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(statTile(fires.toString(), "растопок", red), LinearLayout.LayoutParams(0, -2, 1f))
        addView(row)
    }

    private fun monthlyChart(year: Int) = card().apply {
        val months = (1..12).map { m ->
            val ym = YearMonth.of(year, m)
            val list = records.filterKeys { it.startsWith(ym.toString()) }.values
            Triple(ym.format(DateTimeFormatter.ofPattern("LLL", Locale("ru"))).replace(".", ""), list.size, list.sumOf { it.buckets * it.bucketKg })
        }
        val maxKg = (months.maxOfOrNull { it.third } ?: 0).coerceAtLeast(1)
        addView(label("РАСХОД УГЛЯ ПО МЕСЯЦАМ", 10f, muted, true))
        addView(View(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(145))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            setWillNotDraw(false)
            tag = months
            setBackgroundColor(surface)
            this.setOnClickListener { }
            this.background = shape(surface, 8)
            this.foreground = null
            // Bars are drawn by the custom view below.
        }.let { chartContainer ->
            object : View(this@MainActivity) {
                private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surface2; strokeWidth = dp(1).toFloat() }
                private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = dp(10).toFloat(); textAlign = Paint.Align.CENTER }
                private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas: Canvas) {
                    val left = dp(5).toFloat(); val right = width - dp(5).toFloat()
                    val top = dp(12).toFloat(); val bottom = height - dp(24).toFloat()
                    val h = bottom - top; val slot = (right - left) / 12f
                    for (i in 0..3) { val y = top + h * i / 3f; canvas.drawLine(left, y, right, y, grid) }
                    months.forEachIndexed { index, item ->
                        val cx = left + slot * (index + .5f); val bw = slot * .58f
                        val barTop = bottom - h * item.third.toFloat() / maxKg.toFloat()
                        if (item.third > 0) {
                            barPaint.shader = android.graphics.LinearGradient(0f, barTop, 0f, bottom, orange, red, android.graphics.Shader.TileMode.CLAMP)
                            canvas.drawRoundRect(RectF(cx - bw / 2, barTop, cx + bw / 2, bottom), dp(4).toFloat(), dp(4).toFloat(), barPaint)
                            barPaint.shader = null
                        }
                        canvas.drawText(item.first, cx, height - dp(6).toFloat(), textPaint)
                    }
                }
            }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(145)) }
        })
    }

    private fun bottomNavigation(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(7), dp(5), dp(7))
            background = shape(0xFF151619.toInt(), 16)
        }
        val labels = listOf("▦\nКалендарь", "▥\nСтатистика", "☁\nРезервная\nкопия")
        labels.forEachIndexed { index, value ->
            val item = TextView(this).apply {
                text = value
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (page == index) orange else muted)
                setPadding(dp(3), dp(4), dp(3), dp(4))
                setOnClickListener { if (page == 3) saveEntrySilently(); page = index; render() }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, dp(55), 1f))
        }
        return nav
    }

    private fun saveEntrySilently() {
        if (!::burnCheck.isInitialized) return
        val key = selectedDate.toString()
        if (burnCheck.isChecked && burnCount > 0) records[key] = BurnDay(period, burnCount, bucketKg) else records.remove(key)
        persist()
    }

    private fun updateFuelTotal() {
        if (::totalText.isInitialized) totalText.text = "${burnCount * bucketKg} кг угля за эту растопку"
    }

    private fun updateEditorButtons() {
        if (!::morningButton.isInitialized) return
        setSelected(morningButton, period == "morning", orange)
        setSelected(eveningButton, period == "evening", red)
        setSelected(weight10Button, bucketKg == 10, orange)
        setSelected(weight20Button, bucketKg == 20, orange)
    }

    private fun setSelected(button: Button, selected: Boolean, accent: Int) {
        button.background = if (selected) gradient(accent, red, 12) else shape(surface2, 12)
        button.setTextColor(white)
    }

    private fun statTile(value: String, caption: String, accent: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(value, 24f, accent, true))
        addView(label(caption, 11f, muted).apply { setPadding(0, dp(2), 0, 0) })
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = shape(surface, 17)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun textButton(value: String, size: Float) = Button(this).apply {
        text = value; textSize = size; setTextColor(white); background = shape(surface2, 12)
        isAllCaps = false; stateListAnimator = null; minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(0, 0, 0, 0)
    }

    private fun selectButton(value: String) = Button(this).apply {
        text = value; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(white)
        background = shape(surface2, 12); isAllCaps = false; stateListAnimator = null
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun primaryButton(value: String) = Button(this).apply {
        text = value; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(white)
        background = gradient(orange, red, 16); isAllCaps = false; stateListAnimator = null
        minHeight = 0; minimumHeight = 0; setPadding(dp(12), 0, dp(12), 0)
    }

    private fun secondaryButton(value: String) = Button(this).apply {
        text = value; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(white)
        background = shape(surface2, 13); isAllCaps = false; stateListAnimator = null
        minHeight = 0; minimumHeight = 0; setPadding(dp(8), 0, dp(8), 0)
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun gradient(start: Int, end: Int, radius: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply { cornerRadius = dp(radius).toFloat() }
    private fun outline(color: Int, radius: Int) = GradientDrawable().apply { setColor(0x00202020); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), color) }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun persist() { getSharedPreferences("burn_calendar", MODE_PRIVATE).edit().putString("records", exportJson()).apply() }
    private fun loadSaved() {
        val raw = getSharedPreferences("burn_calendar", MODE_PRIVATE).getString("records", null) ?: return
        try { importJson(raw) } catch (_: Exception) { }
    }
    private fun exportJson(): String {
        val obj = JSONObject(); val arr = JSONArray()
        records.toSortedMap().forEach { (date, d) -> arr.put(JSONObject().put("date", date).put("period", d.period).put("buckets", d.buckets).put("bucketKg", d.bucketKg)) }
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
}
