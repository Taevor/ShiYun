package com.shi.yun.ui.theme

import androidx.compose.runtime.Composable

/**
 * 纸张纹理测试集成桥接 (Release 正式发布环境)
 *
 * 在 Release 构建中：
 * 1. isAvailable 恒为 false，编译期常量；
 * 2. RenderTestScreen 为空实现；
 * 3. R8 会将所有死代码完全消除；
 * 4. 测试页面代码、参数调节控件与测试图片资源 100% 不会打包进正式 APK/AAB。
 */
object TextureTestBridge {
    const val isAvailable: Boolean = false

    @Composable
    fun RenderTestScreen(onDismiss: () -> Unit) {
        // Release 正式发布版为空实现，绝对不包含任何测试界面
    }
}
