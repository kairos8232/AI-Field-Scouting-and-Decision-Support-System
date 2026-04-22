package com.alleyz15.farmtwinai.data.analysis

import platform.Foundation.NSBundle

internal actual fun platformFieldInsightsBaseUrl(): String {
    return NSBundle.mainBundle.objectForInfoDictionaryKey("FieldInsightsBaseUrl")?.toString().orEmpty()
}
