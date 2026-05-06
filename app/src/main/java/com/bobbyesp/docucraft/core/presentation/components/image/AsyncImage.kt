package com.bobbyesp.docucraft.core.presentation.components.image

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import com.skydoves.landscapist.coil.CoilImageState
import com.skydoves.landscapist.coil.LocalCoilImageLoader
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin

@Composable
fun AsyncImage(
    modifier: Modifier = Modifier,
    imageModel: Any? = null,
    shape: Shape = MaterialTheme.shapes.small,
    context: Context = LocalContext.current,
    imageLoader: ImageLoader? = LocalCoilImageLoader.current,
    imageOptions: ImageOptions =
        ImageOptions(contentDescription = null, contentScale = ContentScale.Crop),
    requestListener: (() -> ImageRequest.Listener)? = null,
    onSuccessData: (CoilImageState.Success) -> Unit = { _ -> },
    loading: @Composable (BoxScope.(CoilImageState.Loading) -> Unit)? = null,
    success: @Composable (BoxScope.(CoilImageState.Success, Painter) -> Unit)? = null,
    failure: @Composable (BoxScope.(CoilImageState.Failure) -> Unit)? = null,
) {
    val imageUrl by remember(imageModel) { mutableStateOf(imageModel) }

    CoilImage(
        modifier = modifier.clip(shape),
        imageModel = { imageUrl },
        imageOptions = imageOptions,
        component = rememberImageComponent {
            +CrossfadePlugin(300)
            +ShimmerPlugin(
                Shimmer.Resonate(
                    baseColor = MaterialTheme.colorScheme.surface,
                    highlightColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        onImageStateChanged = { state ->
            if (state is CoilImageState.Success) {
                onSuccessData(state)
            }
        },
        requestListener = requestListener,
        loading = loading,
        success = success,
        failure = failure,
        imageLoader = { imageLoader ?: ImageLoader(context) },
    )
}
