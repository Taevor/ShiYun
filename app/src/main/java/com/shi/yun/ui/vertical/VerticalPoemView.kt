package com.shi.yun.ui.vertical

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shi.yun.data.model.Work
import com.shi.yun.data.util.PoemCoupletExtractor
import com.shi.yun.ui.detail.PoemTypography
import com.shi.yun.ui.theme.LocalAppFontFamily
import com.shi.yun.ui.theme.LocalAppTheme

/**
 * 诗云首页推荐卡片竖排展示组件
 *
 * 核心章法特性：
 * 1. 采用方案 B：纯 Kotlin 排版引擎计算 + Android 原生 Canvas/TextPaint 硬件加速绘制；
 * 2. 深度适配诗云全局字体管理体系（自动响应 [LocalAppFontFamily]，兼容系统默认、衬线宋体、等宽及导入字体）；
 * 3. 深度适配十套全局主题（文字颜色实时提取自当前主题的 titleText 与 secondaryText）；
 * 4. 右上角起首出句与对句，第一列靠右从上至下排列，后续列向左排列；
 * 5. 左下角附近落款作品名称与作者（如《静夜思》李白，不带朝代），从左下角底端向上延伸；
 * 6. 正文与落款自然呼吸留白，不居中，不挤压，完全消除横排标点带来的视觉生硬。
 */
@Composable
fun VerticalPoemCardContent(
    work: Work,
    modifier: Modifier = Modifier,
) {
    val appTheme = LocalAppTheme.current
    val textColor = appTheme.customTokens.titleText
    val attributionColor = appTheme.customTokens.secondaryText

    // 解析当前生效的 Android 原生 Typeface（统一绑定至诗云字体管理体系）
    val fontFamily = LocalAppFontFamily.current
    val resolver = LocalFontFamilyResolver.current
    val activeTypeface = remember(fontFamily, resolver) {
        val resolved = resolver.resolve(fontFamily).value
        (resolved as? Typeface) ?: Typeface.SERIF
    }

    // 结构化提取两句完整诗句与极简落款信息（内容选取与排版彻底解耦）
    val couplet = remember(work.id, work.excerpt, work.content) {
        PoemCoupletExtractor.extractCouplet(work)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val density = LocalDensity.current
        val baseFontSizePx = with(density) { 24.sp.toPx() }
        val attributionFontSizePx = with(density) { 12.5.sp.toPx() }
        val columnSpacingPx = with(density) { 28.dp.toPx() }
        val lineSpacingPx = with(density) { 4.5.dp.toPx() }
        val attrLineSpacingPx = with(density) { 2.dp.toPx() }
        val attrTitleAuthorGapPx = with(density) { 7.dp.toPx() }

        // 四周典雅留白边距
        val paddingTopPx = with(density) { 26.dp.toPx() }
        val paddingRightPx = with(density) { 30.dp.toPx() }
        val paddingLeftPx = with(density) { 28.dp.toPx() }
        val paddingBottomPx = with(density) { 26.dp.toPx() }

        val clauses = if (couplet != null) {
            listOf(couplet.line1, couplet.line2)
        } else {
            PoemTypography.splitIntoClauses(work.excerpt.ifBlank { work.title })
        }

        // 首页古典竖排落款：结构化解析作品名与作者
        val (attrTitle, attrAuthor) = if (couplet != null) {
            val title = couplet.title.trim().removePrefix("《").removeSuffix("》").trim()
            Pair(title, couplet.author.trim())
        } else {
            val cleanTitle = work.title.trim().removePrefix("《").removeSuffix("》").trim()
            val cleanAuthor = work.author.trim()
                .replace(Regex("^[·•\\[〔【(（].*?[·•\\]〕】)）]"), "")
                .replace(Regex("^[·•\\-—\\s]+"), "")
                .trim()
                .ifBlank { "佚名" }
            Pair(cleanTitle, cleanAuthor)
        }

        val layoutResult = remember(
            work.id,
            clauses,
            attrTitle,
            attrAuthor,
            widthPx,
            heightPx,
            activeTypeface,
            baseFontSizePx,
            attributionFontSizePx,
            columnSpacingPx,
            lineSpacingPx,
            attrLineSpacingPx,
            attrTitleAuthorGapPx,
        ) {
            VerticalLayoutEngine.layout(
                VerticalLayoutInput(
                    text = "",
                    clauses = clauses,
                    attributionTitle = attrTitle,
                    attributionAuthor = attrAuthor,
                    availableWidth = widthPx,
                    availableHeight = heightPx,
                    baseFontSizePx = baseFontSizePx,
                    attributionFontSizePx = attributionFontSizePx,
                    columnSpacingPx = columnSpacingPx,
                    lineSpacingPx = lineSpacingPx,
                    attrLineSpacingPx = attrLineSpacingPx,
                    attrTitleAuthorGapPx = attrTitleAuthorGapPx,
                    paddingTopPx = paddingTopPx,
                    paddingRightPx = paddingRightPx,
                    paddingLeftPx = paddingLeftPx,
                    paddingBottomPx = paddingBottomPx,
                    filterPunctuation = true,
                    typeface = activeTypeface,
                )
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = activeTypeface
            }

            for (col in layoutResult.columns) {
                for (glyph in col.glyphs) {
                    textPaint.textSize = glyph.fontSizePx
                    textPaint.color = if (glyph.isAttribution) {
                        attributionColor.toArgb()
                    } else {
                        textColor.toArgb()
                    }

                    if (glyph.rotationDegrees != 0f) {
                        nativeCanvas.save()
                        nativeCanvas.rotate(glyph.rotationDegrees, glyph.x, glyph.y)
                        nativeCanvas.drawText(glyph.char.toString(), glyph.x, glyph.y, textPaint)
                        nativeCanvas.restore()
                    } else {
                        nativeCanvas.drawText(glyph.char.toString(), glyph.x, glyph.y, textPaint)
                    }
                }
            }
        }
    }
}
