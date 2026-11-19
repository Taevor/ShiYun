package com.shi.yun.ui.theme

import androidx.compose.runtime.Composable

/**
 * 纸张纹理测试集成桥接 (Debug 内部开发环境)
 *
 * 仅在 Debug 编译变体中提供测试页面入口与渲染载体。
 */
object TextureTestBridge {
    const val isAvailable: Boolean = true

    @Composable
    fun RenderTestScreen(onDismiss: () -> Unit) {
        TextureTestScreen(onDismiss = onDismiss)
    }
}
