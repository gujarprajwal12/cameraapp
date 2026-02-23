package com.psg.cameraapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import androidx.annotation.DrawableRes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList

@UnstableApi
object WatermarkHelper {
    fun applyTextWatermark(
        context: Context,
        inputPath: String,
        outputPath: String,
        watermarkText: String,
        anchorX: Float = 0.8f,
        anchorY: Float = -0.8f,
        alpha: Float = 0.75f,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val spannable = SpannableString(watermarkText).apply {
            setSpan(ForegroundColorSpan(Color.WHITE), 0, length, 0)
            setSpan(RelativeSizeSpan(1f), 0, length, 0)
        }

        val overlaySettings = OverlaySettings.Builder()
            .setAlphaScale(alpha)
            .setOverlayFrameAnchor(/* x= */ 1f, /* y= */ 1f)
            .setBackgroundFrameAnchor(/* x= */ anchorX, /* y= */ anchorY)
            .build()

        val textOverlay: TextureOverlay =
            TextOverlay.createStaticTextOverlay(spannable, overlaySettings)
        val overlayEffect = OverlayEffect(
            ImmutableList.of<TextureOverlay>(textOverlay)
        )

        buildAndRunTransformer(
            context = context,
            inputPath = inputPath,
            outputPath = outputPath,
            effects = Effects(/* audioProcessors= */ emptyList(), /* videoEffects= */ listOf(overlayEffect)),
            onSuccess = onSuccess,
            onError = onError
        )
    }


    fun applyBitmapWatermark(
        context: Context,
        inputPath: String,
        outputPath: String,
        @DrawableRes logoResId: Int,
        anchorX: Float = 0.8f,
        anchorY: Float = -0.8f,
        alpha: Float = 0.75f,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val bitmap: Bitmap = BitmapFactory.decodeResource(context.resources, logoResId)

        val overlaySettings = OverlaySettings.Builder()
            .setAlphaScale(alpha)
            .setOverlayFrameAnchor(/* x= */ 1f, /* y= */ 1f)
            .setBackgroundFrameAnchor(/* x= */ anchorX, /* y= */ anchorY)
            .build()

        val bitmapOverlay: TextureOverlay =
            BitmapOverlay.createStaticBitmapOverlay(bitmap, overlaySettings)

        // FIX: same as text — wrap in OverlayEffect before adding to Effects
        val overlayEffect = OverlayEffect(
            ImmutableList.of<TextureOverlay>(bitmapOverlay)
        )

        buildAndRunTransformer(
            context = context,
            inputPath = inputPath,
            outputPath = outputPath,
            effects = Effects(/* audioProcessors= */ emptyList(), /* videoEffects= */ listOf(overlayEffect)),
            onSuccess = onSuccess,
            onError = onError
        )
    }


    private fun buildAndRunTransformer(
        context: Context,
        inputPath: String,
        outputPath: String,
        effects: Effects,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val editedMediaItem = EditedMediaItem.Builder(
            MediaItem.fromUri("file://$inputPath")
        )
            .setEffects(effects)
            .build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {

                override fun onCompleted(
                    composition: Composition,
                    result: ExportResult
                ) {
                    onSuccess()
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    onError(exception)
                }
            })
            .build()

        transformer.start(editedMediaItem, outputPath)
    }
}