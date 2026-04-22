package com.alleyz15.farmtwinai.data.remote

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun platformHttpClientEngineFactory(): HttpClientEngineFactory<*> = OkHttp
