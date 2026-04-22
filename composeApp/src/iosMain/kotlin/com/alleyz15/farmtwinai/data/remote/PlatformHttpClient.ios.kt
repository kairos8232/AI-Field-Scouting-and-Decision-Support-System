package com.alleyz15.farmtwinai.data.remote

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun platformHttpClientEngineFactory(): HttpClientEngineFactory<*> = Darwin
