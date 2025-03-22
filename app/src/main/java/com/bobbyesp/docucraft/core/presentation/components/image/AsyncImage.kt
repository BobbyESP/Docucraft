package com.bobbyesp.docucraft.core.presentation.components.image

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.request.ImageRequest
import com.bobbyesp.docucraft.core.presentation.components.others.Placeholder
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import com.skydoves.landscapist.coil.CoilImageState
import com.skydoves.landscapist.coil.LocalCoilImageLoader

@Composable
fun AsyncImage(
    modifier: Modifier = Modifier,
    imageModel: Any? = null,
    imageModifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    context: Context = LocalContext.current,
    imageLoader: ImageLoader? = LocalCoilImageLoader.current,
    imageOptions: ImageOptions =
        ImageOptions(contentDescription = null, contentScale = ContentScale.Crop),
    requestListener: (() -> ImageRequest.Listener)? = null,
    onSuccessData: (CoilImageState.Success) -> Unit = { _ -> },
) {
    val imageUrl by remember(imageModel) { mutableStateOf(imageModel) }

    CoilImage(
        modifier = modifier.clip(shape),
        imageModel = { imageUrl },
        imageOptions = imageOptions,
        onImageStateChanged = { state ->
            if (state is CoilImageState.Success) {
                onSuccessData(state)
            }
        },
        requestListener = requestListener,
        loading = {
            Surface(
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier,
            ) {
                CircularProgressIndicator()
            }
        },
        failure = { error ->
            Placeholder(
                modifier = imageModifier.fillMaxSize(),
                icon = Icons.Rounded.QuestionMark,
                colorful = true,
                contentDescription = "Song cover failed to load",
            )
        },
        imageLoader = { imageLoader ?: ImageLoader(context) },
    )
}
