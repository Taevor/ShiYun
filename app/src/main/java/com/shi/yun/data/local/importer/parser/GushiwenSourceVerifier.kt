package com.shi.yun.data.local.importer.parser

import java.net.URI

/**
 * 古诗文网来源凭证严格校验器 (GushiwenSourceVerifier)
 *
 * 杜绝任何基于简单子串模糊匹配 (如 contains("gushiwen")) 的弱校验：
 * 1. 严格解析 URL 协议与主机域名，仅认可已确认为古诗文网官方有效域名 (gushiwen.cn, gushiwen.org 及其官方子域)；
 * 2. 严格核验 URL 路径是否指向具体作品详情页或正规古籍页面 (如 /shiwenv_*.aspx, /guwen/ 等)；
 * 3. 拦截任何将 gushiwen 作为非官方二级域名、博客参数、外链引用的无关或伪造链接；
 * 4. 无法验证或格式损坏的链接绝不标记为官方凭据。
 */
object GushiwenSourceVerifier {

    private val OFFICIAL_BASE_DOMAINS = setOf("gushiwen.cn", "gushiwen.org")

    /**
     * 校验传入的 URL 是否为真实、明确、有效的古诗文网作品来源链接
     */
    fun isValidGushiwenWorkUrl(urlStr: String?): Boolean {
        if (urlStr.isNullOrBlank()) return false

        val uri = try {
            val trimmed = urlStr.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                return false
            }
            URI(trimmed)
        } catch (e: Exception) {
            return false
        }

        val host = uri.host?.lowercase() ?: return false

        // 严格校验主机名必须为官方顶级/二级域名或其子域
        val isOfficialHost = OFFICIAL_BASE_DOMAINS.any { baseDomain ->
            host == baseDomain || host.endsWith(".$baseDomain")
        }
        if (!isOfficialHost) return false

        // 严格校验路径是否指向有效作品页面
        val path = uri.path ?: return false
        val lowerPath = path.lowercase()

        return lowerPath.startsWith("/shiwenv_") ||
               lowerPath.startsWith("/shiwenv.aspx") ||
               lowerPath.startsWith("/guwen/") ||
               lowerPath.startsWith("/poem/") ||
               lowerPath.startsWith("/author/")
    }
}
