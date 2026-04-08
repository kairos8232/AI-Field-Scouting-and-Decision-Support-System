package com.alleyz15.farmtwinai.data.remote

import io.ktor.client.engine.HttpClientEngineFactory

expect fun platformHttpClientEngineFactory(): HttpClientEngineFactory<*>
