package com.davplayer.app

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import java.util.ArrayList

/**
 * 在系统 MediaCodec 之前加入 FFmpeg 视频软解，用于 Hi10P/10bit AVC 等
 * Android 自带 c2.android.avc.decoder 无法处理的格式。
 */
@UnstableApi
class FfmpegRenderersFactory(
    context: Context,
    private val preferFfmpeg: Boolean
) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        AppLog.log(
            "CODEC",
            "ffmpeg available=${FfmpegLibrary.isAvailable()} version=${FfmpegLibrary.getVersion()} prefer=$preferFfmpeg"
        )
        if (preferFfmpeg && FfmpegLibrary.isAvailable()) {
            out.add(
                ExperimentalFfmpegVideoRenderer(
                    allowedVideoJoiningTimeMs,
                    eventHandler,
                    eventListener,
                    DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
                )
            )
        }
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
    }
}
