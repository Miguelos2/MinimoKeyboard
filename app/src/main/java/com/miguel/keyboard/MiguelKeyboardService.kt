package com.miguel.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentName
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.BitmapFactory
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.SystemClock
import android.os.Vibrator
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.media.MediaPlayer
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import android.widget.PopupWindow
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

class MiguelKeyboardService : InputMethodService() {
    companion object {
        const val ACTION_SETTINGS_CHANGED = "com.miguel.keyboard.ACTION_SETTINGS_CHANGED"
    }

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SETTINGS_CHANGED) refreshKeyboard()
        }
    }
    private lateinit var root: KeyboardRoot
    private var shifted = true
    private var firstLetterPending = true
    private var keyboardMode = 0 // 0=ABC, 1=123/símbolos, 2=emojis
    private var phraseMode = false
    private var lastRightSwipeAt = 0L
    private var lastDeleteSwipeAt = 0L
    private var typewriterPlayer: MediaPlayer? = null
    private var lastEmojiButton: Button? = null
    private val musicHandler = Handler(Looper.getMainLooper())
    private var musicController: MediaController? = null
    private var musicTickerTitle: TextView? = null
    private var musicTickerButton: Button? = null
    private var musicPreviousButton: Button? = null
    private var musicNextButton: Button? = null
    private var musicSpaceButton: Button? = null
    private var musicSpaceHeightScale: Float = 1f
    private val musicRefreshRunnable = object : Runnable {
        override fun run() {
            if (prefs.getBoolean("music_ticker_enabled", false)) {
                updateMusicTicker()
                musicHandler.postDelayed(this, 1000L)
            }
        }
    }

    private fun selectedPalette(): Palette = when (prefs.getString("keyboard_color", "black") ?: "black") {
        "bubblegum" -> Palette(Color.rgb(255, 183, 213), Color.rgb(244, 143, 177), Color.rgb(45, 20, 30), Color.rgb(255, 105, 160))
        "waifu" -> Palette(Color.rgb(255, 126, 185), Color.rgb(255, 168, 205), Color.rgb(70, 20, 45), Color.rgb(255, 80, 160))
        "eva" -> Palette(Color.rgb(103, 56, 160), Color.rgb(83, 42, 135), Color.WHITE, Color.rgb(170, 100, 235), Color.rgb(110, 255, 80))
        "nezuko" -> Palette(Color.rgb(55, 18, 28), Color.rgb(245, 145, 190), Color.rgb(55, 20, 30), Color.rgb(230, 70, 95))
        "pumpkin" -> Palette(Color.rgb(255, 117, 24), Color.rgb(232, 82, 10), Color.WHITE, Color.rgb(255, 170, 60))
        "gray_nardo" -> Palette(Color.rgb(142, 148, 146), Color.rgb(115, 121, 119), Color.WHITE, Color.rgb(185, 191, 189))
        "olive" -> Palette(Color.rgb(107, 125, 50), Color.rgb(85, 107, 47), Color.WHITE, Color.rgb(170, 195, 80))
        "yellow" -> Palette(Color.rgb(255, 242, 166), Color.rgb(244, 211, 94), Color.rgb(55, 45, 10), Color.rgb(210, 165, 0))
        "cloud" -> Palette(Color.rgb(245, 245, 240), Color.WHITE, Color.rgb(25, 25, 25), Color.rgb(120, 120, 120))
        "ferrari" -> Palette(Color.rgb(255, 40, 0), Color.rgb(217, 30, 0), Color.WHITE, Color.rgb(255, 105, 75), Color.rgb(255, 215, 0))
        "transparent" -> Palette(Color.TRANSPARENT, Color.argb(175, 20, 20, 20), Color.WHITE, Color.rgb(255, 70, 70))
        // Gradiente vertical: OLED negro abajo, transición hacia transparencia arriba.
        "blur" -> Palette(Color.BLACK, Color.argb(150, 25, 25, 25), Color.WHITE, Color.rgb(105, 190, 255))
        "sky" -> Palette(Color.rgb(135, 206, 235), Color.rgb(93, 173, 226), Color.rgb(15, 35, 45), Color.rgb(0, 191, 255))
        "gameboy" -> Palette(Color.rgb(138, 172, 15), Color.rgb(115, 145, 12), Color.rgb(20, 28, 5), Color.rgb(190, 220, 45))
        else -> Palette(Color.BLACK, Color.rgb(17, 17, 17), Color.WHITE, Color.RED)
    }

    private data class Palette(val background: Int, val key: Int, val text: Int, val active: Int, val enter: Int = active)

    private val prefs by lazy { getSharedPreferences("keyboard", Context.MODE_PRIVATE) }
    private val numberRow = listOf("1","2","3","4","5","6","7","8","9","0")

    private fun keyboardLanguage(): String = prefs.getString("keyboard_language", "es") ?: "es"

    private fun letterRows(): List<List<String>> = when (keyboardLanguage()) {
        "ja" -> listOf(
            listOf("Q","W","E","R","T","Y","U","I","O","P"),
            listOf("A","S","D","F","G","H","J","K","L"),
            listOf("Z","X","C","V","B","N","M")
        )
        "pt" -> listOf(
            listOf("Q","W","E","R","T","Y","U","I","O","P"),
            listOf("A","S","D","F","G","H","J","K","L","Ç"),
            listOf("Z","X","C","V","B","N","M")
        )
        "en" -> listOf(
            listOf("Q","W","E","R","T","Y","U","I","O","P"),
            listOf("A","S","D","F","G","H","J","K","L"),
            listOf("Z","X","C","V","B","N","M")
        )
        else -> listOf(
            listOf("Q","W","E","R","T","Y","U","I","O","P"),
            listOf("A","S","D","F","G","H","J","K","L","Ñ"),
            listOf("Z","X","C","V","B","N","M")
        )
    }

    private fun symbolRowsForLanguage(): List<List<String>> = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","%","&","*","(",")","-","_"),
        listOf(";",":","\"","'",".","+","/","=","[","]")
    )

    private val emojiRows = listOf(
        listOf("😀","😂","🤣","😊","😍","🥰","😎","🤔","😭","😡"),
        listOf("👍","👎","👏","🙏","❤️","🔥","✨","🎉","💯","💪"),
        listOf("🚀","⭐","☀️","🌙","☕","🍕","🎵","⚽","🐶","🐱")
    )

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(ACTION_SETTINGS_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") registerReceiver(settingsReceiver, filter)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(settingsReceiver) }
        musicHandler.removeCallbacksAndMessages(null)
        typewriterPlayer?.release()
        typewriterPlayer = null
        super.onDestroy()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // Cada nuevo campo comienza en el teclado alfabético y con mayúscula inicial.
        keyboardMode = 0
        shifted = true
        firstLetterPending = true
        phraseMode = false
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // También restablece el modo si el teclado se vuelve a mostrar en el mismo campo.
        keyboardMode = 0
        shifted = true
        firstLetterPending = true
        phraseMode = false
        if (::root.isInitialized) refreshKeyboard()
    }

    override fun onCreateInputView(): View {
        buildKeyboard()
        return root
    }

    private fun buildKeyboard() {
        val heightScale = keyboardHeightScale()
        root = KeyboardRoot(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpScaled(5, heightScale), dpScaled(5, heightScale), dpScaled(5, heightScale), dpScaled(5, heightScale))
            background = GradientDrawable().apply {
                val colorKey = prefs.getString("keyboard_color", "black") ?: "black"
                if (colorKey == "blur") {
                    orientation = GradientDrawable.Orientation.BOTTOM_TOP
                    colors = intArrayOf(Color.BLACK, Color.argb(150, 0, 0, 0), Color.TRANSPARENT)
                } else {
                    setColor(selectedPalette().background)
                }
                val radius = dpScaled(20, heightScale).toFloat()
                setCornerRadii(floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f))
            }
            onSwipeDelete = {
                lastDeleteSwipeAt = 0L
                deleteBeforeCursor()
            }
        }

        if (keyboardMode == 0 && prefs.getBoolean("number_row_enabled", false)) {
            addRow(numberRow, -1, heightScale)
        }
        val activeRows = when (keyboardMode) {
            1 -> symbolRowsForLanguage()
            2 -> emojiRows
            else -> letterRows()
        }
        activeRows.forEachIndexed { index, row ->
            addRow(row, index, heightScale)
        }
        addBottomRow(heightScale)
        addMusicTicker(heightScale)
        root.configureDpadNavigation()
        root.post { root.requestInitialKeyboardFocus() }
    }

    private fun addRow(keys: List<String>, rowIndex: Int, heightScale: Float) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        keys.forEachIndexed { keyIndex, key ->
            val isLetters = keyboardMode == 0
            val mathSymbol = if (keyboardMode == 1 && key.length == 1 && key[0].isDigit()) {
                mapOf("1" to "+", "2" to "−", "3" to "/", "4" to "*", "5" to "%", "6" to "+", "7" to "−", "8" to "/", "9" to "*", "0" to "%")[key]
            } else null
            val label = if (isLetters && shifted) key else if (isLetters) key.lowercase() else key
            val commit = { uppercase: Boolean ->
                if (isLetters && key.length == 1 && key[0].isLetter()) {
                    val heatKey = "heat_${key.lowercase()}"
                    prefs.edit().putInt(heatKey, (prefs.getInt(heatKey, 0) + 1).coerceAtMost(100000)).apply()
                }
                val shouldCapitalize = isLetters && (uppercase || shifted || firstLetterPending)
                commitText(if (shouldCapitalize) key.uppercase() else if (isLetters) key.lowercase() else key)
                if (isLetters) {
                    firstLetterPending = false
                    if (shifted) {
                        shifted = false
                        refreshKeyboard()
                    }
                }
            }
            val button = if (isLetters) {
                makeLetterKey(label, key, shifted, commit)
            } else if (keyboardMode == 2) {
                makeKey(label) {
                    commit(false)
                    saveLastEmoji(key)
                }
            } else if (key == "?") {
                makeQuestionKey()
            } else if (keyboardMode == 1 && key == "@") {
                makeKey(label) {
                    commitText("@")
                    keyboardMode = 0
                    shifted = false
                    refreshKeyboard()
                }
            } else {
                val shownLabel = if (mathSymbol != null) {
                    android.text.SpannableString("$label  $mathSymbol").apply {
                        setSpan(android.text.style.RelativeSizeSpan(0.55f), label.length + 2, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(android.text.style.SuperscriptSpan(), label.length + 2, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else label
                makeKey(shownLabel) { commit(false) }.also { button ->
                    if (mathSymbol != null) {
                        button.setOnLongClickListener {
                            commitText(mathSymbol)
                            playTypewriterSound()
                            vibrateKey()
                            reactivePulse(button)
                            true
                        }
                        button.contentDescription = "$label, mantener pulsado para $mathSymbol"
                    }
                }
            }
            if (isLetters && (prefs.getString("keyboard_color", "black") ?: "black") == "nezuko") {
                applyNezukoKeyStyle(button, rowIndex, keyIndex)
            }
            row.addView(button, LinearLayout.LayoutParams(0, dpScaled(48, heightScale), 1f).apply {
                setMargins(dpScaled(2, heightScale), dpScaled(2, heightScale), dpScaled(2, heightScale), dpScaled(2, heightScale))
            })
        }
        // A dedicated uppercase key sits immediately to the right of M.
        if (keyboardMode == 0 && rowIndex == 2) {
            val shiftKey = makeKey("⇧") {
                shifted = !shifted
                refreshKeyboard()
            }
            if ((prefs.getString("keyboard_color", "black") ?: "black") == "nezuko") {
                applyNezukoKeyStyle(shiftKey, rowIndex, row.childCount)
            }
            row.addView(shiftKey, LinearLayout.LayoutParams(0, dpScaled(48, heightScale), 1f).apply {
                setMargins(dpScaled(2, heightScale), dpScaled(2, heightScale), dpScaled(2, heightScale), dpScaled(2, heightScale))
            })
        }
        root.addView(row, LinearLayout.LayoutParams(-1, dpScaled(52, heightScale)))
    }

    private fun addBottomRow(heightScale: Float) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val emojiButton = makeKey(lastEmoji()) { }
        lastEmojiButton = emojiButton
        var emojiLongPressed = false
        val emojiLongPressRunnable = Runnable {
            emojiLongPressed = true
            keyboardMode = 2
            shifted = false
            vibrateCaseChange()
            refreshKeyboard()
        }
        emojiButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    emojiLongPressed = false
                    emojiButton.removeCallbacks(emojiLongPressRunnable)
                    emojiButton.postDelayed(emojiLongPressRunnable, 500L)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    emojiButton.removeCallbacks(emojiLongPressRunnable)
                    if (!emojiLongPressed) {
                        showRecentEmojiPopup(emojiButton)
                    }
                    emojiLongPressed = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    emojiButton.removeCallbacks(emojiLongPressRunnable)
                    emojiLongPressed = false
                    true
                }
                else -> true
            }
        }
        emojiButton.setOnLongClickListener {
            keyboardMode = 2
            shifted = false
            vibrateCaseChange()
            refreshKeyboard()
            true
        }
        installDpadLongPress(emojiButton, 500L) {
            keyboardMode = 2
            shifted = false
            vibrateCaseChange()
            refreshKeyboard()
        }
        row.addView(emojiButton, weightParams(1.05f, heightScale))

        row.addView(makeKey(when (keyboardMode) { 0 -> "123"; 1 -> "😀"; else -> "ABC" }) {
            keyboardMode = when (keyboardMode) {
                0 -> 1
                1 -> 2
                else -> 0
            }
            shifted = false
            refreshKeyboard()
        }, weightParams(1.05f, heightScale))

        val spaceButton = makeSpaceKey(heightScale)
        when (prefs.getString("keyboard_color", "black") ?: "black") {
            "waifu" -> applyWaifuSpaceBackground(spaceButton, heightScale)
            "eva" -> applyEvaSpaceBackground(spaceButton, heightScale)
            "nezuko" -> applyNezukoSpaceBackground(spaceButton, heightScale)
            "pumpkin" -> applyPumpkinSpaceBackground(spaceButton, heightScale)
        }
        musicSpaceButton = spaceButton
        musicSpaceHeightScale = heightScale
        row.addView(spaceButton, weightParams(2.45f, heightScale))
        row.addView(makeQuestionKey(), weightParams(1.05f, heightScale))
        row.addView(makeEnterKey(heightScale), weightParams(1.7f, heightScale))
        root.addView(row, LinearLayout.LayoutParams(-1, dpScaled(56, heightScale)))
    }

    private fun addMusicTicker(heightScale: Float) {
        if (!prefs.getBoolean("music_ticker_enabled", false)) {
            musicTickerTitle = null
            musicTickerButton = null
            musicPreviousButton = null
            musicNextButton = null
            musicHandler.removeCallbacks(musicRefreshRunnable)
            return
        }

        val palette = selectedPalette()
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpScaled(5, heightScale), 0, dpScaled(5, heightScale), 0)
            background = GradientDrawable().apply {
                setColor(palette.background)
                cornerRadius = dpScaled(8, heightScale).toFloat()
            }
        }

        fun musicControl(symbol: String, action: () -> Unit): Button = Button(this).apply {
            text = symbol
            textSize = 17f
            setTextColor(palette.text)
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(palette.key)
                cornerRadius = dpScaled(7, heightScale).toFloat()
            }
            setOnClickListener { action() }
        }

        val control = musicControl("▶") { toggleMusicPlayback() }
        control.setOnLongClickListener {
            musicController?.transportControls?.stop()
            updateMusicTicker()
            true
        }
        musicTickerButton = control

        val title = TextView(this).apply {
            text = "Sin reproducción"
            textSize = 14f
            setTextColor(palette.text)
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            setPadding(dpScaled(10, heightScale), 0, dpScaled(6, heightScale), 0)
        }
        musicTickerTitle = title

        val previous = musicControl("⏮") {
            musicController?.transportControls?.skipToPrevious()
            vibrateKey()
            musicHandler.postDelayed({ updateMusicTicker() }, 250L)
        }
        val next = musicControl("⏭") {
            musicController?.transportControls?.skipToNext()
            vibrateKey()
            musicHandler.postDelayed({ updateMusicTicker() }, 250L)
        }
        musicPreviousButton = previous
        musicNextButton = next

        line.addView(control, LinearLayout.LayoutParams(dpScaled(46, heightScale), dpScaled(38, heightScale)))
        line.addView(title, LinearLayout.LayoutParams(0, dpScaled(38, heightScale), 1f))
        line.addView(previous, LinearLayout.LayoutParams(dpScaled(42, heightScale), dpScaled(38, heightScale)))
        line.addView(next, LinearLayout.LayoutParams(dpScaled(42, heightScale), dpScaled(38, heightScale)))
        root.addView(line, LinearLayout.LayoutParams(-1, dpScaled(42, heightScale)).apply {
            topMargin = dpScaled(3, heightScale)
        })

        musicHandler.removeCallbacks(musicRefreshRunnable)
        musicHandler.post(musicRefreshRunnable)
    }

    private fun getMusicController(): MediaController? {
        return try {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, MusicSessionNotificationListenerService::class.java)
            val sessions = manager.getActiveSessions(component)
            sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull {
                    val state = it.playbackState?.state
                    state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_BUFFERING
                }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun updateMusicTicker() {
        if (!prefs.getBoolean("music_ticker_enabled", false)) return
        val controller = getMusicController()
        musicController = controller
        val titleView = musicTickerTitle ?: return
        val button = musicTickerButton ?: return
        val palette = selectedPalette()
        titleView.setTextColor(palette.text)
        (titleView.parent as? View)?.background = GradientDrawable().apply {
            setColor(palette.background)
            cornerRadius = dp(8).toFloat()
        }
        button.setTextColor(palette.text)

        if (controller == null) {
            titleView.text = "Sin reproducción · MusicTicker"
            button.text = "▶"
            musicPreviousButton?.isEnabled = false
            musicNextButton?.isEnabled = false
            updateSpaceArtwork(null, false)
            return
        }
        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
        titleView.text = when {
            title.isNotEmpty() && artist.isNotEmpty() -> "$title — $artist"
            title.isNotEmpty() -> title
            artist.isNotEmpty() -> artist
            else -> "Reproducción multimedia"
        }
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        button.text = if (playing) "Ⅱ" else "▶"
        musicPreviousButton?.isEnabled = true
        musicNextButton?.isEnabled = true
        titleView.isSelected = true
        updateSpaceArtwork(controller, playing)
    }

    private fun toggleMusicPlayback() {
        val controller = musicController ?: getMusicController()
        musicController = controller
        if (controller == null) {
            updateMusicTicker()
            return
        }
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) controller.transportControls.pause() else controller.transportControls.play()
        vibrateKey()
        musicHandler.postDelayed({ updateMusicTicker() }, 150L)
    }

    private fun updateSpaceArtwork(controller: MediaController?, playing: Boolean) {
        val button = musicSpaceButton ?: return
        if (!prefs.getBoolean("music_ticker_enabled", false)) {
            restoreSpaceKeyAppearance(button, musicSpaceHeightScale)
            return
        }
        val artwork = controller?.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: controller?.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val palette = selectedPalette()
        val base = GradientDrawable().apply {
            setColor(palette.key)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), adjustColor(palette.key, 0.82f))
        }
        val image = SpaceArtworkDrawable(
            artwork,
            dpScaled(12, musicSpaceHeightScale).toFloat(),
            dpScaled(8, musicSpaceHeightScale).toFloat(),
            playing,
            palette.active
        )
        button.text = ""
        button.background = LayerDrawable(arrayOf(base, image))
    }

    /** Portada cuadrada a la izquierda y ecualizador animado a su derecha. */
    private inner class SpaceArtworkDrawable(
        private val bitmap: android.graphics.Bitmap?,
        private val cornerRadius: Float,
        private val leftInset: Float,
        private val playing: Boolean,
        private val accentColor: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val path = Path()
        private val rect = RectF()
        private val eqPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        private val animator = object : Runnable {
            override fun run() {
                if (playing && callback != null) {
                    invalidateSelf()
                    scheduleSelf(this, android.os.SystemClock.uptimeMillis() + 115L)
                }
            }
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) return
            bitmap?.let {
                val side = b.height() * 0.80f
                val left = b.left + leftInset
                val top = b.top + (b.height() - side) / 2f
                rect.set(left, top, left + side, top + side)
                path.reset()
                path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
                val save = canvas.save()
                canvas.clipPath(path)
                canvas.drawBitmap(it, null, rect, paint)
                canvas.restoreToCount(save)
            }

            // El ecualizador ocupa una zona discreta a la derecha de la portada.
            val coverSide = b.height() * 0.80f
            val eqLeft = if (bitmap != null) b.left + leftInset + coverSide + dpScaled(8, musicSpaceHeightScale) else b.left + b.width() * 0.72f
            val eqRight = b.right - dpScaled(12, musicSpaceHeightScale)
            val available = (eqRight - eqLeft).coerceAtLeast(0f)
            if (available < dp(5)) return
            val centerY = b.exactCenterY()
            if (!playing) {
                canvas.drawLine(eqLeft, centerY, eqRight, centerY, eqPaint)
                unscheduleSelf(animator)
                return
            }
            val bars = 5
            val gap = dpScaled(3, musicSpaceHeightScale).toFloat()
            val barWidth = ((available - gap * (bars - 1)) / bars).coerceAtLeast(dp(1).toFloat())
            val phase = (android.os.SystemClock.uptimeMillis() / 115L).toInt()
            for (i in 0 until bars) {
                val wave = abs(kotlin.math.sin((phase * 0.48 + i * 1.27)))
                val secondary = abs(kotlin.math.sin((phase * 0.23 + i * 0.71)))
                val halfHeight = dpScaled(3, musicSpaceHeightScale) + (b.height() * 0.28f * (0.25f + 0.75f * wave * secondary))
                val x = eqLeft + i * (barWidth + gap) + barWidth / 2f
                canvas.drawLine(x, centerY - halfHeight, x, centerY + halfHeight, eqPaint)
            }
            if (callback != null) scheduleSelf(animator, android.os.SystemClock.uptimeMillis() + 115L)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha; eqPaint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; eqPaint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun restoreSpaceKeyAppearance(button: Button, heightScale: Float) {
        button.text = "ESPACIO"
        when (prefs.getString("keyboard_color", "black") ?: "black") {
            "waifu" -> applyWaifuSpaceBackground(button, heightScale)
            "eva" -> applyEvaSpaceBackground(button, heightScale)
            "nezuko" -> applyNezukoSpaceBackground(button, heightScale)
            "pumpkin" -> applyPumpkinSpaceBackground(button, heightScale)
            else -> {
                val palette = selectedPalette()
                button.background = GradientDrawable().apply {
                    setColor(palette.key)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), adjustColor(palette.key, 0.82f))
                }
            }
        }
    }

    private fun makeSpaceKey(heightScale: Float): Button {
        val button = makeKey("ESPACIO") { commitText(" ") }
        button.setTag(R.id.tag_space_key, true)

        var longPressed = false
        var downX = 0f
        var lastX = 0f
        val longPressRunnable = Runnable {
            longPressed = true
            vibrateKey()
        }

        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longPressed = false
                    downX = event.x
                    lastX = event.x
                    v.removeCallbacks(longPressRunnable)
                    v.postDelayed(longPressRunnable, 450L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longPressed) {
                        val dx = event.x - lastX
                        val step = dp(18).toFloat().coerceAtLeast(1f)
                        if (abs(dx) >= step) {
                            val direction = if (dx > 0f) 1 else -1
                            moveCursor(direction)
                            val steps = (abs(dx) / step).toInt().coerceAtLeast(1)
                            repeat(steps - 1) { moveCursor(direction) }
                            lastX = event.x
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPressRunnable)
                    if (!longPressed) {
                        v.performClick()
                    }
                    longPressed = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPressRunnable)
                    longPressed = false
                    true
                }
                else -> true
            }
        }
        return button
    }

    private fun moveCursor(direction: Int) {
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(1000, 0)?.length ?: 0
        val after = connection.getTextAfterCursor(1000, 0)?.length ?: 0
        val current = before
        val target = (current + direction).coerceIn(0, current + after)
        connection.setSelection(target, target)
        vibrateKey()
    }

    private fun applyWaifuSpaceBackground(button: Button, heightScale: Float) {
        val base = GradientDrawable().apply {
            setColor(selectedPalette().key)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), adjustColor(selectedPalette().key, 0.82f))
        }
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.waifu_space) ?: return
        val image = BitmapDrawable(resources, bitmap).apply {
            gravity = Gravity.CENTER
            setBounds(0, 0, dpScaled(150, heightScale), dpScaled(44, heightScale))
        }
        button.text = ""
        button.background = LayerDrawable(arrayOf(base, image))
        button.setTag(R.id.tag_base_color, selectedPalette().key)
    }

    private fun applyEvaSpaceBackground(button: Button, heightScale: Float) {
        val palette = selectedPalette()
        val base = GradientDrawable().apply {
            setColor(palette.key)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), adjustColor(palette.key, 0.82f))
        }
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.eva_space) ?: return
        val image = BitmapDrawable(resources, bitmap).apply {
            gravity = Gravity.CENTER
            setBounds(0, 0, dpScaled(150, heightScale), dpScaled(44, heightScale))
        }
        button.text = ""
        button.background = LayerDrawable(arrayOf(base, image))
        button.setTag(R.id.tag_base_color, palette.key)
    }

    private fun applyNezukoSpaceBackground(button: Button, heightScale: Float) {
        val palette = selectedPalette()
        val base = GradientDrawable().apply {
            setColor(palette.key)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), adjustColor(palette.key, 0.82f))
        }
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.nezuko_bamboo) ?: return
        val image = BitmapDrawable(resources, bitmap).apply {
            gravity = Gravity.CENTER
            setBounds(0, 0, dpScaled(150, heightScale), dpScaled(44, heightScale))
        }
        button.text = ""
        button.background = LayerDrawable(arrayOf(base, image))
        button.setTag(R.id.tag_base_color, palette.key)
    }

    private fun applyPumpkinSpaceBackground(button: Button, heightScale: Float) {
        val palette = selectedPalette()
        val base = GradientDrawable().apply {
            setColor(palette.key)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), adjustColor(palette.key, 0.82f))
        }
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.pumpkin_space) ?: return
        val image = BitmapDrawable(resources, bitmap).apply {
            gravity = Gravity.CENTER
            setBounds(0, 0, dpScaled(150, heightScale), dpScaled(44, heightScale))
        }
        button.text = ""
        button.background = LayerDrawable(arrayOf(base, image))
        button.setTag(R.id.tag_base_color, palette.key)
    }

    private fun applyNezukoKeyStyle(button: Button, rowIndex: Int, keyIndex: Int) {
        val color = when (rowIndex) {
            0 -> Color.rgb(121, 72, 42)       // brown
            1 -> Color.rgb(245, 145, 190)     // pink
            2 -> if (keyIndex % 2 == 0) Color.WHITE else Color.rgb(220, 35, 50)
            else -> selectedPalette().key
        }
        val textColor = when {
            rowIndex == 0 -> Color.WHITE
            rowIndex == 1 -> Color.rgb(70, 20, 45)
            rowIndex == 2 && keyIndex % 2 == 0 -> Color.rgb(45, 25, 25)
            else -> Color.WHITE
        }
        button.setTextColor(textColor)
        button.background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), adjustColor(color, 0.78f))
        }
        button.setTag(R.id.tag_base_color, color)
    }

    private fun recentEmojis(): List<String> {
        val raw = prefs.getString("recent_emojis", "") ?: ""
        return raw.split("|").filter { it.isNotBlank() }.distinct().take(3)
    }

    private fun saveLastEmoji(emoji: String) {
        val updated = (listOf(emoji) + recentEmojis()).distinct().take(3)
        prefs.edit()
            .putString("last_emoji", emoji)
            .putString("recent_emojis", updated.joinToString("|"))
            .apply()
        lastEmojiButton?.text = emoji
        lastEmojiButton?.invalidate()
    }

    private fun showRecentEmojiPopup(anchor: View) {
        val emojis = recentEmojis().ifEmpty { listOf(lastEmoji()) }
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((5 * density).toInt(), (5 * density).toInt(), (5 * density).toInt(), (5 * density).toInt())
            background = GradientDrawable().apply {
                setColor(selectedPalette().key)
                cornerRadius = 14f * density
                setStroke((1 * density).toInt(), selectedPalette().active)
            }
        }
        // El popup no debe robar el foco al IME: evita parpadeos y cambios de
        // foco del teclado mientras se selecciona un emoji reciente.
        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(58),
            false
        ).apply {
            isOutsideTouchable = true
            isTouchModal = false
            elevation = 12f * density
            // El popup no necesita gestionar el IME.
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        emojis.forEach { emoji ->
            val b = TextView(this).apply {
                text = emoji
                textSize = 25f
                gravity = Gravity.CENTER
                isClickable = true
                setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
                setOnClickListener {
                    commitText(emoji)
                    saveLastEmoji(emoji)
                    playTypewriterSound()
                    vibrateKey()
                    popup.dismiss()
                }
            }
            container.addView(b, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        popup.showAsDropDown(anchor, -(dp(48) * emojis.size), -(anchor.height + dp(64)))
    }

    private fun lastEmoji(): String = prefs.getString("last_emoji", recentEmojis().firstOrNull() ?: "😀") ?: "😀"

    private fun makeLetterKey(label: String, originalKey: String, shiftActive: Boolean, commit: (Boolean) -> Unit): Button {
        val button = makeKey(label) { commit(false) }
        button.setOnClickListener { commit(false); playTypewriterSound(); vibrateKey(); reactivePulse(button) }
        var held = false
        var holdPreview: PopupWindow? = null
        val holdRunnable = Runnable {
            held = true
            holdPreview = showUppercasePreview(button, originalKey.uppercase())
            vibrateCaseChange()
        }
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    held = false
                    holdPreview?.dismiss()
                    holdPreview = null
                    button.removeCallbacks(holdRunnable)
                    button.postDelayed(holdRunnable, 220L)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    button.removeCallbacks(holdRunnable)
                    holdPreview?.dismiss()
                    holdPreview = null
                    commit(held || shiftActive)
                    if (!held) vibrateKey()
                    playTypewriterSound()
                    button.text = if (shiftActive) originalKey else originalKey.lowercase()
                    reactivePulse(button)
                    held = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    button.removeCallbacks(holdRunnable)
                    holdPreview?.dismiss()
                    holdPreview = null
                    button.text = if (shiftActive) originalKey else originalKey.lowercase()
                    held = false
                    true
                }
                else -> true
            }
        }
        return button
    }

    private fun showUppercasePreview(anchor: View, text: String): PopupWindow {
        val density = resources.displayMetrics.density
        val label = TextView(this).apply {
            this.text = text
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(selectedPalette().text)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding((14 * density).toInt(), (7 * density).toInt(), (14 * density).toInt(), (7 * density).toInt())
            background = GradientDrawable().apply {
                setColor(selectedPalette().key)
                cornerRadius = 12f * density
                setStroke((1 * density).toInt(), selectedPalette().active)
            }
        }
        val popup = PopupWindow(
            label,
            (anchor.width.coerceAtLeast(dp(48)) * 1.18f).toInt(),
            (anchor.height.coerceAtLeast(dp(48)) * 1.05f).toInt(),
            false
        ).apply {
            isClippingEnabled = false
            elevation = 10f * density
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        label.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        popup.showAsDropDown(anchor, 0, -(anchor.height + popup.height + dp(5)))
        return popup
    }

    private fun makeQuestionKey(): Button {
        val button = makeKey("?") { commitText("?") }
        var longPressed = false
        val longPressRunnable = Runnable {
            longPressed = true
            vibrateCaseChange()
            reactivePulse(button)
            button.invalidate()
        }
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longPressed = false
                    button.text = "?"
                    button.removeCallbacks(longPressRunnable)
                    button.postDelayed(longPressRunnable, 600L)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    button.removeCallbacks(longPressRunnable)
                    if (longPressed) {
                        // La pulsación larga no escribe nada automáticamente: al soltar
                        // aparece el selector rápido de !, @ y .
                        showQuestionSymbolPopup(button)
                    } else {
                        commitText("?")
                        playTypewriterSound()
                        vibrateKey()
                        reactivePulse(button)
                    }
                    button.text = "?"
                    longPressed = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    button.removeCallbacks(longPressRunnable)
                    button.text = "?"
                    longPressed = false
                    true
                }
                else -> true
            }
        }
        button.setOnLongClickListener {
            showQuestionSymbolPopup(button)
            vibrateCaseChange()
            true
        }
        installDpadLongPress(button, 600L) {
            showQuestionSymbolPopup(button)
            vibrateCaseChange()
        }
        return button
    }

    private fun showQuestionSymbolPopup(anchor: View) {
        val density = resources.displayMetrics.density
        val symbols = listOf("!", "@", ".")
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = GradientDrawable().apply {
                setColor(selectedPalette().key)
                cornerRadius = 14f * density
                setStroke(dp(1), selectedPalette().active)
            }
        }
        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(58),
            false
        ).apply {
            isOutsideTouchable = true
            isTouchModal = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            elevation = 12f * density
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        symbols.forEach { symbol ->
            val item = TextView(this).apply {
                text = symbol
                textSize = 25f
                gravity = Gravity.CENTER
                isClickable = true
                setTextColor(selectedPalette().text)
                setPadding(dp(10), 0, dp(10), 0)
                setOnClickListener {
                    commitText(symbol)
                    playTypewriterSound()
                    vibrateKey()
                    reactivePulse(anchor)
                    popup.dismiss()
                }
            }
            container.addView(item, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        popup.showAsDropDown(anchor, -(dp(48) * symbols.size), -(anchor.height + dp(64)))
    }

    private fun makeEnterKey(heightScale: Float): Button {
        val palette = selectedPalette()
        val button = Button(this).apply {
            id = View.generateViewId()
            text = "⏎"
            textSize = 19f
            setTextColor(palette.text)
            isFocusable = true
            isFocusableInTouchMode = false
            isAllCaps = false
            applyKeyboardTypeface(this)
            setPadding(0, 0, 0, 0)
            val colorKey = prefs.getString("keyboard_color", "black") ?: "black"
            val fill = if (colorKey == "ferrari" || colorKey == "eva") palette.enter else palette.key
            background = GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), if (palette.background == Color.BLACK) Color.rgb(35, 35, 35) else adjustColor(fill, 0.82f))
            }
            setTag(R.id.tag_base_color, fill)
        }
        button.setOnClickListener {
            commitEnter()
            playTypewriterSound()
            vibrateKey()
            reactivePulse(button)
        }
        var longPressed = false
        val longPressRunnable = Runnable {
            longPressed = true
            vibrateCaseChange()
            playTypewriterSound()
            openSettings()
        }
        var enterDownX = 0f
        var enterDownY = 0f
        var enterSwipeHandled = false
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    enterDownX = event.x
                    enterDownY = event.y
                    enterSwipeHandled = false
                    longPressed = false
                    button.removeCallbacks(longPressRunnable)
                    button.postDelayed(longPressRunnable, 1000L)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    button.removeCallbacks(longPressRunnable)
                    val dx = event.x - enterDownX
                    val dy = event.y - enterDownY
                    if (!longPressed && dx < -dp(24) && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        deleteBeforeCursor()
                        playTypewriterSound()
                        vibrateKey()
                        reactivePulse(button)
                        enterSwipeHandled = true
                    } else if (!longPressed && !enterSwipeHandled) {
                        commitEnter()
                        playTypewriterSound()
                        vibrateKey()
                        reactivePulse(button)
                    }
                    longPressed = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    button.removeCallbacks(longPressRunnable)
                    longPressed = false
                    enterSwipeHandled = false
                    true
                }
                else -> true
            }
        }
        // Long-press ENTER from a physical remote/controller opens settings.
        button.setOnLongClickListener {
            vibrateCaseChange()
            playTypewriterSound()
            openSettings()
            true
        }
        installDpadLongPress(button, 1000L) {
            vibrateCaseChange()
            playTypewriterSound()
            openSettings()
        }
        return button
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun playTypewriterSound() {
        if (!prefs.getBoolean("typewriter_sound_enabled", false)) return
        runCatching {
            musicHandler.removeCallbacksAndMessages(null)
        typewriterPlayer?.release()
            typewriterPlayer = MediaPlayer.create(this, R.raw.typewriter_key)
            typewriterPlayer?.setOnCompletionListener { mp ->
                mp.release()
                if (typewriterPlayer === mp) typewriterPlayer = null
            }
            typewriterPlayer?.start()
        }
    }

    private fun rgbModeEnabled(): Boolean = prefs.getBoolean("rgb_mode_enabled", false)

    private fun reactivePulse(source: View) {
        if (!rgbModeEnabled() || !::root.isInitialized) return

        val sourceLocation = IntArray(2)
        source.getLocationOnScreen(sourceLocation)
        val sourceCx = sourceLocation[0] + source.width / 2f
        val sourceCy = sourceLocation[1] + source.height / 2f

        fun collect(view: View, out: MutableList<Button>) {
            if (view is Button) out += view
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) collect(view.getChildAt(i), out)
            }
        }

        val buttons = mutableListOf<Button>()
        collect(root, buttons)
        val targets = buttons.mapNotNull { button ->
            if (button.width == 0 || button.height == 0) return@mapNotNull null
            val location = IntArray(2)
            button.getLocationOnScreen(location)
            val cx = location[0] + button.width / 2f
            val cy = location[1] + button.height / 2f
            val dx = cx - sourceCx
            val dy = cy - sourceCy
            val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
            val base = (button.getTag(R.id.tag_base_color) as? Int) ?: return@mapNotNull null
            Triple(button, WavePoint(distance, angle, cx, cy), base)
        }

        if (targets.isEmpty()) return

        val maxDistance = targets.maxOf { it.second.distance }.coerceAtLeast(1f)
        val minX = targets.minOf { it.second.x }
        val maxX = targets.maxOf { it.second.x }
        val minY = targets.minOf { it.second.y }
        val maxY = targets.maxOf { it.second.y }
        val keyboardWidth = (maxX - minX).coerceAtLeast(1f)
        val keyboardHeight = (maxY - minY).coerceAtLeast(1f)
        val totalDuration = 400L
        val waveMode = prefs.getString("rgb_wave_mode", "expanding") ?: "expanding"
        val evaluator = ArgbEvaluator()
        val twoPi = Math.PI * 2.0

        if (waveMode == "zoom") {
            source.animate().cancel()
            source.animate()
                .scaleX(1.14f)
                .scaleY(1.14f)
                .setDuration(totalDuration / 2)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    source.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(totalDuration / 2)
                        .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                        .start()
                }
                .start()
        }

        targets.forEach { (button, point, base) ->
            val radial = (point.distance / maxDistance).coerceIn(0f, 1f)
            val angle = point.angle
            val drawable = when (val bg = button.background) {
                is GradientDrawable -> bg
                is LayerDrawable -> bg.getDrawable(0) as? GradientDrawable
                else -> null
            } ?: return@forEach
            val reactive = blendWithWhite(base, 0.20f + (1f - radial) * 0.58f)

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = totalDuration
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { animator ->
                    val t = animator.animatedFraction.toDouble()

                    // Each mode uses a different spatial function. The wave is evaluated
                    // continuously for every key, so the entire keyboard participates.
                    val intensity = when (waveMode) {
                        // Expanding ring: a bright ring travels from the pressed key outward.
                        "expanding" -> {
                            val ringWidth = 0.13
                            gaussian(radial - t, ringWidth)
                        }

                        // Spiral: the bright front rotates while simultaneously moving outward.
                        // Several turns make the illumination visibly travel around the source.
                        "spiral" -> {
                            val turns = 2.35
                            val expectedAngle = -Math.PI / 2.0 + t * turns * twoPi + radial * twoPi * 1.15
                            val angularDistance = shortestAngleDistance(angle, expectedAngle)
                            val angularGlow = gaussian(angularDistance, 0.48)
                            val radialGlow = gaussian(radial - t, 0.24)
                            (angularGlow * 0.82 + radialGlow * 0.18).coerceIn(0.0, 1.0)
                        }

                        // Circle: a clean growing circular ring. Unlike the expanding mode,
                        // the ring has a sharper, more pronounced crest and trailing fade.
                        "circle" -> {
                            val ring = gaussian(radial - t, 0.075)
                            val trailing = gaussian(radial - (t - 0.10), 0.20)
                            (ring * 0.90 + trailing * 0.10).coerceIn(0.0, 1.0)
                        }

                        // Edges -> center: starts at the outside of the keyboard and contracts
                        // toward the pressed key.
                        "edges" -> {
                            val targetRadius = 1.0 - t
                            gaussian(radial - targetRadius, 0.11)
                        }

                        // Zig-zag lightning: two simultaneous rays leave the pressed key,
                        // one travelling left and the other right. The vertical path alternates
                        // above and below the source, producing a jagged lightning-bolt shape.
                        "zigzag" -> {
                            val dx = point.x - sourceCx
                            val direction = if (dx >= 0f) 1f else -1f
                            val horizontalProgress = (kotlin.math.abs(dx) / (keyboardWidth / 2f))
                                .coerceIn(0f, 1f)
                                .toDouble()
                            val pathProgress = (horizontalProgress - t).coerceIn(-1.0, 1.0)
                            val zig = triangleWave(horizontalProgress * 5.0)
                            val amplitude = keyboardHeight * 0.22
                            val expectedY = sourceCy + (zig * amplitude).toFloat()
                            val verticalDistance = kotlin.math.abs(point.y - expectedY)
                            val pathGlow = gaussian(verticalDistance.toDouble(), keyboardHeight * 0.10)
                            val frontGlow = gaussian(pathProgress, 0.13)
                            val sourceGlow = if (horizontalProgress < 0.10)
                                gaussian(horizontalProgress.toDouble(), 0.16) else 0.0
                            (pathGlow * frontGlow * (0.82 + 0.18 * direction.toDouble().let { kotlin.math.abs(it) }) + sourceGlow)
                                .coerceIn(0.0, 1.0)
                        }

                        // Zoom: smoothly enlarges only the pressed key, then returns to its original size.
                        "zoom" -> {
                            val isSource = button === source
                            if (isSource) {
                                val zoom = kotlin.math.sin(Math.PI * t).coerceAtLeast(0.0)
                                (zoom * 0.98).coerceIn(0.0, 1.0)
                            } else 0.0
                        }

                        // Music equalizer: the keyboard becomes a set of animated vertical bars.
                        // Each x-position gets its own phase and height, moving continuously.
                        "equalizer" -> {
                            val column = ((point.x - minX) / keyboardWidth).coerceIn(0f, 1f).toDouble()
                            val vertical = (1.0 - ((point.y - minY) / keyboardHeight).coerceIn(0f, 1f))
                            val barPhase = t * Math.PI * 4.0 + column * Math.PI * 10.0
                            val level = 0.18 + 0.82 * (0.5 + 0.5 * kotlin.math.sin(barPhase))
                            val barGlow = if (vertical <= level) {
                                val edge = gaussian(vertical - level, 0.12)
                                (0.42 + 0.58 * edge)
                            } else {
                                gaussian(vertical - level, 0.08) * 0.28
                            }
                            val columnGlow = 0.72 + 0.28 * kotlin.math.sin(t * Math.PI * 2.0 + column * Math.PI * 6.0)
                            (barGlow * columnGlow).coerceIn(0.0, 1.0)
                        }

                        else -> gaussian(radial - t, 0.13)
                    }

                    // Keep the pressed key visibly involved and give the whole effect a smooth
                    // fade-in/fade-out envelope.
                    val envelope = kotlin.math.sin(Math.PI * t).coerceAtLeast(0.0)
                    val sourceBoost = if (radial < 0.10f) (1.0 - radial * 4.0) * 0.25 else 0.0
                    val amount = ((intensity * envelope) + sourceBoost).coerceIn(0.0, 1.0).toFloat()
                    drawable.setColor(evaluator.evaluate(amount, base, reactive) as Int)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        drawable.setColor(base)
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        drawable.setColor(base)
                    }
                })
                start()
            }
        }
    }

    private fun gaussian(value: Double, width: Double): Double {
        val normalized = value / width
        return kotlin.math.exp(-(normalized * normalized) * 2.0).coerceIn(0.0, 1.0)
    }

    private fun triangleWave(value: Double): Double {
        val phase = value - kotlin.math.floor(value)
        return if (phase < 0.5) phase * 2.0 else 2.0 - phase * 2.0
    }

    private fun shortestAngleDistance(a: Double, b: Double): Double {
        var d = (a - b) % (Math.PI * 2.0)
        if (d > Math.PI) d -= Math.PI * 2.0
        if (d < -Math.PI) d += Math.PI * 2.0
        return kotlin.math.abs(d)
    }

    private data class WavePoint(
        val distance: Float,
        val angle: Double,
        val x: Float,
        val y: Float
    )

    private fun blendWithWhite(color: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(color) + (255 - Color.red(color)) * a).toInt().coerceIn(0, 255),
            (Color.green(color) + (255 - Color.green(color)) * a).toInt().coerceIn(0, 255),
            (Color.blue(color) + (255 - Color.blue(color)) * a).toInt().coerceIn(0, 255)
        )
    }

    private fun applyKeyboardTypeface(view: TextView) {
        if (prefs.getString("keyboard_color", "black") == "gameboy") {
            // Dot-matrix inspired typography for the Gameboy palette.
            view.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            view.letterSpacing = 0.08f
        } else {
            view.typeface = Typeface.DEFAULT
            view.letterSpacing = 0f
        }
    }

    private fun installDpadLongPress(button: Button, duration: Long, action: () -> Unit) {
        var triggered = false
        val runnable = Runnable {
            triggered = true
            action()
        }
        button.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) return@setOnKeyListener false
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        triggered = false
                        button.removeCallbacks(runnable)
                        button.postDelayed(runnable, duration)
                    }
                    false
                }
                KeyEvent.ACTION_UP -> {
                    button.removeCallbacks(runnable)
                    if (triggered) {
                        triggered = false
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun makeKey(text: CharSequence, action: () -> Unit): Button {
        val palette = selectedPalette()
        return Button(this).apply {
            id = View.generateViewId()
            this.text = text
            textSize = if (text.length > 3) 11f else 19f
            setTextColor(palette.text)
            isFocusable = true
            isFocusableInTouchMode = false
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                val active = (text == "⇧" && shifted) || (text == "ABC" && keyboardMode == 2)
                val fill = if (active) palette.active else palette.key
                setColor(fill)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), if (palette.background == Color.BLACK) Color.rgb(35,35,35) else adjustColor(fill, 0.82f))
            }
            val fill = if ((text == "⇧" && shifted) || (text == "ABC" && keyboardMode == 2)) palette.active else palette.key
            setTag(R.id.tag_base_color, fill)
            setOnClickListener { action(); playTypewriterSound(); vibrateKey(); reactivePulse(this) }
        }
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun deleteBeforeCursor() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    /**
     * Deletes the last word before the cursor. If there is a space immediately
     * before the cursor, that separator is removed together with the word.
     */
    private fun deleteLastWord() {
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(100, 0)?.toString() ?: return
        if (before.isEmpty()) return

        var end = before.length
        while (end > 0 && before[end - 1].isWhitespace()) end--
        if (end == 0) {
            connection.deleteSurroundingText(before.length, 0)
            return
        }

        var start = end
        while (start > 0 && !before[start - 1].isWhitespace()) start--
        val count = before.length - start
        connection.deleteSurroundingText(count, 0)
    }

    private fun commitEnter() {
        val connection = currentInputConnection ?: return
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            connection.performEditorAction(action)
        } else {
            connection.commitText("\n", 1)
        }
    }

    private fun refreshKeyboard() {
        setInputView(buildKeyboardView())
    }

    private fun buildKeyboardView(): View {
        buildKeyboard()
        return root
    }

    private fun animateToKeyboard() {
        phraseMode = false
        val old = root
        buildKeyboard()
        root.alpha = 0f
        root.translationY = dp(12).toFloat()
        setInputView(root)
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        old.alpha = 0f
    }

    private fun vibrateKey() {
        if (!prefs.getBoolean("vibration_enabled", true)) return
        vibrateForStrength(8L + prefs.getInt("vibration_strength", 45).coerceIn(0, 100) / 8L)
    }

    private fun vibrateForStrength(duration: Long) {
        val strength = prefs.getInt("vibration_strength", 45).coerceIn(0, 100)
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            val amplitude = (1 + strength * 254 / 100).coerceIn(1, 255)
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(duration)
        }
    }

    private fun vibrateGesture() {
        if (!prefs.getBoolean("vibration_enabled", true)) return
        // Respuesta háptica fuerte al finalizar cualquier deslizamiento.
        vibrateForStrength(30L)
    }

    private fun vibrateCaseChange() {
        vibrateForStrength(14L)
    }

    private fun showPhraseLayout() {
        if (phraseMode) return
        phraseMode = true
        val heightScale = keyboardHeightScale()
        val palette = selectedPalette()
        val old = root

        val phraseRoot = KeyboardRoot(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpScaled(8, heightScale), dpScaled(6, heightScale), dpScaled(8, heightScale), dpScaled(6, heightScale))
            background = GradientDrawable().apply {
                setColor(palette.background)
                val radius = dpScaled(20, heightScale).toFloat()
                setCornerRadii(floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f))
            }
            onSwipeDelete = {
                lastDeleteSwipeAt = 0L
                deleteBeforeCursor()
            }
        }
        root = phraseRoot

        val title = TextView(this).apply {
            text = "Frases"
            textSize = 17f
            setTextColor(palette.active)
            applyKeyboardTypeface(this)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        phraseRoot.addView(title, LinearLayout.LayoutParams(-1, dpScaled(30, heightScale)))

        val flow = PhraseFlowLayout(this).apply {
            setPadding(dpScaled(2, heightScale), dpScaled(2, heightScale), dpScaled(2, heightScale), dpScaled(2, heightScale))
            horizontalSpacing = dpScaled(6, heightScale)
            verticalSpacing = dpScaled(6, heightScale)
        }

        loadPhrases().forEach { phrase ->
            val bubble = makePhraseKey(phrase, palette, heightScale)
            bubble.setOnClickListener {
                commitText(phrase)
                playTypewriterSound()
                vibrateGesture()
                animateToKeyboard()
            }
            flow.addView(bubble)
        }
        phraseRoot.addView(flow, LinearLayout.LayoutParams(-1, 0, 1f))

        phraseRoot.alpha = 0f
        phraseRoot.translationY = dp(18).toFloat()
        setInputView(phraseRoot)
        phraseRoot.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        old.animate().alpha(0f).setDuration(100L).start()
    }

    private fun makePhraseKey(text: String, palette: Palette, heightScale: Float): TextView = TextView(this).apply {
        id = View.generateViewId()
        this.text = text
        textSize = 15f
        setTextColor(palette.text)
        applyKeyboardTypeface(this)
        gravity = Gravity.CENTER
        setPadding(dpScaled(14, heightScale), dpScaled(9, heightScale), dpScaled(14, heightScale), dpScaled(9, heightScale))
        background = GradientDrawable().apply {
            setColor(palette.key)
            cornerRadius = dpScaled(18, heightScale).toFloat()
            setStroke(dp(1), adjustColor(palette.key, 0.82f))
        }
        isClickable = true
        isFocusable = true
    }

    private inner class PhraseFlowLayout(context: Context) : android.view.ViewGroup(context) {
        var horizontalSpacing = dp(6)
        var verticalSpacing = dp(6)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val available = (width - paddingLeft - paddingRight).coerceAtLeast(0)
            var lineWidth = 0
            var lineHeight = 0
            var totalHeight = paddingTop + paddingBottom

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
                val lp = child.layoutParams as MarginLayoutParams
                val childWidth = child.measuredWidth + lp.leftMargin + lp.rightMargin
                val childHeight = child.measuredHeight + lp.topMargin + lp.bottomMargin
                val nextWidth = if (lineWidth == 0) childWidth else lineWidth + horizontalSpacing + childWidth
                if (lineWidth > 0 && nextWidth > available) {
                    totalHeight += lineHeight + verticalSpacing
                    lineWidth = childWidth
                    lineHeight = childHeight
                } else {
                    lineWidth = nextWidth
                    lineHeight = maxOf(lineHeight, childHeight)
                }
            }
            totalHeight += lineHeight
            val resolvedHeight = resolveSize(totalHeight, heightMeasureSpec)
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolvedHeight)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val available = (right - left - paddingLeft - paddingRight).coerceAtLeast(0)
            var x = paddingLeft
            var y = paddingTop
            var lineHeight = 0

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val lp = child.layoutParams as MarginLayoutParams
                val childWidth = child.measuredWidth
                val childHeight = child.measuredHeight
                val nextX = if (x == paddingLeft) x + lp.leftMargin + childWidth + lp.rightMargin
                    else x + horizontalSpacing + lp.leftMargin + childWidth + lp.rightMargin

                if (x != paddingLeft && nextX > right - left - paddingRight) {
                    x = paddingLeft
                    y += lineHeight + verticalSpacing
                    lineHeight = 0
                }

                val childLeft = if (x == paddingLeft) x + lp.leftMargin else x + horizontalSpacing + lp.leftMargin
                val childTop = y + lp.topMargin
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
                x = childLeft + childWidth + lp.rightMargin
                lineHeight = maxOf(lineHeight, lp.topMargin + childHeight + lp.bottomMargin)
            }
        }

        override fun generateDefaultLayoutParams(): MarginLayoutParams =
            MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

        override fun generateLayoutParams(attrs: android.util.AttributeSet?): MarginLayoutParams =
            MarginLayoutParams(context, attrs)

        override fun generateLayoutParams(p: android.view.ViewGroup.LayoutParams?): MarginLayoutParams =
            MarginLayoutParams(p)

        override fun checkLayoutParams(p: android.view.ViewGroup.LayoutParams?): Boolean = p is MarginLayoutParams
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

    private fun keyboardHeightScale(): Float {
        val percent = prefs.getInt("keyboard_height_percent", 100).coerceIn(70, 100)
        return percent / 100f
    }

    private fun weightParams(weight: Float, heightScale: Float) = LinearLayout.LayoutParams(0, dpScaled(52, heightScale), weight).apply {
        setMargins(dpScaled(2, heightScale), dpScaled(2, heightScale), dpScaled(2, heightScale), dpScaled(2, heightScale))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dpScaled(value: Int, scale: Float): Int = (dp(value) * scale).toInt().coerceAtLeast(1)

    private fun adjustColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private inner class KeyboardRoot(context: Context) : LinearLayout(context) {
        var onSwipeDelete: (() -> Unit)? = null
        private var downX = 0f
        private var downY = 0f
        private var trackingGesture = false
        private var gestureTriggered = false
        private var spaceGestureCandidate = false
        private var verticalPhraseGesture = false

        init {
            isClickable = true
            // Do not let the root consume TV/DPAD focus. Focus belongs to keys.
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipChildren = false
            clipToPadding = false
        }

        // Wire a predictable spatial focus grid for Android TV and other
        // non-touch devices. The normal phone touch behavior is unchanged.
        fun configureDpadNavigation() {
            if (!::root.isInitialized) return
            val rows = mutableListOf<List<View>>()
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child is ViewGroup) {
                    val focusable = (0 until child.childCount)
                        .map { child.getChildAt(it) }
                        .filter { it.isFocusable }
                    if (focusable.isNotEmpty()) rows += focusable
                }
            }
            rows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { index, view ->
                    if (view.id == View.NO_ID) view.id = View.generateViewId()
                    view.nextFocusLeftId = row.getOrNull(index - 1)?.id ?: row.last().id
                    view.nextFocusRightId = row.getOrNull(index + 1)?.id ?: row.first().id
                    val above = rows.getOrNull(rowIndex - 1)
                    val below = rows.getOrNull(rowIndex + 1)
                    if (above != null) view.nextFocusUpId = above[(index * above.size / row.size).coerceIn(0, above.lastIndex)].id
                    if (below != null) view.nextFocusDownId = below[(index * below.size / row.size).coerceIn(0, below.lastIndex)].id
                }
            }
        }

        fun requestInitialKeyboardFocus() {
            if (!::root.isInitialized) return
            val first = findFirstFocusable(root) ?: return
            first.requestFocus()
        }

        private fun findFirstFocusable(group: ViewGroup): View? {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child.isFocusable) return child
                if (child is ViewGroup) {
                    val nested = findFirstFocusable(child)
                    if (nested != null) return nested
                }
            }
            return null
        }

        // Intercept the gesture once it is clearly a keyboard-wide swipe.
        // This prevents the individual key Button from stealing the sequence.
        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    trackingGesture = true
                    gestureTriggered = false
                    verticalPhraseGesture = false
                    spaceGestureCandidate = findTouchTarget(this, event.x, event.y)
                        ?.getTag(R.id.tag_space_key) == true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (trackingGesture && !gestureTriggered) {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        val horizontal = abs(dx) >= dp(40) && abs(dx) > abs(dy) * 1.15f
                        val upward = dy <= -dp(40) && abs(dy) > abs(dx) * 1.15f
                        val downward = dy >= dp(40) && abs(dy) > abs(dx) * 1.15f
                        if ((horizontal && !spaceGestureCandidate) || upward || downward) {
                            gestureTriggered = true
                            parent?.requestDisallowInterceptTouchEvent(true)

                            // A vertical swipe always opens the phrase layout.
                            // Trigger it as soon as the direction is recognized,
                            // so child buttons cannot consume the gesture before
                            // the ACTION_UP event.
                            if ((upward || downward) && !phraseMode) {
                                verticalPhraseGesture = true
                                showPhraseLayout()
                                vibrateGesture()
                            }
                            return true
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    trackingGesture = false
                    gestureTriggered = false
                    spaceGestureCandidate = false
                    verticalPhraseGesture = false
                }
            }
            return false
        }

        private fun findTouchTarget(group: ViewGroup, x: Float, y: Float): View? {
            for (i in group.childCount - 1 downTo 0) {
                val child = group.getChildAt(i)
                if (x >= child.left && x < child.right && y >= child.top && y < child.bottom) {
                    if (child is ViewGroup) {
                        val nested = findTouchTarget(child, x - child.left, y - child.top)
                        if (nested != null) return nested
                    }
                    return child
                }
            }
            return null
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    trackingGesture = true
                    gestureTriggered = false
                    spaceGestureCandidate = findTouchTarget(this, event.x, event.y)
                        ?.getTag(R.id.tag_space_key) == true
                    return true
                }
                MotionEvent.ACTION_MOVE -> return true
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val horizontal = abs(dx) >= dp(40) && abs(dx) > abs(dy) * 1.15f
                    val upward = dy <= -dp(40) && abs(dy) > abs(dx) * 1.15f
                    val downward = dy >= dp(40) && abs(dy) > abs(dx) * 1.15f
                    val wasGesture = horizontal || upward || downward || gestureTriggered
                    trackingGesture = false
                    gestureTriggered = false
                    spaceGestureCandidate = false

                    if (verticalPhraseGesture) {
                        verticalPhraseGesture = false
                        performClick()
                        return true
                    }

                    if (wasGesture) {
                        when {
                            upward && !phraseMode -> {
                                // Teclado normal -> Frases
                                showPhraseLayout()
                                vibrateGesture()
                            }
                            downward && !phraseMode -> {
                                // También permitimos abajo -> arriba/abajo para abrir Frases.
                                showPhraseLayout()
                                vibrateGesture()
                            }
                            downward && phraseMode -> {
                                // En Frases, deslizar de arriba hacia abajo vuelve al teclado normal.
                                animateToKeyboard()
                                vibrateGesture()
                            }
                            upward && phraseMode -> {
                                // Mantener arriba -> abajo/arriba sobre Frases no cierra el layout.
                                vibrateGesture()
                            }
                            horizontal && dx < 0f -> {
                                val now = SystemClock.elapsedRealtime()
                                val withinDoubleDeleteSwipe =
                                    lastDeleteSwipeAt != 0L && (now - lastDeleteSwipeAt) < 500L

                                if (withinDoubleDeleteSwipe) {
                                    // Segundo deslizamiento rápido: borra la última palabra.
                                    deleteLastWord()
                                    lastDeleteSwipeAt = 0L
                                } else {
                                    // Primer deslizamiento: borra únicamente el último carácter.
                                    deleteBeforeCursor()
                                    lastDeleteSwipeAt = now
                                }
                                lastRightSwipeAt = 0L
                                vibrateGesture()
                            }
                            horizontal && dx > 0f -> {
                                val now = SystemClock.elapsedRealtime()
                                val withinDoubleSwipe = lastRightSwipeAt != 0L && (now - lastRightSwipeAt) < 500L
                                val before = currentInputConnection?.getTextBeforeCursor(1, 0)?.toString()

                                if (withinDoubleSwipe && before == " ") {
                                    currentInputConnection?.deleteSurroundingText(1, 0)
                                    currentInputConnection?.commitText(".", 1)
                                    lastRightSwipeAt = 0L
                                } else {
                                    currentInputConnection?.commitText(" ", 1)
                                    lastRightSwipeAt = now
                                }
                                vibrateGesture()
                            }
                            else -> {
                                lastRightSwipeAt = 0L
                            }
                        }
                        performClick()
                        return true
                    }
                    return performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    trackingGesture = false
                    gestureTriggered = false
                    verticalPhraseGesture = false
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    }
}
