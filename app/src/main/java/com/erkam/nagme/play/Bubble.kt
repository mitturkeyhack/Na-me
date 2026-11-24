package com.erkam.nagme.play

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.erkam.nagme.MainActivity
import com.erkam.nagme.R
import com.erkam.nagme.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

private val ACCENT get() = com.erkam.nagme.data.Store.settings.value.accent.toInt()

/** Müzik çalarken diğer uygulamaların üstünde duran, dönen plak gibi yüzen simge. */
class Bubble(private val ctx: Context, private val player: Player) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dp = ctx.resources.displayMetrics.density
    private fun Int.dp() = (this * dp).toInt()

    private var appForeground = true
    private var dismissed = false
    private var bubbleView: BubbleView? = null
    private var panel: Panel? = null
    private val bubbleParams = WindowManager.LayoutParams(
        96.dp(), 96.dp(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = ctx.resources.displayMetrics.widthPixels - 92.dp()
        y = (ctx.resources.displayMetrics.heightPixels * 0.42f).toInt()
    }

    private val lifecycleObserver = LifecycleEventObserver { owner, _ ->
        val fg = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (fg && !appForeground) dismissed = false
        appForeground = fg
        update()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            bubbleView?.playing = isPlaying
            panel?.refresh()
            update()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            loadCover(); panel?.refresh(); update()
        }

        override fun onPlaybackStateChanged(playbackState: Int) = update()
    }

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        player.addListener(listener)
        scope.launch { Store.settings.collectLatest { update() } }
    }

    fun stop() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        player.removeListener(listener)
        hideAll()
        scope.cancel()
    }

    private fun shouldShow() = Store.settings.value.bubble &&
        Settings.canDrawOverlays(ctx) &&
        !appForeground && !dismissed &&
        player.mediaItemCount > 0 && player.playbackState != Player.STATE_IDLE

    private fun update() {
        if (shouldShow()) show() else hideAll()
    }

    private fun show() {
        if (bubbleView != null) return
        val v = BubbleView(ctx)
        v.playing = player.isPlaying
        bubbleView = v
        runCatching { wm.addView(v, bubbleParams) }.onFailure { bubbleView = null; return }
        loadCover()
    }

    private fun hideAll() {
        hidePanel()
        bubbleView?.let { runCatching { wm.removeView(it) } }
        bubbleView = null
    }

    private fun loadCover() {
        val uri = player.currentMediaItem?.mediaMetadata?.artworkUri ?: return
        scope.launch {
            val req = ImageRequest.Builder(ctx).data(uri).size(256).allowHardware(false).build()
            val r = SingletonImageLoader.get(ctx).execute(req)
            if (r is SuccessResult) bubbleView?.cover = r.image.toBitmap()
        }
    }

    private fun togglePanel() {
        if (panel != null) hidePanel() else showPanel()
    }

    private val autoHide = Runnable { hidePanel() }

    private fun showPanel() {
        val p = Panel(ctx)
        val w = 250.dp()
        val h = 118.dp()
        val screenW = ctx.resources.displayMetrics.widthPixels
        val onRight = bubbleParams.x + 48.dp() > screenW / 2
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (onRight) (bubbleParams.x - w + 8.dp()).coerceAtLeast(8.dp()) else bubbleParams.x + 88.dp()
            y = bubbleParams.y + 48.dp() - h / 2
        }
        panel = p
        runCatching { wm.addView(p.root, lp) }.onFailure { panel = null; return }
        p.refresh()
        main.removeCallbacks(autoHide)
        main.postDelayed(autoHide, 7000)
    }

    private fun hidePanel() {
        main.removeCallbacks(autoHide)
        panel?.let { runCatching { wm.removeView(it.root) } }
        panel = null
    }

    private fun touchPanel() {
        main.removeCallbacks(autoHide)
        main.postDelayed(autoHide, 7000)
    }

    /** Dokununca açılan küçük kumanda. */
    private inner class Panel(c: Context) {
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 12.dp(), 10.dp(), 8.dp())
            background = GradientDrawable().apply {
                cornerRadius = 20f * dp
                setColor(0xF21E2330.toInt())
                setStroke(1.dp(), 0xFF2E3546.toInt())
            }
        }
        private val title = TextView(c).apply {
            setTextColor(Color.WHITE); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        private val artist = TextView(c).apply {
            setTextColor(0xFFB9C0CE.toInt()); textSize = 12f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        private val playBtn = button(R.drawable.ic_pause, "Duraklat", accent = true) {
            if (player.isPlaying) player.pause() else player.play()
        }

        init {
            val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val texts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(title); texts.addView(artist)
            top.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(button(R.drawable.ic_close, "Yüzen simgeyi kapat", small = true) {
                dismissed = true; hideAll()
            })
            root.addView(top)
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(button(R.drawable.ic_prev, "Önceki") { player.seekToPrevious() }, weightLp())
            row.addView(playBtn, LinearLayout.LayoutParams(52.dp(), 52.dp()))
            row.addView(button(R.drawable.ic_next, "Sonraki") { player.seekToNext() }, weightLp())
            row.addView(button(R.drawable.ic_open, "Uygulamayı aç") {
                hidePanel()
                ctx.startActivity(
                    Intent(ctx, MainActivity::class.java)
                        .putExtra("player", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }, weightLp())
            root.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 56.dp()))
        }

        private fun weightLp() = LinearLayout.LayoutParams(0, 52.dp(), 1f)

        private fun button(icon: Int, label: String, accent: Boolean = false, small: Boolean = false, onClick: () -> Unit) =
            ImageButton(ctx).apply {
                setImageResource(icon)
                contentDescription = label
                scaleType = android.widget.ImageView.ScaleType.CENTER
                if (accent) {
                    setColorFilter(0xFF1A1206.toInt())
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ACCENT) }
                } else {
                    setColorFilter(Color.WHITE)
                    background = null
                }
                if (small) layoutParams = LinearLayout.LayoutParams(36.dp(), 36.dp())
                setOnClickListener { touchPanel(); onClick(); refresh() }
            }

        fun refresh() {
            val m = player.currentMediaItem?.mediaMetadata
            title.text = m?.title ?: ""
            artist.text = m?.artist ?: ""
            playBtn.setImageResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }
    }

    /** Dönen kapak + yayılan halkalar + oynayan tını çubukları. */
    @SuppressLint("ViewConstructor")
    private inner class BubbleView(c: Context) : View(c) {
        var cover: Bitmap? = null
            set(value) { field = value; shader = null; invalidate() }
        var playing = false
            set(value) { field = value; invalidate() }

        private var shader: BitmapShader? = null
        private val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val m = Matrix()
        private var angle = 0f
        private var lastFrame = 0L
        private val slop = ViewConfiguration.get(c).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        init { contentDescription = "Nağme kontrolleri" }

        override fun onDraw(canvas: Canvas) {
            val now = System.nanoTime() / 1_000_000L
            val dt = if (lastFrame == 0L) 0L else now - lastFrame
            lastFrame = now
            if (playing) angle = (angle + dt * 360f / 6000f) % 360f

            val cx = width / 2f
            val cy = height / 2f
            val r = 30f * dp

            // Yayılan halkalar
            if (playing) {
                for (k in 0..1) {
                    val t = ((now % 1800L) / 1800f + k * 0.5f) % 1f
                    ring.color = ACCENT
                    ring.strokeWidth = 2f * dp
                    ring.alpha = ((1f - t) * 140).toInt()
                    canvas.drawCircle(cx, cy, r + t * 16f * dp, ring)
                }
            }

            // Gölge benzeri koyu taban
            fill.color = 0x66000000
            canvas.drawCircle(cx, cy + 2 * dp, r + 2 * dp, fill)

            // Kapak (dönen plak)
            val bmp = cover
            if (bmp != null) {
                val s = shader ?: BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { shader = it }
                val scale = (2 * r) / min(bmp.width, bmp.height)
                m.reset()
                m.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
                m.postScale(scale, scale)
                m.postRotate(angle)
                m.postTranslate(cx, cy)
                s.setLocalMatrix(m)
                coverPaint.shader = s
                canvas.drawCircle(cx, cy, r, coverPaint)
            } else {
                fill.color = 0xFF2F5D5A.toInt()
                canvas.drawCircle(cx, cy, r, fill)
            }
            // Plak ortası
            fill.color = 0xFF14211F.toInt()
            canvas.drawCircle(cx, cy, 7f * dp, fill)
            fill.color = 0x55FFFFFF
            canvas.drawCircle(cx, cy, 2.5f * dp, fill)
            // Kenarlık
            ring.color = ACCENT; ring.alpha = 255; ring.strokeWidth = 3f * dp
            canvas.drawCircle(cx, cy, r, ring)

            // Tını rozeti
            val bx = cx + r * 0.72f
            val by = cy + r * 0.72f
            fill.color = 0xFF0F0F12.toInt()
            canvas.drawCircle(bx, by, 13f * dp, fill)
            fill.color = ACCENT
            val barW = 3f * dp
            val maxH = 12f * dp
            for (i in 0..2) {
                val phase = now / 900.0 * 2 * Math.PI + i * 2.1
                val h = if (playing) maxH * (0.3f + 0.7f * abs(sin(phase)).toFloat()) else maxH * 0.3f
                val x = bx - 6f * dp + i * 4.5f * dp
                canvas.drawRoundRect(x, by + maxH / 2 - h, x + barW, by + maxH / 2, barW / 2, barW / 2, fill)
            }

            if (playing) postInvalidateOnAnimation() else lastFrame = 0L
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = bubbleParams.x; startY = bubbleParams.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) { dragging = true; hidePanel() }
                    if (dragging) {
                        bubbleParams.x = (startX + dx).toInt()
                        bubbleParams.y = (startY + dy).toInt()
                        runCatching { wm.updateViewLayout(this, bubbleParams) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) { performClick(); togglePanel() } else snapToEdge()
                }
            }
            return true
        }

        override fun performClick(): Boolean { super.performClick(); return true }

        private fun snapToEdge() {
            val screenW = ctx.resources.displayMetrics.widthPixels
            val screenH = ctx.resources.displayMetrics.heightPixels
            bubbleParams.x = if (bubbleParams.x + width / 2 > screenW / 2) screenW - width + 4.dp() else -4.dp()
            bubbleParams.y = bubbleParams.y.coerceIn(40.dp(), screenH - 160.dp())
            runCatching { wm.updateViewLayout(this, bubbleParams) }
        }
    }
}
