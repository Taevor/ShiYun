package com.shi.yun.common.log

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 日志级别枚举
 */
enum class LogLevel(val priority: Int, val label: String) {
    DEBUG(Log.DEBUG, "DEBUG"),
    INFO(Log.INFO, "INFO"),
    WARN(Log.WARN, "WARN"),
    ERROR(Log.ERROR, "ERROR")
}

/**
 * 日志导出结果数据模型
 */
data class LogExportResult(
    val success: Boolean,
    val fileName: String = "",
    val relativePath: String = "",
    val totalBytes: Long = 0L,
    val uri: Uri? = null,
    val errorMessage: String? = null,
)

/**
 * 本地日志统计摘要
 */
data class LogStats(
    val fileCount: Int,
    val totalSizeBytes: Long,
    val directoryPath: String,
    val lastModifiedTimestamp: Long,
)

/**
 * 诗云统一日志基础设施 (AppLogger)
 *
 * 核心架构规范：
 * 1. 统一入口：全应用唯一日志写入入口，杜绝各页面随意创建日志文件；
 * 2. 毫秒级时间戳与级别分类：时间格式 yyyy-MM-dd HH:mm:ss.SSS，含模块标签与异常堆栈；
 * 3. 异步非阻塞与线程安全：单线程调度器顺序追加写入，绝不阻塞主线程，完全隔离写入并发；
 * 4. 容错与无损保护：所有磁盘 I/O 均受严格异常保护，日志系统自身出错绝不导致 App 崩溃；
 * 5. 文件轮换：单个日志文件上限 2 MB，最多保留 5 个轮换备份，超出自动归档与清理；
 * 6. 现代公共目录导出：基于 Android MediaStore API 导出至 Download/诗云日志，无需任何宽泛存储权限；
 * 7. 隐私合规：严禁记录完整用户输入或密码，仅记录长度、耗时与脱敏统计指标。
 */
object AppLogger {

    private const val LOG_SUBDIR = "logs"
    private const val ACTIVE_LOG_FILE = "app.log"
    private const val MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L // 单文件 2 MB
    private const val MAX_BACKUP_FILES = 5 // 最多保留 5 个备份文件

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
    private val exportFileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)

    @Volatile
    private var logDir: File? = null

    @Volatile
    private var isInitialized = false

    // 全局手势关联 ID 计数器，确保同一手势事件（按下、释放、取消、识别）能跨组件串联
    private val gestureSequenceCounter = java.util.concurrent.atomic.AtomicLong(1000L)

    // 单线程守护线程池，用于保证日志追加写入绝对线程安全且非阻塞
    private val writerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ShiYun-AppLogger-Worker").apply {
            isDaemon = true
        }
    }

    /**
     * 初始化日志系统（可在 Application 或 MainActivity.onCreate 中调用）
     */
    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                val baseDir = context.applicationContext.filesDir.resolve(LOG_SUBDIR)
                if (!baseDir.exists()) {
                    baseDir.mkdirs()
                }
                logDir = baseDir
                isInitialized = true

                // 注册全局未捕获异常处理器
                installCrashHandler()

                i("AppLogger", "诗云运行日志系统初始化就绪: 存储目录=${baseDir.absolutePath}")
                i("AppLogger", "运行环境: Android SDK=${Build.VERSION.SDK_INT}, 机型=${Build.MANUFACTURER} ${Build.MODEL}, 架构=${Build.SUPPORTED_ABIS.joinToString()}")
            } catch (e: Exception) {
                // 初始化异常绝不抛出崩溃
                try {
                    Log.e("AppLogger", "初始化日志目录失败: ${e.message}", e)
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * 仅供测试使用的初始化方法，允许指定自定义目录并重置状态
     */
    fun initForTesting(testDir: File) {
        synchronized(this) {
            if (!testDir.exists()) {
                testDir.mkdirs()
            }
            logDir = testDir
            isInitialized = true
        }
    }

    /**
     * 等待所有后台写入任务完成（供测试或关键生命周期节点落盘）
     */
    fun flush(timeoutMs: Long = 2000L) {
        val future = writerExecutor.submit { }
        try {
            future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        }
    }

    /**
     * 兼容旧测试调用的刷盘方法
     */
    fun flushForTesting(timeoutMs: Long = 3000L) {
        flush(timeoutMs)
    }

    /**
     * 注册全局未捕获异常崩溃日志兜底（进程终止前同步刷盘）
     */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                e("CrashHandler", "捕获到未捕获异常，线程=[${thread.name}]: ${throwable.message}", throwable)
                // 确保崩溃日志在进程终结前落盘
                flush(2000L)
            } catch (_: Exception) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message, null)
    }

    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    /**
     * 生成唯一手势关联 ID (例如 G_1001)
     */
    fun generateGestureId(): String = "G_${gestureSequenceCounter.incrementAndGet()}"

    /**
     * 手势诊断日志：记录手势开始、释放、取消、识别与消费
     */
    fun gesture(
        gestureId: String?,
        page: String,
        target: String,
        stage: String,
        detail: String = "",
    ) {
        val idStr = gestureId ?: "-"
        val msg = "手势ID=[$idStr] 页面=[$page] 目标=[$target] 阶段=[$stage]${if (detail.isNotBlank()) " 详情=$detail" else ""}"
        d("Gesture", msg)
    }

    /**
     * 用户重要交互事件日志：记录点击、输入、切换及其结果与耗时
     */
    fun interaction(
        page: String,
        widget: String,
        event: String,
        result: String = "SUCCESS",
        detail: String? = null,
        durationMs: Long? = null,
        throwable: Throwable? = null,
    ) {
        val msg = buildString {
            append("页面=[$page] 控件=[$widget] 事件=[$event] 结果=[$result]")
            if (durationMs != null) append(" 耗时=${durationMs}ms")
            if (!detail.isNullOrBlank()) append(" 详情=$detail")
        }
        if (result == "FAILED" || throwable != null) {
            w("Interaction", msg, throwable)
        } else {
            i("Interaction", msg)
        }
    }

    /**
     * 关键状态变动日志（仅在状态实质变化时记录，杜绝页面重组刷屏）
     */
    fun stateChange(
        page: String,
        property: String,
        to: Any?,
        reason: String? = null,
    ) {
        val msg = buildString {
            append("页面=[$page] 属性=[$property] 现值=[$to]")
            if (!reason.isNullOrBlank()) append(" 原因=$reason")
        }
        i("StateChange", msg)
    }

    /**
     * 数据库与数据操作日志：记录耗时、结果与脱敏信息
     */
    fun dataOp(
        operation: String,
        target: String,
        result: String = "SUCCESS",
        durationMs: Long? = null,
        detail: String? = null,
        throwable: Throwable? = null,
    ) {
        val msg = buildString {
            append("操作=[$operation] 目标=[$target] 结果=[$result]")
            if (durationMs != null) append(" 耗时=${durationMs}ms")
            if (!detail.isNullOrBlank()) append(" 详情=$detail")
        }
        if (result == "FAILED" || throwable != null) {
            e("Database", msg, throwable)
        } else {
            d("Database", msg)
        }
    }

    /**
     * 页面性能监控日志：耗时统计与超时报警
     */
    fun perf(
        page: String,
        action: String,
        durationMs: Long,
        thresholdMs: Long = 300L,
        detail: String? = null,
    ) {
        if (durationMs > thresholdMs) {
            w("Performance", "性能警告: 页面=[$page] 动作=[$action] 耗时=${durationMs}ms (超过阈值${thresholdMs}ms)${if (detail != null) " 详情=$detail" else ""}")
        } else {
            d("Performance", "性能指标: 页面=[$page] 动作=[$action] 耗时=${durationMs}ms${if (detail != null) " 详情=$detail" else ""}")
        }
    }

    /**
     * 核心格式化与写入派发逻辑
     */
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        // 1. 同步镜像输出至系统 Logcat，方便开发期调试
        try {
            when (level) {
                LogLevel.DEBUG -> Log.d(tag, message, throwable)
                LogLevel.INFO -> Log.i(tag, message, throwable)
                LogLevel.WARN -> Log.w(tag, message, throwable)
                LogLevel.ERROR -> Log.e(tag, message, throwable)
            }
        } catch (_: Throwable) {
            // 在未 Mock 的纯 JVM 单元测试环境中忽略 Logcat 抛错
        }

        val targetDir = logDir ?: return
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }

        // 2. 格式化日志内容
        val logLine = buildString {
            append(timestamp)
            append(" [")
            append(level.label)
            append("] [")
            append(tag)
            append("] ")
            append(message)
            append("\n")
            if (throwable != null) {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                append(sw.toString())
                if (!sw.toString().endsWith("\n")) {
                    append("\n")
                }
            }
        }

        // 3. 异步提交至后台线程写入私有文件
        try {
            writerExecutor.execute {
                writeLogLineToFile(targetDir, logLine)
            }
        } catch (_: Exception) {
            // 调度器关闭或队列满时静默忽略，绝不干扰业务
        }
    }

    /**
     * 文件追加写入与大小轮换
     */
    @Synchronized
    internal fun writeLogLineToFile(dir: File, content: String) {
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val activeFile = dir.resolve(ACTIVE_LOG_FILE)

            // 检查当前文件大小，达到 2 MB 时触发轮换
            if (activeFile.exists() && activeFile.length() >= MAX_FILE_SIZE_BYTES) {
                rotateLogFiles(dir)
            }

            // 追加写入
            FileOutputStream(activeFile, true).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
            }
        } catch (e: Exception) {
            try {
                Log.e("AppLogger", "写入日志文件异常: ${e.message}")
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * 日志轮换：
     * app.log -> app_1.log -> app_2.log -> ... -> app_5.log (超出即删除)
     */
    internal fun rotateLogFiles(dir: File) {
        try {
            val oldestFile = dir.resolve("app_$MAX_BACKUP_FILES.log")
            if (oldestFile.exists()) {
                oldestFile.delete()
            }

            for (i in (MAX_BACKUP_FILES - 1) downTo 1) {
                val src = dir.resolve("app_$i.log")
                val dst = dir.resolve("app_${i + 1}.log")
                if (src.exists()) {
                    src.renameTo(dst)
                }
            }

            val activeFile = dir.resolve(ACTIVE_LOG_FILE)
            val firstBackup = dir.resolve("app_1.log")
            if (activeFile.exists()) {
                activeFile.renameTo(firstBackup)
            }
        } catch (e: Exception) {
            try {
                Log.e("AppLogger", "日志文件轮换失败: ${e.message}")
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * 获取当前本地日志统计指标
     */
    fun getLogStats(): LogStats {
        val dir = logDir ?: return LogStats(0, 0L, "未初始化", 0L)
        if (!dir.exists()) {
            return LogStats(0, 0L, dir.absolutePath, 0L)
        }
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".log") } ?: emptyArray()
        val totalSize = files.sumOf { it.length() }
        val latestModified = files.maxOfOrNull { it.lastModified() } ?: 0L
        return LogStats(
            fileCount = files.size,
            totalSizeBytes = totalSize,
            directoryPath = dir.absolutePath,
            lastModifiedTimestamp = latestModified,
        )
    }

    /**
     * 清理所有本地内部存储日志文件
     */
    fun clearLogs(): Boolean {
        val dir = logDir ?: return false
        return try {
            val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".log") } ?: emptyArray()
            var allDeleted = true
            for (f in files) {
                if (!f.delete()) {
                    allDeleted = false
                }
            }
            i("AppLogger", "已执行本地运行日志清空操作: 清除文件数=${files.size}, 成功=$allDeleted")
            allDeleted
        } catch (e: Exception) {
            e("AppLogger", "清空本地日志失败: ${e.message}", e)
            false
        }
    }

    /**
     * 导出所有保留日志至公共 Download/诗云日志 目录
     *
     * 采用 Android 原生 MediaStore 规范，兼容 Android 10+ (API 29+)，零存储权限请求。
     */
    fun exportLogsToDownloads(context: Context): LogExportResult {
        val dir = logDir ?: return LogExportResult(
            success = false,
            errorMessage = "日志系统尚未初始化",
        )

        // 导出前强制等待在途异步写入落盘
        flush(1000L)

        val timestampStr = synchronized(exportFileNameFormat) { exportFileNameFormat.format(Date()) }
        val exportFileName = "shiyun_log_$timestampStr.txt"
        val relativeSubDir = "${Environment.DIRECTORY_DOWNLOADS}/诗云日志"

        return try {
            // 获取应用真实版本信息
            val (verName, verCode) = try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode.toLong()
                }
                pInfo.versionName to code
            } catch (_: Exception) {
                "未知" to -1L
            }

            // 按历史时序收集所有日志文件（app_5.log -> ... -> app_1.log -> app.log）
            val logFilesToExport = mutableListOf<File>()
            for (i in MAX_BACKUP_FILES downTo 1) {
                val f = dir.resolve("app_$i.log")
                if (f.exists() && f.length() > 0) {
                    logFilesToExport.add(f)
                }
            }
            val active = dir.resolve(ACTIVE_LOG_FILE)
            if (active.exists() && active.length() > 0) {
                logFilesToExport.add(active)
            }

            val totalLogBytes = logFilesToExport.sumOf { it.length() }

            val resolver = context.applicationContext.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, exportFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubDir)
            }

            val targetCollectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val targetUri = resolver.insert(targetCollectionUri, contentValues)
                ?: return LogExportResult(
                    success = false,
                    errorMessage = "无法在公共 Download 目录创建导出文件",
                )

            var writtenBytes = 0L
            resolver.openOutputStream(targetUri)?.use { outputStream ->
                // 1. 写入结构化导出头部诊断信息
                val header = buildString {
                    append("================================================================\n")
                    append("诗云应用运行日志导出文件 (ShiYun Diagnostic Report)\n")
                    append("================================================================\n")
                    append("导出时间: ").append(synchronized(dateFormat) { dateFormat.format(Date()) }).append("\n")
                    append("设备厂商: ").append(Build.MANUFACTURER).append("\n")
                    append("设备型号: ").append(Build.MODEL).append("\n")
                    append("系统版本: Android SDK ").append(Build.VERSION.SDK_INT).append(" (Android ").append(Build.VERSION.RELEASE).append(")\n")
                    append("应用包名: ").append(context.packageName).append("\n")
                    append("应用版本: ").append(verName).append(" (versionCode: ").append(verCode).append(")\n")
                    append("日志文件数: ").append(logFilesToExport.size).append(" 个 (总大小: ").append(totalLogBytes / 1024).append(" KB)\n\n")

                    append("--- [归档文件列表清单] ---\n")
                    logFilesToExport.forEachIndexed { index, file ->
                        val fileType = if (file.name == ACTIVE_LOG_FILE) "当前活跃日志" else "历史轮换归档"
                        val lastMod = synchronized(dateFormat) { dateFormat.format(Date(file.lastModified())) }
                        append("${index + 1}. ${file.name} ($fileType) - ${file.length() / 1024} KB, 最后更新: $lastMod\n")
                    }
                    append("\n")

                    append("--- [日志分类与关键词索引说明] ---\n")
                    append("* [Interaction]   : 用户交互事件 (控件点击、页面跳转、操作耗时与结果)\n")
                    append("* [Gesture]       : 手势诊断追踪 (手势ID关联、DOWN/UP/CANCEL/DOUBLE_TAP/滚动)\n")
                    append("* [StateChange]   : 核心状态变化 (收藏状态、排版设置、主题切换、面板状态)\n")
                    append("* [Database]      : 数据层操作 (Room DAO 读写耗时、收藏持久化、作品查询)\n")
                    append("* [Performance]   : 性能监控指标 (操作耗时统计、超过阈值耗时警告)\n")
                    append("* [CrashHandler]  : 未捕获异常与致命崩溃堆栈兜底记录\n")
                    append("* [Navigation]    : 路由导航流转 (底部Tab切换、详情页进退、选本切篇)\n")
                    append("================================================================\n\n")
                }
                val headerBytes = header.toByteArray(Charsets.UTF_8)
                outputStream.write(headerBytes)
                writtenBytes += headerBytes.size

                // 2. 按顺序合并写入历史日志文件
                for (file in logFilesToExport) {
                    val fileHeader = "\n--- [归档日志: ${file.name}] ---\n".toByteArray(Charsets.UTF_8)
                    outputStream.write(fileHeader)
                    writtenBytes += fileHeader.size

                    file.inputStream().use { inputStream ->
                        val buffer = ByteArray(8192)
                        var readLen: Int
                        while (inputStream.read(buffer).also { readLen = it } != -1) {
                            outputStream.write(buffer, 0, readLen)
                            writtenBytes += readLen
                        }
                    }
                }
                outputStream.flush()
            }

            i("AppLogger", "日志成功导出至公共目录: 文件名=$exportFileName, 路径=$relativeSubDir, 大小=$writtenBytes 字节")

            LogExportResult(
                success = true,
                fileName = exportFileName,
                relativePath = relativeSubDir,
                totalBytes = writtenBytes,
                uri = targetUri,
            )
        } catch (e: Exception) {
            e("AppLogger", "导出日志到 Downloads 异常: ${e.message}", e)
            LogExportResult(
                success = false,
                errorMessage = e.message ?: "导出日志失败",
            )
        }
    }
}
