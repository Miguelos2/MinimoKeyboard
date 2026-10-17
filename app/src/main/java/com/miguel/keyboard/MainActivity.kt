package com.miguel.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.SeekBar
import android.widget.Switch
import android.widget.Spinner
import android.widget.BaseAdapter
import android.widget.AbsListView
import android.view.ViewGroup
import android.graphics.Typeface
import android.widget.TextView
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan


/** Horizontal tabs with progressive edge resistance, similar to the rubber-band feel on iOS. */
private class EdgeFrictionHorizontalScrollView(context: Context) : HorizontalScrollView(context) {
    private var lastRawX = 0f
    private var adjustedX = 0f
    private var tracking = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastRawX = event.x
                adjustedX = event.x
                tracking = true
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val rawX = event.x
                val dx = rawX - lastRawX
                val childWidth = if (childCount > 0) getChildAt(0).width else 0
                val maxScroll = (childWidth - width + paddingLeft + paddingRight).coerceAtLeast(0)
                val threshold = (resources.displayMetrics.density * 42f).coerceAtLeast(1f)
                val distanceToEdge = when {
                    dx > 0f -> scrollX.toFloat() // dragging toward the left edge
                    dx < 0f -> (maxScroll - scrollX).toFloat() // dragging toward the right edge
                    else -> threshold
                }.coerceIn(0f, threshold)
                val towardEdge = (dx > 0f && scrollX < threshold) ||
                    (dx < 0f && maxScroll - scrollX < threshold)
                val resistance = if (towardEdge) 0.22f + 0.78f * (distanceToEdge / threshold) else 1f
                adjustedX += dx * resistance
                lastRawX = rawX
                val adjusted = MotionEvent.obtain(event)
                adjusted.setLocation(adjustedX, event.y)
                val handled = super.onTouchEvent(adjusted)
                adjusted.recycle()
                return handled
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val adjusted = MotionEvent.obtain(event)
                if (tracking) adjusted.setLocation(adjustedX, event.y)
                val handled = super.onTouchEvent(adjusted)
                adjusted.recycle()
                tracking = false
                return handled
            }
        }
        if (event.actionMasked != MotionEvent.ACTION_MOVE) {
            return super.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }
}

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("keyboard", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupTabbedSettings()

        val vibrationSwitch = findViewById<Switch>(R.id.switchVibration)
        val vibrationSeek = findViewById<SeekBar>(R.id.seekVibration)
        val vibrationText = findViewById<TextView>(R.id.tvVibration)
        val soundSwitch = findViewById<Switch>(R.id.switchTypewriterSound)
        val rgbSwitch = findViewById<Switch>(R.id.switchRgbMode)
        val numberRowSwitch = findViewById<Switch>(R.id.switchNumberRow)
        val musicTickerSwitch = findViewById<Switch>(R.id.switchMusicTicker)
        val rgbWaveSpinner = findViewById<Spinner>(R.id.rgbWaveSpinner)
        val heightSeek = findViewById<SeekBar>(R.id.seekKeyboardHeight)
        val heightText = findViewById<TextView>(R.id.tvKeyboardHeight)
        val versionText = findViewById<TextView>(R.id.tvVersion)

        vibrationSwitch.isChecked = prefs.getBoolean("vibration_enabled", true)
        soundSwitch.isChecked = prefs.getBoolean("typewriter_sound_enabled", false)
        rgbSwitch.isChecked = prefs.getBoolean("rgb_mode_enabled", false)
        numberRowSwitch.isChecked = prefs.getBoolean("number_row_enabled", false)
        musicTickerSwitch.isChecked = prefs.getBoolean("music_ticker_enabled", false)
        setupRgbWaveSelector(rgbWaveSpinner, rgbSwitch.isChecked)
        vibrationSeek.max = 20
        vibrationSeek.progress = (prefs.getInt("vibration_strength", 45).coerceIn(0, 100) / 5)
        updateVibrationText(vibrationText, vibrationSeek.progress * 5)

        val savedHeight = prefs.getInt("keyboard_height_percent", 100).coerceIn(70, 100)
        heightSeek.progress = savedHeight - 70
        updateHeightText(heightText, savedHeight)

        versionText.text = "${localizedVersionLabel()} ${BuildConfig.VERSION_NAME}"
        setupColorSelector()
        setupLanguageSelector()
        setupPhrases()
        applyTranslations()
        updateSectionTitleColors()
        applyRgbLabel(findViewById(R.id.switchRgbMode))

        findViewById<android.widget.Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        soundSwitch.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("typewriter_sound_enabled", enabled).apply()
            sendSettingsChanged()
        }

        rgbSwitch.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("rgb_mode_enabled", enabled).apply()
            rgbWaveSpinner.isEnabled = enabled
            sendSettingsChanged()
        }

        musicTickerSwitch.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("music_ticker_enabled", enabled).apply()
            sendSettingsChanged()
            if (enabled) {
                runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
        }

        numberRowSwitch.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("number_row_enabled", enabled).apply()
            sendSettingsChanged()
        }

        vibrationSwitch.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("vibration_enabled", enabled).apply()
            sendSettingsChanged()
        }

        vibrationSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val safe = (progress.coerceIn(0, 20) * 5)
                prefs.edit().putInt("vibration_strength", safe).apply()
                updateVibrationText(vibrationText, safe)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) { vibrateConfiguredForOneSecond() }
        })

        heightSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val safe = (progress + 70).coerceIn(70, 100)
                prefs.edit().putInt("keyboard_height_percent", safe).apply()
                updateHeightText(heightText, safe)
                if (fromUser) sendSettingsChanged()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private val tabViews = linkedMapOf<String, TextView>()
    private val tabSections = linkedMapOf<String, View>()
    private var selectedTabKey = "minimo"
    private var settingsHelpView: TextView? = null

    private fun setupTabbedSettings() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        val scroll = content.getChildAt(0) as? ScrollView ?: return
        val original = scroll.getChildAt(0) as? LinearLayout ?: return

        val originalChildren = (0 until original.childCount).map { original.getChildAt(it) }
        val versionFrame = originalChildren.lastOrNull { child -> child.findViewById<TextView?>(R.id.tvVersion) != null }
        val versionView = versionFrame?.findViewById<TextView>(R.id.tvVersion)

        val tabScroll = EdgeFrictionHorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_ALWAYS
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(18))
            setPadding(0, dp(4), 0, dp(10))
        }
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        tabScroll.addView(tabRow, ViewGroup.LayoutParams(-2, dp(52)))

        val host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val sectionKeys = listOf(
            "minimo", "activation", "vibration", "music", "size", "rgb", "language", "phrases"
        )
        val sectionContainers = linkedMapOf<String, LinearLayout>()
        sectionKeys.forEachIndexed { index, key ->
            val section = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(8), dp(4), dp(12))
                visibility = if (index == 0) View.VISIBLE else View.GONE
            }
            sectionContainers[key] = section
            host.addView(section, LinearLayout.LayoutParams(-1, -2))
        }

        fun sectionFor(id: Int): String? = when (id) {
            R.id.tvTitle, R.id.tvSubtitle, R.id.tvSoundDescription, R.id.switchNumberRow -> "minimo"
            R.id.tvActivation, R.id.tvActivationSteps, R.id.btnEnable -> "activation"
            R.id.tvVibrationTitle, R.id.switchVibration, R.id.switchTypewriterSound, R.id.tvVibration, R.id.seekVibration -> "vibration"
            R.id.tvMusicTickerTitle, R.id.switchMusicTicker, R.id.tvMusicTickerDescription -> "music"
            R.id.tvHeightTitle, R.id.tvKeyboardHeight, R.id.seekKeyboardHeight, R.id.tvHeightDescription -> "size"
            R.id.switchRgbMode, R.id.tvRgbWave, R.id.rgbWaveSpinner, R.id.tvColorTitle, R.id.colorSpinner -> "rgb"
            R.id.tvLanguageTitle, R.id.tvLanguageDescription, R.id.languageContainer -> "language"
            R.id.tvPhrasesTitle, R.id.tvPhrasesDescription, R.id.phrasesContainer -> "phrases"
            else -> null
        }

        originalChildren.forEach { child ->
            if (child === versionFrame) return@forEach
            val key = sectionFor(child.id) ?: return@forEach
            (child.parent as? ViewGroup)?.removeView(child)
            sectionContainers[key]?.addView(child)
        }

        val help = TextView(this).apply {
            id = View.generateViewId()
            setTextColor(Color.LTGRAY)
            textSize = 14f
            setPadding(dp(8), dp(8), dp(8), dp(12))
            text = helpTextForLanguage()
        }
        settingsHelpView = help
        sectionContainers["minimo"]?.addView(help, 2)

        // Mapa local de pulsaciones: muestra frecuencia por tecla, nunca el texto escrito.
        val heatmapTitle = TextView(this).apply {
            text = "Mapa de pulsaciones · uso local"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(dp(8), dp(8), dp(8), dp(5))
        }
        sectionContainers["minimo"]?.addView(heatmapTitle)
        val heatmap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = GradientDrawable().apply {
                setColor(Color.rgb(20, 22, 28)); cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(65, 75, 95))
            }
        }
        val heatRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val counts = prefs.all.filterKeys { it.startsWith("heat_") }.mapKeys { it.key.removePrefix("heat_") }
        val maxCount = (counts.values.maxOfOrNull { (it as? Int) ?: 0 } ?: 0).coerceAtLeast(1)
        heatRows.forEach { chars ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER }
            chars.forEach { ch ->
                val n = (counts[ch.toString()] as? Int) ?: 0
                val intensity = (n * 170 / maxCount).coerceIn(0, 170)
                val cell = TextView(this).apply {
                    text = ch.toString(); gravity = android.view.Gravity.CENTER
                    textSize = 12f; setTextColor(Color.WHITE)
                    background = GradientDrawable().apply { setColor(Color.rgb(25, 55 + intensity / 2, 75 + intensity)); cornerRadius = dp(5).toFloat() }
                }
                row.addView(cell, LinearLayout.LayoutParams(0, dp(28), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
            heatmap.addView(row)
        }
        val heatNote = TextView(this).apply {
            text = "Se cuentan teclas, no palabras ni contenido. Cerrá y volvé a abrir Ajustes para actualizar."
            textSize = 10f; setTextColor(Color.GRAY); setPadding(dp(5), dp(4), dp(5), dp(2))
        }
        heatmap.addView(heatNote)
        sectionContainers["minimo"]?.addView(heatmap)

        // Campo de prueba compartido y persistente: no se oculta al cambiar de pestaña,
        // por lo que el foco y el teclado de entrada no colapsan al navegar por Ajustes.
        val keyboardTestRow = createKeyboardTestRow("shared")
        keyboardTestRow.isFocusable = false

        val titles = mapOf(
            "minimo" to "Minimo Keyboard",
            "activation" to "Activación",
            "vibration" to "Vibración y sonido",
            "music" to "MusicTicker",
            "size" to "Tamaño",
            "rgb" to "RGB",
            "language" to "Idiomas",
            "phrases" to "Frases"
        )
        titles.forEach { (key, _) ->
            val tab = TextView(this).apply {
                text = ""
                textSize = 12f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(35, 35, 35))
                    cornerRadius = dp(20).toFloat()
                }
                setOnClickListener { selectSettingsTab(key) }
            }
            tab.isFocusable = false
            tab.isFocusableInTouchMode = false
            tabViews[key] = tab
            tabRow.addView(tab, LinearLayout.LayoutParams(dp(101), dp(38)).apply {
                marginStart = dp(4); marginEnd = dp(4)
            })
        }

        // La barra de pestañas queda fuera del ScrollView: permanece fija en vertical.
        original.removeAllViews()
        original.addView(host)
        original.addView(keyboardTestRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
        if (versionFrame != null) original.addView(versionFrame)
        content.removeView(scroll)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        content.addView(shell, ViewGroup.LayoutParams(-1, -1))
        shell.addView(tabScroll, LinearLayout.LayoutParams(-1, dp(76)))
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(15) })

        sectionContainers.forEach { (key, view) -> tabSections[key] = view }
        updateSettingsTabLabels()
        selectSettingsTab("minimo")
        // Mantiene un campo enfocable estable fuera de las pestañas; al abrir Ajustes
        // queda listo para escribir y la navegación entre pestañas no lo destruye.
        keyboardTestRow.post {
            val edit = findEditText(keyboardTestRow)
            edit?.requestFocus()
            edit?.post {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(edit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun findEditText(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findEditText(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun selectSettingsTab(key: String) {
        selectedTabKey = key
        tabSections.forEach { (name, view) -> view.visibility = if (name == key) View.VISIBLE else View.GONE }
        val selected = selectedSectionColor()
        tabViews.forEach { (name, tab) ->
            val active = name == key
            tab.background = GradientDrawable().apply {
                setColor(if (active) selected else Color.rgb(35, 35, 35))
                cornerRadius = dp(20).toFloat()
                if (!active) setStroke(dp(1), Color.rgb(70, 70, 70))
            }
            tab.setTextColor(if (active && isLightColor(selected)) Color.rgb(20,20,20) else Color.WHITE)
        }
    }

    private fun selectedSectionColor(): Int {
        val key = prefs.getString("keyboard_color", "black") ?: "black"
        return colorOptions().firstOrNull { it.key == key }?.color ?: Color.RED
    }

    private fun updateSettingsTabLabels() {
        val lang = prefs.getString("keyboard_language", "es") ?: "es"
        val labels = when (lang) {
            "pt" -> listOf("Minimo Keyboard", "Ativação", "Vibração e som", "MusicTicker", "Tamanho", "RGB", "Idiomas", "Frases")
            "en" -> listOf("Minimo Keyboard", "Activation", "Vibration & sound", "MusicTicker", "Size", "RGB", "Languages", "Phrases")
            "ja" -> listOf("Minimo Keyboard", "有効化", "振動とサウンド", "MusicTicker", "サイズ", "RGB", "言語", "フレーズ")
            else -> listOf("Minimo Keyboard", "Activación", "Vibración y sonido", "MusicTicker", "Tamaño", "RGB", "Idiomas", "Frases")
        }
        val icons = listOf("⌨", "✓", "♫", "♪", "↕", "◉", "文", "☷")
        tabViews.values.forEachIndexed { index, tab -> tab.text = "${icons[index]}  ${labels[index]}" }
        selectSettingsTab(selectedTabKey)
        settingsHelpView?.text = helpTextForLanguage()
    }

    private fun createKeyboardTestRow(sectionKey: String): View {
        val lang = prefs.getString("keyboard_language", "es") ?: "es"
        val label = TextView(this).apply {
            text = when (lang) {
                "pt" -> "Teste o teclado"
                "en" -> "Test the keyboard"
                "ja" -> "キーボードを試す"
                else -> "Probá el teclado"
            }
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), 0, dp(4), dp(5))
        }
        val input = EditText(this).apply {
            hint = when (lang) {
                "pt" -> "Toque aqui e digite…"
                "en" -> "Tap here and type…"
                "ja" -> "ここをタップして入力…"
                else -> "Tocá acá y escribí…"
            }
            textSize = 16f
            setSingleLine(false)
            minLines = 1
            maxLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(30, 30, 30))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), selectedSectionColor())
            }
            isFocusable = true
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NONE
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4))
            addView(label, LinearLayout.LayoutParams(-1, -2))
            addView(input, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun helpTextForLanguage(): String {
        val language = prefs.getString("keyboard_language", "es") ?: "es"
        return when (language) {
            "pt" -> "Ajuda rápida\n\n• Deslize da esquerda para a direita: insere espaço.\n• Dois deslizes rápidos para a direita: troca o espaço por ponto.\n• Deslize da direita para a esquerda: apaga um caractere; um segundo deslize rápido apaga a última palavra.\n• Deslize para cima ou para baixo: abre Frases; em Frases, deslize para baixo para voltar.\n• Segure uma letra: mostra a maiúscula e, ao soltar, insere a maiúscula.\n• Segure ?: abre !, @ e .\n• Segure Enter: abre as configurações.\n• Segure emoji: abre o teclado completo de emojis.\n• Segure Espaço e deslize: move o cursor."
            "en" -> "Quick help\n\n• Swipe left to right: inserts a space.\n• Two quick right swipes: replaces that space with a period.\n• Swipe right to left: deletes one character; a second quick swipe deletes the last word.\n• Swipe up or down: opens Phrases; in Phrases, swipe down to return.\n• Hold a letter: shows the uppercase preview and inserts uppercase on release.\n• Hold ?: opens !, @ and .\n• Hold Enter: opens settings.\n• Hold emoji: opens the full emoji keyboard.\n• Hold Space and swipe: moves the cursor."
            "ja" -> "クイックヘルプ\n\n• 左から右へスワイプ：スペースを入力。\n• 右へ素早く2回スワイプ：そのスペースをピリオドに変更。\n• 右から左へスワイプ：1文字削除。500ms以内にもう一度で最後の単語を削除。\n• 上下にスワイプ：フレーズを開く。フレーズ画面では下へスワイプで戻る。\n• 文字キーを長押し：大文字を表示し、離すと大文字を入力。\n• ?を長押し：!, @, . を表示。\n• Enterを長押し：設定を開く。\n• 絵文字キーを長押し：絵文字キーボードを開く。\n• Spaceを長押しして左右へスワイプ：カーソル移動。"
            else -> "Ayuda rápida\n\n• Deslizá de izquierda a derecha: inserta un espacio.\n• Dos deslizamientos rápidos hacia la derecha: reemplaza ese espacio por un punto.\n• Deslizá de derecha a izquierda: borra un carácter; un segundo deslizamiento rápido borra la última palabra.\n• Deslizá hacia arriba o hacia abajo: abre Frases; dentro de Frases, deslizá hacia abajo para volver.\n• Mantené una letra pulsada: muestra la mayúscula arriba de la tecla y al soltar inserta la mayúscula.\n• Mantené ? pulsado: abre un selector con !, @ y .\n• Mantené Enter: abre la configuración.\n• Mantené emoji: abre el teclado completo de emojis.\n• Mantené Espacio y deslizá: mueve el cursor."
        }
    }

    private fun setupRgbWaveSelector(spinner: Spinner, rgbEnabled: Boolean) {
        val options = when (prefs.getString("keyboard_language", "es") ?: "es") {
            "pt" -> listOf("Expansiva", "Espiral", "Círculo", "Extremos → centro", "Raios Zig Zag", "Equalizador", "Zoom")
            "en" -> listOf("Expanding", "Spiral", "Circle", "Edges → center", "Zig Zag Rays", "Equalizer", "Zoom")
            "ja" -> listOf("拡大", "スパイラル", "円", "外側 → 中心", "ジグザグレイ", "イコライザー", "ズーム")
            else -> listOf("Expansiva", "Espiral", "Círculo", "Extremos → centro", "Rayos Zig Zag", "Ecualizador", "Zoom")
        }
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = options.size
            override fun getItem(position: Int): String = options[position]
            override fun getItemId(position: Int): Long = position.toLong()
            private fun row(position: Int): TextView = TextView(this@MainActivity).apply {
                text = options[position]
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                val selectedKey = prefs.getString("keyboard_color", "black") ?: "black"
                val selectedColor = colorOptions().firstOrNull { it.key == selectedKey }?.color ?: Color.BLACK
                val selectorFill = if (selectedKey == "transparent") Color.rgb(45, 45, 45) else selectedColor
                setTextColor(if (isLightColor(selectorFill)) Color.rgb(25, 25, 25) else Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(selectorFill)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), adjustOptionBorder(selectorFill))
                }
            }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = row(position)
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = row(position).apply {
                layoutParams = AbsListView.LayoutParams(-1, dp(48))
            }
        }
        spinner.adapter = adapter
        val saved = prefs.getString("rgb_wave_mode", "expanding") ?: "expanding"
        spinner.setSelection(when (saved) {
            "spiral", "sine" -> 1
            "circle", "cosine" -> 2
            "edges", "sine_cosine" -> 3
            "zigzag" -> 4
            "equalizer" -> 5
            "zoom" -> 6
            else -> 0
        })
        spinner.isEnabled = rgbEnabled
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val key = when (position) {
                    1 -> "spiral"
                    2 -> "circle"
                    3 -> "edges"
                    4 -> "zigzag"
                    5 -> "equalizer"
                    6 -> "zoom"
                    else -> "expanding"
                }
                prefs.edit().putString("rgb_wave_mode", key).apply()
                if (rgbSwitchIsEnabled()) sendSettingsChanged()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun rgbSwitchIsEnabled(): Boolean = prefs.getBoolean("rgb_mode_enabled", false)

    private data class ColorOption(val key: String, val name: String, val color: Int)

    private fun colorOptions(): List<ColorOption> = listOf(
        ColorOption("cloud", "Blanco Nube", Color.rgb(245, 245, 240)),
        ColorOption("yellow", "Amarillo Pastel", Color.rgb(255, 242, 166)),
        ColorOption("sky", "Azul Cielo", Color.rgb(135, 206, 235)),
        ColorOption("bubblegum", "Rosa Bubblegum", Color.rgb(255, 183, 213)),
        ColorOption("waifu", "Rosa Waifu", Color.rgb(255, 126, 185)),
        ColorOption("eva", "E.V.A.", Color.rgb(103, 56, 160)),
        ColorOption("nezuko", "Nezuko", Color.rgb(245, 145, 190)),
        ColorOption("pumpkin", "Pumpkin", Color.rgb(255, 117, 24)),
        ColorOption("gray_nardo", "Gray Nardo", Color.rgb(142, 148, 146)),
        ColorOption("olive", "Verde Aceituna", Color.rgb(107, 125, 50)),
        ColorOption("gameboy", "Verde Gameboy", Color.rgb(138, 172, 15)),
        ColorOption("ferrari", "Rojo Ferrari", Color.rgb(255, 40, 0)),
        ColorOption("black", "Black AMOLED", Color.BLACK),
        ColorOption("transparent", "Transparente", Color.rgb(45, 45, 45)),
        ColorOption("blur", "Gradiente", Color.rgb(12, 12, 12))
    )

    private fun localizedColorName(key: String): String {
        val lang = prefs.getString("keyboard_language", "es") ?: "es"
        val names = mapOf(
            "cloud" to mapOf("es" to "Blanco Nube", "pt" to "Branco Nuvem", "en" to "Cloud White", "ja" to "クラウドホワイト"),
            "yellow" to mapOf("es" to "Amarillo Pastel", "pt" to "Amarelo Pastel", "en" to "Pastel Yellow", "ja" to "パステルイエロー"),
            "sky" to mapOf("es" to "Azul Cielo", "pt" to "Azul Céu", "en" to "Sky Blue", "ja" to "スカイブルー"),
            "bubblegum" to mapOf("es" to "Rosa Bubblegum", "pt" to "Rosa Bubblegum", "en" to "Bubblegum Pink", "ja" to "バブルガムピンク"),
            "waifu" to mapOf("es" to "Rosa Waifu", "pt" to "Rosa Waifu", "en" to "Waifu Pink", "ja" to "ワイフピンク"),
            "eva" to mapOf("es" to "E.V.A.", "pt" to "E.V.A.", "en" to "E.V.A.", "ja" to "E.V.A."),
            "nezuko" to mapOf("es" to "Nezuko", "pt" to "Nezuko", "en" to "Nezuko", "ja" to "禰豆子"),
            "pumpkin" to mapOf("es" to "Pumpkin", "pt" to "Pumpkin", "en" to "Pumpkin", "ja" to "パンプキン"),
            "gray_nardo" to mapOf("es" to "Gray Nardo", "pt" to "Gray Nardo", "en" to "Gray Nardo", "ja" to "グレーナルド"),
            "olive" to mapOf("es" to "Verde Aceituna", "pt" to "Verde Azeitona", "en" to "Olive Green", "ja" to "オリーブグリーン"),
            "gameboy" to mapOf("es" to "Verde Gameboy", "pt" to "Verde Gameboy", "en" to "Gameboy Green", "ja" to "ゲームボーイグリーン"),
            "ferrari" to mapOf("es" to "Rojo Ferrari", "pt" to "Vermelho Ferrari", "en" to "Ferrari Red", "ja" to "フェラーリレッド"),
            "black" to mapOf("es" to "Black AMOLED", "pt" to "Black AMOLED", "en" to "Black AMOLED", "ja" to "ブラックAMOLED"),
            "transparent" to mapOf("es" to "Transparente", "pt" to "Transparente", "en" to "Transparent", "ja" to "透明"),
            "blur" to mapOf("es" to "Gradiente", "pt" to "Gradiente", "en" to "Gradient", "ja" to "グラデーション")
        )
        return names[key]?.get(lang) ?: key
    }

    private fun setupColorSelector() {
        val spinner = findViewById<Spinner>(R.id.colorSpinner)
        val options = colorOptions()
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = options.size
            override fun getItem(position: Int): ColorOption = options[position]
            override fun getItemId(position: Int): Long = position.toLong()

            private fun makeRow(position: Int, compact: Boolean): TextView = TextView(this@MainActivity).apply {
                val option = options[position]
                text = localizedColorName(option.key)
                textSize = if (compact) 15f else 16f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(if (isLightColor(option.color)) Color.rgb(25, 25, 25) else Color.WHITE)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(if (compact) 10 else 12), dp(14), dp(if (compact) 10 else 12))
                background = GradientDrawable().apply {
                    setColor(option.color)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), adjustOptionBorder(option.color))
                }
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return makeRow(position, true)
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return makeRow(position, false).apply {
                    val lp = AbsListView.LayoutParams(-1, dp(48))
                    layoutParams = lp
                }
            }
        }
        spinner.adapter = adapter
        val saved = prefs.getString("keyboard_color", "black") ?: "black"
        spinner.setSelection(options.indexOfFirst { it.key == saved }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val key = options[position].key
                if (key != (prefs.getString("keyboard_color", "black") ?: "black")) {
                    prefs.edit().putString("keyboard_color", key).apply()
                    updateSectionTitleColors()
                    setupRgbWaveSelector(findViewById(R.id.rgbWaveSpinner), prefs.getBoolean("rgb_mode_enabled", false))
                    sendSettingsChanged()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun isLightColor(color: Int): Boolean {
        if (color == Color.TRANSPARENT) return false
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color))
        return luminance > 160
    }

    private fun adjustOptionBorder(color: Int): Int {
        return if (isLightColor(color)) Color.argb(90, 0, 0, 0) else Color.argb(120, 255, 255, 255)
    }

    private fun setupLanguageSelector() {
        val container = findViewById<LinearLayout>(R.id.languageContainer)
        container.removeAllViews()
        val saved = prefs.getString("keyboard_language", "es") ?: "es"
        languageOptions().forEach { (key, flag, name) ->
            val button = TextView(this).apply {
                text = "$flag  $name"
                textSize = 15f
                gravity = android.view.Gravity.CENTER
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                isFocusable = true
                isClickable = true
                setPadding(dp(8), dp(12), dp(8), dp(12))
                layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                setOnClickListener {
                    prefs.edit().putString("keyboard_language", key).apply()
                    updateLanguageButtons()
                    applyTranslations()
                    sendSettingsChanged()
                }
                tag = key
            }
            container.addView(button)
        }
        updateLanguageButtons()
    }

    private fun languageOptions(): List<Triple<String, String, String>> {
        // Use compact ISO-style abbreviations so the selector remains visually balanced.
        // The full language name is still used by the localized configuration text.
        return listOf(
            Triple("es", "🇪🇸", "ES"),
            Triple("pt", "🇧🇷", "PT"),
            Triple("en", "🇬🇧", "EN"),
            Triple("ja", "🇯🇵", "JA")
        )
    }

    private fun updateLanguageButtons() {
        val container = findViewById<LinearLayout>(R.id.languageContainer) ?: return
        val selected = prefs.getString("keyboard_language", "es") ?: "es"
        val accent = when (prefs.getString("keyboard_color", "black") ?: "black") {
            "eva" -> Color.rgb(150, 95, 220)
            "waifu" -> Color.rgb(255, 80, 160)
            else -> colorOptions().firstOrNull { it.key == prefs.getString("keyboard_color", "black") }?.color ?: Color.RED
        }
        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i) as? TextView ?: continue
            val active = view.tag == selected
            view.setTextColor(if (active) Color.WHITE else Color.LTGRAY)
            view.background = GradientDrawable().apply {
                setColor(if (active) accent else Color.rgb(25, 25, 25))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(if (active) 2 else 1), if (active) accent else Color.rgb(65, 65, 65))
            }
        }
    }

    private fun setupPhrases() {
        val container = findViewById<LinearLayout>(R.id.phrasesContainer)
        val saved = loadPhrases()
        container.removeAllViews()
        saved.forEachIndexed { index, phrase ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val edit = EditText(this).apply {
                id = View.generateViewId()
                setText(phrase)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.DKGRAY)
                hint = "Frase ${index + 1}"
                textSize = 15f
                setSingleLine(true)
                setPadding(dp(10), 0, dp(10), 0)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(18, 18, 18))
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.rgb(55, 55, 55))
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    setMargins(0, dp(4), dp(8), dp(4))
                }
            }
            val save = ImageButton(this).apply {
                contentDescription = "Guardar frase ${index + 1}"
                setImageResource(R.drawable.ic_save)
                setColorFilter(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(35, 35, 35))
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    setMargins(0, dp(4), 0, dp(4))
                }
                setOnClickListener {
                    prefs.edit().putString("phrase_${index + 1}", edit.text.toString()).apply()
                    sendSettingsChanged()
                    vibrateShort()
                    animate().scaleX(0.88f).scaleY(0.88f).setDuration(70L).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(90L).start()
                    }.start()
                }
            }
            row.addView(edit)
            row.addView(save)
            container.addView(row)
            if (index == 0) {
                edit.requestFocus()
                edit.setSelection(0)
            }
        }
    }

    private fun localizedVersionLabel(): String = when (prefs.getString("keyboard_language", "es") ?: "es") {
        "pt" -> "Versão"
        "en" -> "Version"
        "ja" -> "バージョン"
        else -> "Versión"
    }

    private fun applyTranslations() {
        val lang = prefs.getString("keyboard_language", "es") ?: "es"
        val t = when (lang) {
            "pt" -> mapOf(
                "subtitle" to "Teclado QWERTY minimalista · AMOLED · offline",
                "activation" to "Ativação",
                "steps" to "1. Abra as configurações de teclados do Android.\n2. Ative o Minimo Keyboard.\n3. Escolha-o como teclado padrão.",
                "open" to "ABRIR CONFIGURAÇÕES DO TECLADO",
                "vibration" to "Vibração",
                "vibrateKey" to "Vibrar ao tocar uma tecla",
                "typewriter" to "Som de máquina de escrever",
                "numberRow" to "Linha de números acima do teclado",
                "music" to "MusicTicker",
                "musicSwitch" to "Mostrar a música atual acima do teclado",
                "musicDesc" to "Mostra título/artista em deslocamento e permite reproduzir ou pausar. Ao ativar, é solicitado acesso às sessões multimídia.",
                "rgb" to "RGB Mode", "rgbWave" to "Onda RGB",
                "soundDesc" to "Reproduz um pequeno som mecânico ao tocar uma tecla.",
                "height" to "Altura do teclado", "heightDesc" to "100% = altura normal · 70% = altura mínima",
                "language" to "Idioma do teclado",
                "languageDesc" to "Escolha Español, Português, English ou 日本語. A alteração se aplica ao layout e aos símbolos.",
                "color" to "Cor do teclado", "phrases" to "Frases rápidas",
                "phrasesDesc" to "Edite cada frase e toque no disquete para salvá-la. Elas aparecem no teclado ao deslizar de baixo para cima.",
                "version" to "Versão", "intensity" to "Intensidade", "test" to "solte o controle para testar durante 1 segundo"
            )
            "en" -> mapOf(
                "subtitle" to "Minimalist QWERTY keyboard · AMOLED · offline",
                "activation" to "Activation",
                "steps" to "1. Open Android keyboard settings.\n2. Enable Minimo Keyboard.\n3. Select it as the default keyboard.",
                "open" to "OPEN KEYBOARD SETTINGS",
                "vibration" to "Vibration",
                "vibrateKey" to "Vibrate when pressing a key",
                "typewriter" to "Typewriter sound",
                "numberRow" to "Number row above the keyboard",
                "music" to "MusicTicker",
                "musicSwitch" to "Show the current song above the keyboard",
                "musicDesc" to "Shows the title/artist as a ticker and lets you play or pause. Enabling it requests access to media sessions.",
                "rgb" to "RGB Mode", "rgbWave" to "RGB Wave",
                "soundDesc" to "Plays a small mechanical sound when a key is pressed.",
                "height" to "Keyboard height", "heightDesc" to "100% = normal height · 70% = minimum height",
                "language" to "Keyboard language",
                "languageDesc" to "Choose Español, Português, English or 日本語. The change applies to the layout and symbols.",
                "color" to "Keyboard color", "phrases" to "Quick phrases",
                "phrasesDesc" to "Edit each phrase and tap the disk to save it. They appear when you swipe from bottom to top.",
                "version" to "Version", "intensity" to "Intensity", "test" to "release the control to test for 1 second"
            )
            "ja" -> mapOf(
                "subtitle" to "ミニマルQWERTYキーボード · AMOLED · オフライン",
                "activation" to "有効化",
                "steps" to "1. Androidのキーボード設定を開きます。\n2. Minimo Keyboardを有効にします。\n3. デフォルトのキーボードとして選択します。",
                "open" to "キーボード設定を開く",
                "vibration" to "バイブレーション",
                "vibrateKey" to "キーを押したときに振動する",
                "typewriter" to "タイプライター音",
                "numberRow" to "キーボード上部に数字列を表示",
                "music" to "MusicTicker",
                "musicSwitch" to "現在の曲をキーボード上に表示",
                "musicDesc" to "タイトルとアーティストをスクロール表示し、再生・一時停止できます。有効にするとメディアセッションへのアクセスを求めます。",
                "rgb" to "RGB Mode", "rgbWave" to "RGBウェーブ",
                "soundDesc" to "キーを押したときに小さな機械音を再生します。",
                "height" to "キーボードの高さ", "heightDesc" to "100% = 通常の高さ · 70% = 最小の高さ",
                "language" to "キーボードの言語",
                "languageDesc" to "Español、Português、English、日本語から選択できます。レイアウトと記号に反映されます。",
                "color" to "キーボードの色", "phrases" to "クイックフレーズ",
                "phrasesDesc" to "各フレーズを編集し、フロッピーアイコンをタップして保存します。下から上へスワイプすると表示されます。",
                "version" to "バージョン", "intensity" to "強度", "test" to "指を離すと1秒間テストします"
            )
            else -> mapOf(
                "subtitle" to "Teclado minimalista QWERTY · AMOLED · offline",
                "activation" to "Activación",
                "steps" to "1. Abrí los ajustes de teclados de Android.\n2. Activá Minimo Keyboard.\n3. Elegilo como teclado predeterminado.",
                "open" to "ABRIR AJUSTES DE TECLADO",
                "vibration" to "Vibración y sonido",
                "vibrateKey" to "Vibrar al pulsar una tecla",
                "typewriter" to "Sonido de máquina de escribir",
                "numberRow" to "Línea de números sobre el teclado",
                "music" to "MusicTicker",
                "musicSwitch" to "Mostrar canción actual sobre el teclado",
                "musicDesc" to "Muestra título/artista en desplazamiento y permite reproducir o pausar. Al activarlo se solicita acceso a sesiones multimedia.",
                "rgb" to "RGB Mode", "rgbWave" to "Onda RGB",
                "soundDesc" to "Reproduce un pequeño sonido mecánico al pulsar una tecla.",
                "height" to "Altura del teclado", "heightDesc" to "100% = altura normal · 70% = altura mínima",
                "language" to "Idioma del teclado",
                "languageDesc" to "Elegí Español, Português, English o 日本語. El cambio se aplica al layout y a los símbolos.",
                "color" to "Color del teclado", "phrases" to "Frases rápidas",
                "phrasesDesc" to "Editá cada frase y tocá el disquete para guardarla. Se muestran en el teclado al deslizar de abajo hacia arriba.",
                "version" to "Versión", "intensity" to "Intensidad", "test" to "soltá el control para probar durante 1 segundo"
            )
        }
        fun setText(id: Int, key: String) { findViewById<TextView>(id)?.text = t[key] ?: "" }
        setText(R.id.tvSubtitle, "subtitle"); setText(R.id.tvActivation, "activation"); setText(R.id.tvActivationSteps, "steps")
        findViewById<android.widget.Button>(R.id.btnEnable)?.text = t["open"]
        setText(R.id.tvVibrationTitle, "vibration")
        findViewById<Switch>(R.id.switchVibration)?.text = t["vibrateKey"]
        findViewById<Switch>(R.id.switchTypewriterSound)?.text = t["typewriter"]
        findViewById<Switch>(R.id.switchNumberRow)?.text = t["numberRow"]
        setText(R.id.tvMusicTickerTitle, "music"); findViewById<Switch>(R.id.switchMusicTicker)?.text = t["musicSwitch"]; setText(R.id.tvMusicTickerDescription, "musicDesc")
        findViewById<Switch>(R.id.switchRgbMode)?.let { it.text = t["rgb"]; applyRgbLabel(it) }; setText(R.id.tvRgbWave, "rgbWave"); setText(R.id.tvSoundDescription, "soundDesc")
        setText(R.id.tvHeightTitle, "height"); setText(R.id.tvHeightDescription, "heightDesc"); setText(R.id.tvLanguageTitle, "language"); setText(R.id.tvLanguageDescription, "languageDesc")
        setText(R.id.tvColorTitle, "color"); setText(R.id.tvPhrasesTitle, "phrases"); setText(R.id.tvPhrasesDescription, "phrasesDesc")
        updateVibrationText(findViewById(R.id.tvVibration), prefs.getInt("vibration_strength",45))
        updateHeightText(findViewById(R.id.tvKeyboardHeight), prefs.getInt("keyboard_height_percent",100))
        findViewById<TextView>(R.id.tvVersion)?.let { it.text = "${t["version"]} ${BuildConfig.VERSION_NAME}" }
        setupLanguageSelector(); setupColorSelector(); setupRgbWaveSelector(findViewById(R.id.rgbWaveSpinner), prefs.getBoolean("rgb_mode_enabled", false))
        updateSectionTitleColors()
        updateSettingsTabLabels()
    }

    private fun applyRgbLabel(switch: Switch) {
        val label = SpannableStringBuilder("RGB Mode")
        label.setSpan(ForegroundColorSpan(Color.RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        label.setSpan(ForegroundColorSpan(Color.GREEN), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        label.setSpan(ForegroundColorSpan(Color.BLUE), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        label.setSpan(ForegroundColorSpan(Color.WHITE), 3, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        switch.text = label
    }

    private fun loadPhrases(): List<String> {
        val defaults = listOf(
            "Voy a casa.",
            "Yendo.",
            "Te amo.",
            "Hola! Buenos dias!",
            "Ya lo hago.",
            "Donde queda eso?",
            "Si.",
            "No.",
            "Buenisimo.",
            "Ahora voy."
        )
        return defaults.mapIndexed { index, fallback ->
            prefs.getString("phrase_${index + 1}", fallback) ?: fallback
        }
    }

    private fun vibrateShort() {
        if (!prefs.getBoolean("vibration_enabled", true)) return
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            val strength = prefs.getInt("vibration_strength", 45).coerceIn(0, 100)
            val amplitude = (1 + strength * 254 / 100).coerceIn(1, 255)
            vibrator.vibrate(VibrationEffect.createOneShot(18L, amplitude))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(18L)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun updateSectionTitleColors() {
        val color = when (prefs.getString("keyboard_color", "black") ?: "black") {
            "bubblegum" -> Color.rgb(255, 105, 160)
            "waifu" -> Color.rgb(255, 80, 160)
            "olive" -> Color.rgb(170, 195, 80)
            "yellow" -> Color.rgb(210, 165, 0)
            "cloud" -> Color.rgb(120, 120, 120)
            "ferrari" -> Color.rgb(255, 105, 75)
            "transparent" -> Color.rgb(255, 70, 70)
            "sky" -> Color.rgb(70, 180, 230)
            "gameboy" -> Color.rgb(138, 172, 15)
            "eva" -> Color.rgb(150, 95, 220)
            "nezuko" -> Color.rgb(245, 145, 190)
            "pumpkin" -> Color.rgb(255, 117, 24)
            "gray_nardo" -> Color.rgb(142, 148, 146)
            else -> Color.RED
        }
        listOf(
            R.id.tvTitle,
            R.id.tvActivation,
            R.id.tvVibrationTitle,
            R.id.tvHeightTitle,
            R.id.tvColorTitle,
            R.id.tvLanguageTitle,
            R.id.tvPhrasesTitle,
            R.id.tvMusicTickerTitle
        ).forEach { findViewById<TextView>(it)?.setTextColor(color) }
        updateLanguageButtons()
        updateVersionBubble()
    }

    private fun updateVersionBubble() {
        val version = findViewById<TextView>(R.id.tvVersion) ?: return
        val key = prefs.getString("keyboard_color", "black") ?: "black"
        val selected = colorOptions().firstOrNull { it.key == key }?.color ?: Color.BLACK
        val bubbleColor = if (key == "transparent") Color.rgb(45, 45, 45) else Color.argb(210, Color.red(selected), Color.green(selected), Color.blue(selected))
        val textColor = if (isLightColor(selected)) Color.rgb(20, 20, 20) else Color.WHITE
        version.setTextColor(textColor)
        version.setPadding(dp(10), dp(5), dp(10), dp(5))
        version.background = GradientDrawable().apply {
            setColor(bubbleColor)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), if (isLightColor(selected)) Color.argb(150,0,0,0) else Color.argb(170,255,255,255))
        }
    }

    private fun sendSettingsChanged() {
        sendBroadcast(Intent(MiguelKeyboardService.ACTION_SETTINGS_CHANGED).setPackage(packageName))
    }

    private fun updateVibrationText(view: TextView, value: Int) {
        val lang = prefs.getString("keyboard_language", "es") ?: "es"
        view.text = when (lang) {
            "pt" -> "Intensidade: $value% · solte o controle para testar por 1 segundo"
            "en" -> "Intensity: $value% · release the control to test for 1 second"
            "ja" -> "強度: $value% · 指を離すと1秒間テストします"
            else -> "Intensidad: $value% · soltá el control para probar durante 1 segundo"
        }
    }

    private fun updateHeightText(view: TextView, value: Int) {
        val lang = prefs.getString("keyboard_language", "es") ?: "es"
        view.text = when (lang) {
            "pt" -> "Altura: $value%"
            "en" -> "Height: $value%"
            "ja" -> "高さ: $value%"
            else -> "Altura: $value%"
        }
    }

    private fun vibrateConfiguredForOneSecond() {
        val strength = prefs.getInt("vibration_strength", 45).coerceIn(0, 100)
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            val amplitude = (1 + strength * 254 / 100).coerceIn(1, 255)
            vibrator.vibrate(VibrationEffect.createOneShot(1000L, amplitude))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(1000L)
        }
    }
}
