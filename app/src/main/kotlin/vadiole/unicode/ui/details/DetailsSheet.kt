package vadiole.unicode.ui.details

import android.content.Context
import android.graphics.*
import android.view.Gravity
import kotlinx.coroutines.launch
import vadiole.unicode.data.CharStorage
import vadiole.unicode.ui.components.RecticlePathHelper
import vadiole.unicode.ui.components.Screen
import vadiole.unicode.ui.theme.*
import vadiole.unicode.utils.dp
import vadiole.unicode.utils.frameParams
import vadiole.unicode.utils.ktx.Text
import vadiole.unicode.utils.matchParent
import vadiole.unicode.utils.wrapContent

class DetailsSheet(
    context: Context,
    theme: AppTheme,
    private val charStorage: CharStorage,
) : Screen(context), ThemeDelegate {
    private val cornerRadius = 33.dp(context)
    private val viewBounds = RectF()
    private val recticlePath = Path()
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Text {
        layoutParams = frameParams(matchParent, wrapContent, gravity = Gravity.TOP, marginTop = 20.dp(context))
        text = "Char details"
        setTextColor(Color.WHITE)
        textSize = 24f
        gravity = Gravity.CENTER
    }

    private val charView = Text {
        layoutParams = frameParams(matchParent, wrapContent, gravity = Gravity.TOP, marginTop = 120.dp(context))
        setTextColor(Color.WHITE)
        textSize = 128f
        gravity = Gravity.CENTER
    }

    init {
        layoutParams = frameParams(matchParent, 500.dp(context), gravity = Gravity.BOTTOM)
        setWillNotDraw(false)
        addView(title)
        addView(charView)
        theme.observe(this)
    }

    fun bind(id: Int) = launch {
        val codePoint = charStorage.getCodePoint(id)
        val char = String(Character.toChars(codePoint))
        charView.text = char
    }

    override fun applyTheme(theme: Theme) {
        backgroundPaint.color = theme.getColor(key_dialogBackground)
        charView.setTextColor(theme.getColor(key_dialogTextPrimary))
        title.setTextColor(theme.getColor(key_dialogTextPrimary))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        viewBounds.set(0f, 0f, w.toFloat(), h + 100f.dp(context))
        RecticlePathHelper.buildPath(recticlePath, viewBounds, cornerRadius, skipBottom = true)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(recticlePath, backgroundPaint)
        super.draw(canvas)
    }
}