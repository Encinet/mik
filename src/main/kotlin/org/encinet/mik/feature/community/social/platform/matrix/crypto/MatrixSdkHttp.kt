package org.encinet.mik.feature.community.social.platform.matrix.crypto

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.statement.HttpResponseContainer
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/** Bounds SDK JSON bodies even when the server uses chunked transfer encoding. */
internal class MatrixSdkHttp private constructor(private val maximumBytes: Int) {
    class Configuration {
        var maximumBytes: Int = 2_097_152
    }

    companion object : HttpClientPlugin<Configuration, MatrixSdkHttp> {
        override val key = AttributeKey<MatrixSdkHttp>("mik-matrix-response-limit")

        override fun prepare(block: Configuration.() -> Unit): MatrixSdkHttp =
            MatrixSdkHttp(Configuration().apply(block).maximumBytes)

        override fun install(plugin: MatrixSdkHttp, scope: HttpClient) {
            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                val body = subject.response as? ByteReadChannel
                if (body != null) {
                    val bytes = body.readRemaining(plugin.maximumBytes.toLong() + 1).readByteArray()
                    check(bytes.size <= plugin.maximumBytes) { "Matrix crypto response exceeded size limit" }
                    proceedWith(HttpResponseContainer(subject.expectedType, ByteReadChannel(bytes)))
                }
            }
        }
    }
}
