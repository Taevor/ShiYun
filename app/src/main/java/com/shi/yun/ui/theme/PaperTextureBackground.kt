package com.shi.yun.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.shi.yun.R
import com.shi.yun.common.log.AppLogger

/**
 * 纸张纹理位图全局单例缓存
 *
 * 核心保障：
 * 1. 杜绝在 Compose 重组时重复解码，只在初次装载时解码一次；
 * 2. inScaled = false 确保 1912x1912 原图不被系统 DPI 自动放大缩放；
 * 3. 严格复用同一个 Bitmap 实例，由所有六套纯色主题共享。
 */
object PaperTextureCache {
    @Volatile
    private var cachedBitmap: Bitmap? = null

    fun getTextureBitmap(context: Context, @DrawableRes resId: Int = R.drawable.bg_paper_texture): Bitmap {
        cachedBitmap?.let { if (!it.isRecycled) return it }
        synchronized(this) {
            cachedBitmap?.let { if (!it.isRecycled) return it }
            val startMs = System.currentTimeMillis()
            val options = BitmapFactory.Options().apply {
                inScaled = false // 保证像素 1:1，避免不同 DPI 设备倍率放大
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeResource(context.resources, resId, options)
                ?: throw IllegalStateException("无法解码纸张纹理图片资源: $resId")
            cachedBitmap = bmp
            val duration = System.currentTimeMillis() - startMs
            AppLogger.i("Theme", "纸张纹理原图解码成功: ${bmp.width}x${bmp.height}, 耗时=${duration}ms, 内存=${bmp.byteCount / 1024}KB")
            return bmp
        }
    }
}

/**
 * AGSL 硬件加速线性光着色器源码 (Photoshop Linear Light 原语)
 *
 * Photoshop 数学公式:
 * B: 纯色底色, S: 纸张纹理颜色 (中性灰 0.5)
 * Result = B + alpha * (2.0 * S - 1.0)
 */
private const val LINEAR_LIGHT_AGSL = """
uniform shader baseShader;
uniform shader textureShader;
uniform float alpha;

half4 main(float2 coord) {
    half4 base = baseShader.eval(coord);
    half4 tex = textureShader.eval(coord);
    // Photoshop Linear Light 线性光数学原语
    half3 blended = base.rgb + alpha * (2.0 * tex.rgb - 1.0);
    blended = clamp(blended, 0.0, 1.0);
    return half4(blended, base.a);
}
"""

/**
 * 纯色背景叠加纸张纹理（Linear Light 混合模式）组件
 *
 * @param solidColor 纯色背景基色（如宣纸古籍 #F6E9CF）
 * @param textureResId 纹理图片资源 ID（默认修复版原图 R.drawable.bg_paper_texture）
 * @param textureAlpha 纹理混合透明度，统一规范为 0.15f
 */
@Composable
fun SolidTextureBackground(
    solidColor: Color,
    modifier: Modifier = Modifier,
    @DrawableRes textureResId: Int = R.drawable.bg_paper_texture,
    textureAlpha: Float = ThemeVisualSpecs.TEXTURE_ALPHA_CLASSIC,
) {
    val context = LocalContext.current
    val bitmap = remember(textureResId) {
        PaperTextureCache.getTextureBitmap(context, textureResId)
    }

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+): 原生 AGSL RuntimeShader 硬件级单 Pass 渲染
            val runtimeShader = RuntimeShader(LINEAR_LIGHT_AGSL)
            val baseShader = LinearGradient(
                0f, 0f, 1f, 1f,
                solidColor.toArgb(), solidColor.toArgb(),
                Shader.TileMode.CLAMP,
            )
            val textureShader = BitmapShader(
                bitmap,
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT,
            )
            runtimeShader.setInputShader("baseShader", baseShader)
            runtimeShader.setInputShader("textureShader", textureShader)
            runtimeShader.setFloatUniform("alpha", textureAlpha)

            val paint = Paint().apply {
                shader = runtimeShader
            }
            drawContext.canvas.nativeCanvas.drawRect(0f, 0f, canvasWidth, canvasHeight, paint)
        } else {
            // Android 10-12 (API 29-32): ColorMatrix 等价线性代数硬件级 Pass 渲染
            // 数学推导: base + alpha * (2*tex - 1) = (2*alpha) * tex + (base - alpha)
            val scale = 2f * textureAlpha
            val rOffset = (solidColor.red - textureAlpha) * 255f
            val gOffset = (solidColor.green - textureAlpha) * 255f
            val bOffset = (solidColor.blue - textureAlpha) * 255f

            val colorMatrix = ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, rOffset,
                    0f, scale, 0f, 0f, gOffset,
                    0f, 0f, scale, 0f, bOffset,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            val textureShader = BitmapShader(
                bitmap,
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT,
            )
            val paint = Paint().apply {
                shader = textureShader
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
            drawContext.canvas.nativeCanvas.drawRect(0f, 0f, canvasWidth, canvasHeight, paint)
        }
    }
}
