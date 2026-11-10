package com.example.marstekwidget

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private class AC(val dark: Boolean) {
        val bg = Color.parseColor(if (dark) "#0F1319" else "#F4F6FA")
        val card = Color.parseColor(if (dark) "#1A212B" else "#FFFFFF")
        val text = Color.parseColor(if (dark) "#F3F4F6" else "#111827")
        val sub = Color.parseColor(if (dark) "#9CA3AF" else "#6B7280")
        val accent = Color.parseColor(if (dark) "#3B82F6" else "#2563EB")
        val field = Color.parseColor(if (dark) "#11161D" else "#F1F3F8")
        val stroke = Color.parseColor(if (dark) "#2A323D" else "#E5E7EB")
    }

    private lateinit var p: Prefs
    private val groups = mutableListOf<String>()

    private var etEmail: EditText? = null
    private var etPass: EditText? = null
    private var etCap: EditText? = null
    private var etAvg: EditText? = null
    private var etMin: EditText? = null
    private var etStreet: EditText? = null
    private var etHouse: EditText? = null
    private var spGroup: Spinner? = null
    private var spRegion: Spinner? = null
    private var etChannel: EditText? = null
    private var groupAdapter: ArrayAdapter<String>? = null
    private var regionAdapter: ArrayAdapter<String>? = null
    private val regionList = mutableListOf<RegionDso>()
    private val regionLabels = mutableListOf<String>()
    private var themeGroup: RadioGroup? = null
    private var tvStatus: TextView? = null
    private var tvDiag: TextView? = null
    private var tvGroupInfo: TextView? = null
    private var selStreet: YasnoAddress.Item? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        for (i in 1..60) groups.add("$i.1")
        if (p.group.isNotBlank() && !groups.contains(p.group)) groups.add(p.group)
        regionList.add(RegionDso(p.regionId, p.dsoId, p.regionLabel))
        regionLabels.add(p.regionLabel)
        build()
        loadRegions()
        loadGroups()
    }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun isDark(): Boolean = when (p.appTheme) {
        1 -> false
        2 -> true
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) g.setStroke(dp(1), stroke)
        return g
    }

    private fun num(e: EditText?, def: Double): Double =
        e?.text?.toString()?.replace(',', '.')?.trim()?.toDoubleOrNull() ?: def

    private fun saveFields() {
        etEmail?.let { p.email = it.text.toString().trim() }
        etPass?.let { p.password = it.text.toString() }
        if (etCap != null) p.capacity = num(etCap, 2.0)
        if (etAvg != null) p.avgW = num(etAvg, 220.0)
        if (etMin != null) p.minSoc = num(etMin, 10.0)
        spGroup?.selectedItem?.toString()?.let { p.group = it }
        etChannel?.let {
            val v = it.text.toString().trim()
            if (Telegram.channelName(v) != Telegram.channelName(p.channel)) { p.tgEvents = ""; p.tgTitle = "" }
            p.channel = v
        }
        themeGroup?.let { p.theme = (it.checkedRadioButtonId - 100).coerceIn(0, 2) }
    }

    private fun build() {
        val c = AC(isDark())
        window.statusBarColor = c.bg
        window.navigationBarColor = c.bg
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val frame = FrameLayout(this)
        frame.setBackgroundColor(c.bg)
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(32), dp(16), dp(60))

        fun card(title: String, hint: String?): LinearLayout {
            val l = LinearLayout(this)
            l.orientation = LinearLayout.VERTICAL
            l.background = rounded(c.card, 18, c.stroke)
            l.setPadding(dp(14), dp(14), dp(14), dp(14))
            val tt = TextView(this)
            tt.text = title; tt.textSize = 15f; tt.typeface = Typeface.DEFAULT_BOLD; tt.setTextColor(c.text)
            l.addView(tt)
            if (hint != null) {
                val h = TextView(this)
                h.text = hint; h.textSize = 12f; h.setTextColor(c.sub)
                l.addView(h)
            }
            val lp = LinearLayout.LayoutParams(match, wrap)
            lp.topMargin = dp(12)
            col.addView(l, lp)
            return l
        }

        fun caption(parent: LinearLayout, s: String) {
            val t = TextView(this)
            t.text = s; t.textSize = 12f; t.setTextColor(c.sub); t.setPadding(0, dp(10), 0, dp(4))
            parent.addView(t)
        }

        fun field(parent: LinearLayout, label: String, value: String, type: Int, hintText: String = ""): EditText {
            caption(parent, label)
            val e = EditText(this)
            e.setSingleLine()
            e.inputType = type
            e.setText(value)
            e.hint = hintText
            e.textSize = 15f
            e.setTextColor(c.text)
            e.setHintTextColor(c.sub)
            e.background = rounded(c.field, 12, c.stroke)
            e.setPadding(dp(12), dp(10), dp(12), dp(10))
            parent.addView(e, LinearLayout.LayoutParams(match, wrap))
            return e
        }

        fun button(caption: String, primary: Boolean, onClick: () -> Unit): Button {
            val b = Button(this)
            b.text = caption
            b.isAllCaps = false
            b.textSize = 15f
            b.setTextColor(if (primary) Color.WHITE else c.text)
            b.background = rounded(if (primary) c.accent else c.field, 14, if (primary) null else c.stroke)
            b.setOnClickListener { onClick() }
            return b
        }

        val numType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val txtType = InputType.TYPE_CLASS_TEXT

        // ---------- шапка ----------
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.marstek_logo)
        header.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.setPadding(dp(12), 0, 0, 0)
        val h1 = TextView(this)
        h1.text = "Marstek віджет"; h1.textSize = 22f; h1.typeface = Typeface.DEFAULT_BOLD; h1.setTextColor(c.text)
        val h2 = TextView(this)
        h2.text = "Venus A · моніторинг станції"; h2.textSize = 12f; h2.setTextColor(c.sub)
        titles.addView(h1); titles.addView(h2)
        header.addView(titles, LinearLayout.LayoutParams(0, wrap, 1f))
        val toggle = TextView(this)
        toggle.text = if (c.dark) "☀" else "☾"
        toggle.textSize = 20f
        toggle.gravity = Gravity.CENTER
        toggle.setTextColor(c.text)
        toggle.background = rounded(c.card, 22, c.stroke)
        toggle.setOnClickListener {
            saveFields()
            p.appTheme = if (c.dark) 1 else 2
            build()
        }
        header.addView(toggle, LinearLayout.LayoutParams(dp(44), dp(44)))
        col.addView(header, LinearLayout.LayoutParams(match, wrap))

        // ---------- акаунт ----------
        val acc = card("Акаунт Marstek", "Той самий, де прив'язана станція")
        etEmail = field(acc, "Email", p.email, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        etPass = field(acc, "Пароль", p.password, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

        // ---------- станція ----------
        val st = card("Станція і прогноз", "Потрібні для розрахунку «вистачить / не вистачить»")
        etCap = field(st, "Ємність, кВт·год", p.capacity.toString(), numType)
        etAvg = field(st, "Середнє споживання, Вт", p.avgW.toInt().toString(), numType)
        etMin = field(st, "Мінімальний заряд станції, %", p.minSoc.toInt().toString(), numType)

        // ---------- світло ----------
        val li = card("Світло · плановий графік", "Yasno: Київ і, якщо доступно, Київська область")
        tvGroupInfo = TextView(this)
        tvGroupInfo!!.textSize = 13f
        tvGroupInfo!!.setTextColor(c.text)
        tvGroupInfo!!.setPadding(0, dp(8), 0, 0)
        li.addView(tvGroupInfo)
        updateGroupInfo()

        caption(li, "Регіон і оператор")
        val rgBox = LinearLayout(this)
        rgBox.background = rounded(c.field, 12, c.stroke)
        rgBox.setPadding(dp(6), dp(2), dp(6), dp(2))
        val spr = Spinner(this)
        val adr = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, regionLabels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as TextView).setTextColor(c.text)
                return v
            }
        }
        adr.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spr.adapter = adr
        spr.setSelection(0)
        spr.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val r = regionList.getOrNull(pos) ?: return
                if (r.regionId == p.regionId && r.dsoId == p.dsoId) return
                p.regionId = r.regionId
                p.dsoId = r.dsoId
                p.regionLabel = r.label.substringBefore(" · ")
                selStreet = null
                loadGroups()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        rgBox.addView(spr, LinearLayout.LayoutParams(match, wrap))
        li.addView(rgBox, LinearLayout.LayoutParams(match, wrap))
        spRegion = spr
        regionAdapter = adr

        caption(li, "Група")
        val spBox = LinearLayout(this)
        spBox.background = rounded(c.field, 12, c.stroke)
        spBox.setPadding(dp(6), dp(2), dp(6), dp(2))
        val sp = Spinner(this)
        val ad = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, groups) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as TextView).setTextColor(c.text)
                return v
            }
        }
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp.adapter = ad
        sp.setSelection(groups.indexOf(p.group).coerceAtLeast(0))
        spBox.addView(sp, LinearLayout.LayoutParams(match, wrap))
        li.addView(spBox, LinearLayout.LayoutParams(match, wrap))
        spGroup = sp
        groupAdapter = ad

        caption(li, "Або знайти за адресою")
        etStreet = field(li, "Вулиця", "", txtType, "наприклад, Хрещатик")
        li.addView(button("Знайти вулицю", false) { searchStreet() },
            LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(6) })
        etHouse = field(li, "Будинок", "", txtType, "наприклад, 22")
        li.addView(button("Знайти будинок і визначити групу", false) { searchHouse() },
            LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(6) })

        // ---------- СвітлоБот ----------
        val tg = card("СвітлоБот (Telegram)", "Фактичні відключення з публічного каналу. Вхід в Telegram не потрібен")
        etChannel = field(tg, "Посилання на канал", p.channel, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, "https://t.me/назва_каналу")

        // ---------- тема віджетів ----------
        val th = card("Тема віджетів", "Світла, темна або за системною")
        val rg = RadioGroup(this)
        rg.orientation = RadioGroup.HORIZONTAL
        listOf("Авто", "Світла", "Темна").forEachIndexed { i, n ->
            val rb = RadioButton(this)
            rb.text = n
            rb.id = 100 + i
            rb.setTextColor(c.text)
            rb.isChecked = (p.theme == i)
            rg.addView(rb)
        }
        th.addView(rg)
        themeGroup = rg

        // ---------- збереження ----------
        val save = button("Зберегти і оновити віджети", true) {
            saveFields()
            p.token = ""
            tvStatus?.text = "Завантаження…"
            updateGroupInfo()
            UpdateWorker.schedule(this)
            Thread {
                Refresher.refresh(this)
                runOnUiThread { showInfo() }
            }.start()
        }
        col.addView(save, LinearLayout.LayoutParams(match, dp(52)).apply { topMargin = dp(16) })

        // ---------- як додати + статус ----------
        val how = card("Як додати віджети", null)
        val howText = TextView(this)
        howText.text = "Утримайте палець на робочому столі → Віджети → «Marstek віджет». " +
                "Доступні: кільця 2×1; батарея, навантаження, відключення, прогноз, графік (4×1); план і факт (4×2). " +
                "Віджет оновлюється сам приблизно раз на 15 хвилин, а торкання по ньому або по кнопці ⟳ в куті оновлює одразу."
        howText.textSize = 13f
        howText.setTextColor(c.sub)
        howText.setPadding(0, dp(6), 0, 0)
        how.addView(howText)

        val stc = card("Стан", null)
        tvStatus = TextView(this)
        tvStatus!!.textSize = 13f
        tvStatus!!.setTextColor(c.text)
        tvStatus!!.setPadding(0, dp(6), 0, 0)
        stc.addView(tvStatus)
        tvDiag = TextView(this)
        tvDiag!!.textSize = 11f
        tvDiag!!.setTextColor(c.sub)
        tvDiag!!.setTextIsSelectable(true)
        tvDiag!!.visibility = View.GONE
        val diagBtn = button("Показати діагностику", false) {
            val d = tvDiag!!
            d.visibility = if (d.visibility == View.GONE) View.VISIBLE else View.GONE
        }
        stc.addView(diagBtn, LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(8) })
        stc.addView(tvDiag)
        showInfo()

        scroll.addView(col)
        frame.addView(scroll, FrameLayout.LayoutParams(match, match))

        val footer = TextView(this)
        footer.text = "made by igorguiv"
        footer.textSize = 10f
        footer.setTextColor(c.sub)
        footer.alpha = 0.8f
        val flp = FrameLayout.LayoutParams(wrap, wrap, Gravity.BOTTOM or Gravity.END)
        flp.setMargins(0, 0, dp(14), dp(10))
        frame.addView(footer, flp)

        setContentView(frame)
    }

    private fun updateGroupInfo() {
        val addr = if (p.address.isNotBlank()) "\nАдреса: ${p.address}" else ""
        tvGroupInfo?.text = "Поточна група: ${Outages.normGroup(p.group)}$addr"
    }

    private fun showInfo() {
        val q = Prefs(this)
        val m = if (q.lastError.isNotEmpty()) "Marstek: помилка — ${q.lastError}" else if (q.lastRaw.isNotEmpty()) "Marstek: OK" else "Marstek: ще не було запитів"
        val o = if (q.outageErr.isNotEmpty()) "Графік: помилка — ${q.outageErr}" else if (q.outageRaw.isNotEmpty()) "Графік: OK" else "Графік: ще не завантажено"
        val g = if (q.channel.isBlank()) "СвітлоБот: канал не вказано" else if (q.tgErr.isNotEmpty()) "СвітлоБот: помилка — ${q.tgErr}" else if (q.tgEvents.isNotEmpty()) "СвітлоБот: OK (${q.tgEvents.split(";").size} подій)" else "СвітлоБот: ще не завантажено"
        tvStatus?.text = "$m\n$o\n$g"
        tvDiag?.text = q.lastRaw.take(1500) + "\n\n" + q.outageRaw.take(800)
    }

    private fun loadRegions() {
        Thread {
            val list = try { Outages.regions() } catch (e: Exception) { null }
            runOnUiThread {
                if (list != null && list.isNotEmpty()) {
                    regionList.clear(); regionLabels.clear()
                    regionList.addAll(list)
                    list.forEach { regionLabels.add(it.label) }
                    var idx = list.indexOfFirst { it.regionId == p.regionId && it.dsoId == p.dsoId }
                    if (idx < 0) {
                        regionList.add(0, RegionDso(p.regionId, p.dsoId, p.regionLabel))
                        regionLabels.add(0, p.regionLabel)
                        idx = 0
                    }
                    regionAdapter?.notifyDataSetChanged()
                    spRegion?.setSelection(idx)
                }
            }
        }.start()
    }

    private fun loadGroups() {
        val r = p.regionId
        val d = p.dsoId
        Thread {
            val keys = try { Outages.groupKeys(r, d) } catch (e: Exception) { null }
            runOnUiThread {
                if (keys != null && keys.isNotEmpty()) {
                    groups.clear()
                    groups.addAll(keys)
                    groupAdapter?.notifyDataSetChanged()
                    val i = groups.indexOf(p.group)
                    spGroup?.setSelection(if (i >= 0) i else 0)
                }
            }
        }.start()
    }

    private fun status(s: String) { runOnUiThread { tvStatus?.text = s } }

    private fun pick(title: String, items: List<String>, onPick: (Int) -> Unit) {
        if (items.isEmpty()) { status("Нічого не знайдено"); return }
        AlertDialog.Builder(this).setTitle(title)
            .setItems(items.take(60).toTypedArray()) { _, i -> onPick(i) }.show()
    }

    private fun searchStreet() {
        val q = etStreet?.text?.toString()?.trim().orEmpty()
        if (q.length < 2) { Toast.makeText(this, "Введіть щонайменше 2 літери", Toast.LENGTH_SHORT).show(); return }
        status("Пошук вулиці…")
        Thread {
            try {
                val list = YasnoAddress.streets(p.regionId, p.dsoId, q)
                runOnUiThread {
                    pick("Оберіть вулицю", list.map { it.name }) { i ->
                        selStreet = list[i]
                        etStreet?.setText(list[i].name)
                        status("Вулицю обрано. Введіть номер будинку.")
                    }
                }
            } catch (e: Exception) {
                status("Помилка пошуку вулиці: ${e.message}")
            }
        }.start()
    }

    private fun searchHouse() {
        val st = selStreet
        if (st == null) { Toast.makeText(this, "Спершу знайдіть і оберіть вулицю", Toast.LENGTH_SHORT).show(); return }
        val q = etHouse?.text?.toString()?.trim().orEmpty()
        if (q.isEmpty()) { Toast.makeText(this, "Введіть номер будинку", Toast.LENGTH_SHORT).show(); return }
        status("Пошук будинку…")
        Thread {
            try {
                val list = YasnoAddress.houses(p.regionId, p.dsoId, st.id, q)
                runOnUiThread {
                    pick("Оберіть будинок", list.map { it.name }) { i ->
                        val h = list[i]
                        etHouse?.setText(h.name)
                        status("Визначаю групу…")
                        Thread {
                            try {
                                val g = YasnoAddress.group(p.regionId, p.dsoId, st.id, h.id)
                                runOnUiThread {
                                    setGroup(g)
                                    p.address = "${st.name}, ${h.name}"
                                    updateGroupInfo()
                                    status("Групу визначено: $g. Натисніть «Зберегти і оновити віджети».")
                                }
                            } catch (e: Exception) {
                                status("Помилка визначення групи: ${e.message}")
                            }
                        }.start()
                    }
                }
            } catch (e: Exception) {
                status("Помилка пошуку будинку: ${e.message}")
            }
        }.start()
    }

    private fun setGroup(g: String) {
        if (!groups.contains(g)) groups.add(g)
        (spGroup?.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()
        spGroup?.setSelection(groups.indexOf(g).coerceAtLeast(0))
        p.group = g
    }
}
