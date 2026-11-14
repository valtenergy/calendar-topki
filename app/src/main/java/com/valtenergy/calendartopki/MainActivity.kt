package com.valtenergy.calendartopki

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Session(var buckets:Int=0,var kg:Int=20)
data class BurnDay(var morning:Session?=null,var evening:Session?=null)

class MainActivity:ComponentActivity(){
 private val records=mutableMapOf<String,BurnDay>()
 private var selected=LocalDate.now(); private var month=YearMonth.now()
 private var screen="calendar"; private var period="morning"; private var buckets=0; private var kg=20
 private lateinit var page:LinearLayout
 private val bg=0xFF0D1015.toInt(); private val panel=0xFF191E26.toInt(); private val panel2=0xFF242B35.toInt()
 private val orange=0xFFFF861F.toInt(); private val red=0xFFE94D32.toInt(); private val white=0xFFF4F5F7.toInt()
 private val muted=0xFF9AA5B2.toInt(); private val green=0xFF32D583.toInt()
 private val createBackup=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){u->if(u!=null)try{contentResolver.openOutputStream(u)?.use{it.write(exportJson().toByteArray())};toast("Резервная копия сохранена")}catch(e:Exception){toast("Не удалось сохранить копию")}}
 private val openBackup=registerForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)try{val s=contentResolver.openInputStream(u)?.bufferedReader()?.use{it.readText()}?:error("empty");importJson(s);persist();render();toast("Данные восстановлены")}catch(e:Exception){toast("Не удалось восстановить копию")}}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=bg;window.navigationBarColor=bg;load();render()}
 private fun render(){
  val outer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
  val scroll=ScrollView(this);page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(18));setBackgroundColor(bg)}
  scroll.addView(page);outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));outer.addView(navBar(),LinearLayout.LayoutParams(-1,dp(68)));setContentView(outer)
  when(screen){"calendar"->calendarScreen();"stats"->statsScreen();"backup"->backupScreen();else->settingsScreen()}
 }
 private fun header(k:String,t:String){page.addView(txt(k,10,orange,true));page.addView(txt(t,24,white,true).apply{setPadding(0,dp(4),0,dp(14))})}
 private fun calendarScreen(){
  val h=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  h.addView(ImageView(this).apply{setImageResource(R.drawable.ic_launcher_full);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(dp(54),dp(54)))
  val n=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,0,0)}
  n.addView(txt("Журнал котла",22,white,true));n.addView(txt("Учёт растопок и расхода угля",12,muted));h.addView(n);page.addView(h);page.addView(space(14))
  val today=records[LocalDate.now().toString()]
  card().also{it.addView(txt("СЕГОДНЯ · "+LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM",Locale("ru"))).uppercase(Locale("ru")),10,0xFFFFB46D.toInt(),true));it.addView(txt("Отопление под контролем",21,white,true).apply{setPadding(0,dp(7),0,dp(4))});it.addView(txt(if(today?.let{it.morning!=null||it.evening!=null}==true)"🔥 Сегодня котёл топили" else "Сегодня растопка ещё не отмечена",13,if(today==null)muted else green,true));page.addView(it)}
  page.addView(space(16));header("КАЛЕНДАРЬ","История растопок")
  card().also{c->
   val r=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
   r.addView(action("‹",28){month=month.minusMonths(1);render()},LinearLayout.LayoutParams(dp(44),dp(42)))
   r.addView(txt(month.format(DateTimeFormatter.ofPattern("LLLL yyyy",Locale("ru"))).replaceFirstChar{it.uppercase()},17,white,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,dp(42),1f))
   r.addView(action("›",28){month=month.plusMonths(1);render()},LinearLayout.LayoutParams(dp(44),dp(42)));c.addView(r)
   val wd=LinearLayout(this);listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach{wd.addView(txt(it,11,muted,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,dp(30),1f))};c.addView(wd)
   val off=month.atDay(1).dayOfWeek.value-1;val count=((off+month.lengthOfMonth()+6)/7)*7
   for(w in 0 until count/7){val row=LinearLayout(this);for(col in 0..6){val d=w*7+col-off+1
    val cell=if(d in 1..month.lengthOfMonth()){val date=month.atDay(d);val rec=records[date.toString()];val marked=rec?.let{it.morning!=null||it.evening!=null}==true
     LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=when{date==selected->grad(orange,red,12);marked->shape(0xFF38291F.toInt(),12);date==LocalDate.now()->stroke(orange,12);else->shape(panel,12)}
      addView(txt(d.toString(),14,if(date==selected)bg else white,true));addView(txt(if(marked)"🔥" else " ",10,orange));setOnClickListener{selected=date;loadEditor();render()}}
    }else View(this)
    row.addView(cell,LinearLayout.LayoutParams(0,dp(48),1f).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})
   };c.addView(row)}
   c.addView(txt("🔥 растопка     Оранжевая рамка — сегодня",10,muted).apply{setPadding(0,dp(8),0,0)});page.addView(c)
  }
  page.addView(space(14));header("НОВАЯ ЗАПИСЬ",selected.format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale("ru"))).replaceFirstChar{it.uppercase()})
  card().also{c->
   val sw=LinearLayout(this);sw.addView(pill("☀ Утро",period=="morning"){period="morning";loadEditor();render()},LinearLayout.LayoutParams(0,dp(48),1f).apply{marginEnd=dp(5)});sw.addView(pill("☾ Вечер",period=="evening"){period="evening";loadEditor();render()},LinearLayout.LayoutParams(0,dp(48),1f).apply{marginStart=dp(5)});c.addView(sw)
   val s=getSession(selected,period);buckets=s?.buckets?:0;kg=s?.kg?:20
   c.addView(txt("КОЛИЧЕСТВО ВЁДЕР УГЛЯ",10,muted,true).apply{setPadding(0,dp(16),0,dp(8))})
   val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
   row.addView(action("−",24){buckets=(buckets-1).coerceAtLeast(0);render()},LinearLayout.LayoutParams(dp(52),dp(48)))
   row.addView(txt(buckets.toString(),24,white,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,dp(48),1f))
   row.addView(action("+",24){buckets++;render()},LinearLayout.LayoutParams(dp(52),dp(48)));c.addView(row)
   c.addView(txt("ВЕС ОДНОГО ВЕДРА",10,muted,true).apply{setPadding(0,dp(14),0,dp(8))})
   val weights=LinearLayout(this);weights.addView(pill("10 кг",kg==10){kg=10;render()},LinearLayout.LayoutParams(0,dp(42),1f).apply{marginEnd=dp(5)});weights.addView(pill("20 кг",kg==20){kg=20;render()},LinearLayout.LayoutParams(0,dp(42),1f).apply{marginStart=dp(5)});c.addView(weights)
   c.addView(txt((buckets*kg).toString()+" кг угля за эту растопку",14,orange,true).apply{setPadding(0,dp(13),0,0)})
  }
  page.addView(primary("СОХРАНИТЬ ЗАПИСЬ"){saveEditor();toast("Запись сохранена");render()},LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(10)})
  page.addView(space(14));val m=monthRecords(month);val st=card();st.addView(txt("ИТОГО ЗА МЕСЯЦ",10,muted,true));val row=LinearLayout(this);row.addView(metric(m.size.toString(),"растопок"),LinearLayout.LayoutParams(0,-2,1f));row.addView(metric(m.sumOf{it.buckets}.toString(),"вёдер"),LinearLayout.LayoutParams(0,-2,1f));row.addView(metric(m.sumOf{it.buckets*it.kg}.toString(),"кг угля"),LinearLayout.LayoutParams(0,-2,1f));st.addView(row);page.addView(st)
 }
 private fun statsScreen(){header("АНАЛИТИКА","Статистика");val all=records.values.flatMap{listOfNotNull(it.morning,it.evening)};val c=card();val r=LinearLayout(this);r.addView(metric(all.size.toString(),"растопок"),LinearLayout.LayoutParams(0,-2,1f));r.addView(metric(all.sumOf{it.buckets}.toString(),"вёдер"),LinearLayout.LayoutParams(0,-2,1f));r.addView(metric(all.sumOf{it.buckets*it.kg}.toString(),"кг угля"),LinearLayout.LayoutParams(0,-2,1f));c.addView(r);page.addView(c);page.addView(space(12));header("РАСХОД","По месяцам")
  for(i in 1..12){val ym=YearMonth.of(month.year,i);val ss=monthRecords(ym);val used=ss.sumOf{it.buckets*it.kg};val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};row.addView(txt(ym.format(DateTimeFormatter.ofPattern("LLLL",Locale("ru"))).replaceFirstChar{it.uppercase()},14,white,true),LinearLayout.LayoutParams(0,dp(42),1f));row.addView(txt(ss.size.toString()+" топок",12,muted),LinearLayout.LayoutParams(dp(78),-2));row.addView(txt(used.toString()+" кг",14,orange,true).apply{gravity=Gravity.END},LinearLayout.LayoutParams(dp(76),-2));page.addView(row);page.addView(View(this).apply{setBackgroundColor(panel2);layoutParams=LinearLayout.LayoutParams(-1,dp(1))})}
 }
 private fun backupScreen(){header("БЕЗОПАСНОСТЬ","Резервная копия");card().also{it.gravity=Gravity.CENTER_HORIZONTAL;it.addView(txt("☁",54,orange,true));it.addView(txt("Твои записи всегда под рукой",18,white,true).apply{gravity=Gravity.CENTER;setPadding(0,dp(8),0,dp(5))});it.addView(txt("Сохрани файл копии или восстанови данные из ранее созданного файла.",12,muted).apply{gravity=Gravity.CENTER});page.addView(it)}
  page.addView(primary("☁  СОЗДАТЬ РЕЗЕРВНУЮ КОПИЮ"){createBackup.launch("zhurnal-kotla-backup.json")},LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(10)})
  page.addView(outlineButton("↓  ВОССТАНОВИТЬ ИЗ КОПИИ"){openBackup.launch(arrayOf("application/json","text/*"))},LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(10)})
  page.addView(space(16));card().also{it.addView(txt("СОВЕТ",10,orange,true));it.addView(txt("Не удаляй файл резервной копии после восстановления. Храни его отдельно.",13,white).apply{setPadding(0,dp(7),0,0)});page.addView(it)}
 }
 private fun settingsScreen(){header("ПРИЛОЖЕНИЕ","Настройки");card().also{it.addView(txt("ТЕМА ОФОРМЛЕНИЯ",10,orange,true));it.addView(txt("Тёмная с оранжевыми акцентами",15,white,true).apply{setPadding(0,dp(8),0,0)});it.addView(txt("Журнал котла · версия 7.2",12,muted).apply{setPadding(0,dp(14),0,0)});page.addView(it)}}
 private fun navBar()=LinearLayout(this).apply{setBackgroundColor(0xFF151920.toInt());gravity=Gravity.CENTER;listOf(Triple("calendar","▦","Календарь"),Triple("stats","▥","Статистика"),Triple("backup","☁","Копия"),Triple("settings","⚙","Настройки")).forEach{(id,ico,label)->addView(LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=if(screen==id)grad(orange,red,12)else shape(0x00111111,12);setPadding(dp(5),dp(6),dp(5),dp(5));addView(txt(ico,21,if(screen==id)bg else white,true).apply{gravity=Gravity.CENTER});addView(txt(label,10,if(screen==id)bg else muted,true).apply{gravity=Gravity.CENTER});setOnClickListener{screen=id;render()}},LinearLayout.LayoutParams(0,dp(58),1f).apply{setMargins(dp(3),dp(4),dp(3),dp(4))})}}
 private fun loadEditor(){val s=getSession(selected,period);buckets=s?.buckets?:0;kg=s?.kg?:20}
 private fun getSession(d:LocalDate,p:String)=records[d.toString()]?.let{if(p=="morning")it.morning else it.evening}
 private fun saveEditor(){val d=records.getOrPut(selected.toString()){BurnDay()};val s=if(buckets<=0)null else Session(buckets,kg);if(period=="morning")d.morning=s else d.evening=s;if(d.morning==null&&d.evening==null)records.remove(selected.toString());persist()}
 private fun monthRecords(ym:YearMonth)=records.filterKeys{it.startsWith(ym.toString())}.values.flatMap{listOfNotNull(it.morning,it.evening)}
 private fun metric(v:String,l:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(txt(v,23,orange,true));addView(txt(l,11,muted))}
 private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=shape(panel,18);setPadding(dp(15),dp(14),dp(15),dp(14));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)}}
 private fun txt(s:String,size:Int,color:Int,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(color);if(bold)setTypeface(null,Typeface.BOLD)}
 private fun action(s:String,size:Int,click:()->Unit)=Button(this).apply{text=s;textSize=size.toFloat();setTextColor(white);background=shape(panel2,12);isAllCaps=false;minHeight=0;minimumHeight=0;minWidth=0;minimumWidth=0;stateListAnimator=null;setOnClickListener{click()}}
 private fun pill(s:String,sel:Boolean,click:()->Unit)=Button(this).apply{text=s;textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(if(sel)bg else white);background=if(sel)grad(orange,red,12)else shape(panel2,12);isAllCaps=false;minHeight=0;minimumHeight=0;stateListAnimator=null;setOnClickListener{click()}}
 private fun primary(s:String,click:()->Unit)=Button(this).apply{text=s;textSize=13f;setTypeface(null,Typeface.BOLD);setTextColor(bg);background=grad(orange,red,14);isAllCaps=false;minHeight=0;minimumHeight=0;stateListAnimator=null;setOnClickListener{click()}}
 private fun outlineButton(s:String,click:()->Unit)=Button(this).apply{text=s;textSize=13f;setTextColor(white);background=stroke(orange,14);isAllCaps=false;minHeight=0;minimumHeight=0;stateListAnimator=null;setOnClickListener{click()}}
 private fun shape(color:Int,r:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r).toFloat()}
 private fun grad(a:Int,b:Int,r:Int)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply{cornerRadius=dp(r).toFloat()}
 private fun stroke(color:Int,r:Int)=GradientDrawable().apply{setColor(0xFF171B22.toInt());cornerRadius=dp(r).toFloat();setStroke(dp(1),color)}
 private fun space(h:Int)=View(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(h))}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
 private fun persist(){getSharedPreferences("burn_calendar",MODE_PRIVATE).edit().putString("records",exportJson()).apply()}
 private fun load(){val s=getSharedPreferences("burn_calendar",MODE_PRIVATE).getString("records",null)?:return;try{importJson(s)}catch(_:Exception){}}
 private fun exportJson():String{val a=JSONArray();records.toSortedMap().forEach{(date,d)->a.put(JSONObject().put("date",date).put("morningBuckets",d.morning?.buckets?:0).put("morningKg",d.morning?.kg?:20).put("eveningBuckets",d.evening?.buckets?:0).put("eveningKg",d.evening?.kg?:20))};return JSONObject().put("version",3).put("records",a).toString(2)}
 private fun importJson(raw:String){val a=JSONObject(raw).getJSONArray("records");val restored=mutableMapOf<String,BurnDay>();for(i in 0 until a.length()){val o=a.getJSONObject(i);val date=o.getString("date");LocalDate.parse(date);val day=if(o.has("period")){val s=Session(o.optInt("buckets"),o.optInt("bucketKg",20));if(o.optString("period")=="evening")BurnDay(null,s)else BurnDay(s,null)}else{val m=if(o.optInt("morningBuckets")>0)Session(o.optInt("morningBuckets"),o.optInt("morningKg",20))else null;val e=if(o.optInt("eveningBuckets")>0)Session(o.optInt("eveningBuckets"),o.optInt("eveningKg",20))else null;if(m==null&&e==null&&o.optInt("buckets")>0){val s=Session(o.optInt("buckets"),o.optInt("bucketKg",20));if(o.optString("period")=="evening")BurnDay(null,s)else BurnDay(s,null)}else BurnDay(m,e)};if(day.morning!=null||day.evening!=null)restored[date]=day};records.clear();records.putAll(restored)}
}