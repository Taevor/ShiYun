package com.shi.yun.data.model

/**
 * 数据源原始提供方枚举
 */
enum class SourceOrigin {
    /** 诗云本地经过审定校对的经典作品库 */
    LOCAL_CANONICAL,

    /** shi-ci.cn (公开中华诗词 API) */
    SHI_CI_CN,

    /** 诗泉 (poetry.palemoky.com 诗词数据接口) */
    POETRY_PALEMOKY,

    /** 用户自定义/导入来源 */
    USER_CUSTOM
}
