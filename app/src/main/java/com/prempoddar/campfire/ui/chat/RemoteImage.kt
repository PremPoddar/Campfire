package com.prempoddar.campfire.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.prempoddar.campfire.AppGraph
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pictures from this Campfire's own server, scaled to the screen and kept in memory a while. */
private object ImageCache : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

@Composable
fun RemoteImage(url: String, description: String?, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState(ImageCache.get(url), url) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = AppGraph.chat.http.get(url).readRawBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()?.also { ImageCache.put(url, it) }
        }
    }
    val b = bitmap
    if (b != null) Image(b.asImageBitmap(), description, modifier, contentScale = contentScale)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
}
