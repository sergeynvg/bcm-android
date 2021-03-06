package com.bcm.messenger.adhoc.component

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.attachments.DatabaseAttachment
import com.bcm.messenger.common.audio.AudioSlidePlayer
import com.bcm.messenger.common.audio.ChatsAudioPlayer
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.mms.AudioSlide
import com.bcm.messenger.common.mms.Slide
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.DateUtils
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.adhoc_audio_view.view.*
import java.lang.ref.WeakReference
import kotlin.math.min

/**
 *
 */
class AdHocAudioView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr), AudioSlidePlayer.Listener {

    companion object {

        private const val TAG = "AdHocAudioView"
        private const val AUDIO_BACKWARD = 5
    }

    private var slide: AudioSlide? = null
    private var playingPosition: Long = 0 //unit second
    private var backwardsCounter = 0 //handle audio back cause error

    private var mAdHocMessage: AdHocMessageDetail? = null

    private var mCurrentControlView: View? = null
    private var accountContext: AccountContext? = null

    /**
     *
     * @return
     */
    private val progress: Double
        get() = if (audio_progress.progress <= 0 || audio_progress.max <= 0) {
            0.0
        } else {
            audio_progress.progress.toDouble() / audio_progress.max.toDouble()
        }

    init {
        View.inflate(context, R.layout.adhoc_audio_view, this)

        audio_play.setOnClickListener { v ->
            doPlayAction(true)
        }
        audio_pause.setOnClickListener { v ->
            doPlayAction(false)
        }

        setOnClickListener { v ->
            if (audio_play.isClickable) {
                if (audio_play.visibility == View.VISIBLE) {
                    doPlayAction(true)
                } else {
                    doPlayAction(false)
                }
            }
        }
    }

    fun setAudio(accountContext: AccountContext, messageRecord: AdHocMessageDetail) {
        mAdHocMessage = messageRecord
        this.accountContext = accountContext
        val content = messageRecord.getMessageBody()?.content as AmeGroupMessage.AudioContent
        val slide = AudioSlide(context, messageRecord.toAttachmentUri(), content.size, content.duration, false)
        this.slide = slide
        displayControl(audio_play)
        if (messageRecord.toAttachmentUri() == null) {
            ALog.w(TAG, "setAudio fail, uri is null")
            audio_play?.isClickable = false
            audio_pause?.isClickable = false
        } else {
            audio_play?.isClickable = true
            audio_pause?.isClickable = true
        }

        updateMediaDuration(accountContext, slide, slide.duration)
        checkDuration(accountContext, slide)

        if (ChatsAudioPlayer.instance.isPlaying(slide)) {
            togglePlayToPause()
            ChatsAudioPlayer.instance.setEventListener(this)
        } else {
            audio_progress.progress  = 0
        }
    }

    private fun checkDuration(accountContext: AccountContext, slide: AudioSlide) {
        if (slide.duration <= 0) {
            val weakThis = WeakReference(this)
            AmeDispatcher.singleScheduler.scheduleDirect {
                val duration = ChatsAudioPlayer.getDuration(null, slide)
                AmeDispatcher.mainThread.dispatch {
                    if (slide.uri == weakThis.get()?.slide?.uri) {
                        updateMediaDuration(accountContext, slide, duration)
                    }
                }
            }
        }
    }

    fun setProgressDrawableResource(resourceId: Int) {
        setProgressDrawable(context.getDrawable(resourceId) ?: return)
    }


    fun setProgressDrawable(progressDrawable: Drawable) {
        audio_progress.progressDrawable = progressDrawable
    }


    fun setAudioAppearance(playRes: Int, pauseRes: Int, decorationColor: Int, textColor: Int) {
        audio_play.setImageResource(playRes)
        audio_pause.setImageResource(pauseRes)
        audio_decoration.setBackgroundColor(decorationColor)
        audio_timestamp.setTextColor(textColor)
    }

    fun cleanup() {
        if (ChatsAudioPlayer.instance.isPlaying(slide?:return)) {
            ChatsAudioPlayer.instance.setEventListener(null)
        }
    }

    override fun onPrepare(player: AudioSlidePlayer?, totalMills: Long) {
        if (this.slide?.uri == player?.audioSlide?.uri) {
            val accountContext = this.accountContext ?: return
            updateMediaDuration(accountContext, player?.audioSlide, totalMills)
        }
    }

    override fun onStart(player: AudioSlidePlayer?, totalMills: Long) {
        val accountContext = this.accountContext ?: return
        if (this.slide?.uri == player?.audioSlide?.uri) {
            updateMediaDuration(accountContext, player?.audioSlide, totalMills)
            if (audio_pause.visibility != View.VISIBLE) {
                togglePlayToPause()
            }
        }
    }

    override fun onStop(player: AudioSlidePlayer?) {
        ALog.i("AudioView", "onStop" + audio_progress.progress)
        if (this.slide?.uri == player?.audioSlide?.uri) {
            onStop(slide)
        }
    }

    private fun onStop(slide: AudioSlide?) {
        togglePauseToPlay()
        backwardsCounter = AUDIO_BACKWARD

        slide?:return
        if (!ChatsAudioPlayer.instance.isPlaying(slide)) {
            onProgress(slide, 0.0, 0)
        } else if(slide.duration - slide.duration *progress < 1000) {
            onProgress(slide, 0.0, 0)
        }
    }

    override fun onProgress(player: AudioSlidePlayer?, progress: Double, millis: Long) {
        ALog.d(TAG, "onProgress progress: $progress, mills: $millis")
        onProgress(player?.audioSlide, progress, millis)
    }

    private fun onProgress(audioSlide: AudioSlide?, progress: Double, millis: Long){
        ALog.d(TAG, "onProgress progress: $progress, mills: $millis")
        if (this.slide?.uri != audioSlide?.uri) {
            return
        }
        val position = millis / 1000L
        if (0L == millis) {
            audio_timestamp.text = DateUtils.convertMinuteAndSecond(audioSlide?.duration
                    ?: 0)
        } else if (position != playingPosition) {
            playingPosition = position
            audio_timestamp.text = DateUtils.convertMinuteAndSecond((audioSlide?.duration
                    ?: 0) - millis)
        }

        val newProgress = Math.floor(progress * audio_progress.max).toInt()
        if (newProgress > audio_progress.progress || backwardsCounter >= AUDIO_BACKWARD) {
            backwardsCounter = 0
            audio_progress.progress = newProgress

        } else {
            backwardsCounter++
        }
    }

    private fun togglePlayToPause() {
        post { displayControl(audio_pause) }
    }

    private fun togglePauseToPlay() {
        post { displayControl(audio_play) }
    }


    private fun updateMediaDuration(accountContext: AccountContext, audioSlide: AudioSlide?, duration: Long) {

        ALog.d(TAG, "updateMediaDuration: $duration")
        if (audioSlide?.duration != duration) {
            if (audioSlide != null) {
                audioSlide.duration = duration
                Observable.create<Boolean> {
                    if (audioSlide.asAttachment() is DatabaseAttachment) {
                        val databaseAttachment = audioSlide.asAttachment() as DatabaseAttachment
                        databaseAttachment.duration = duration
                        Repository.getAttachmentRepo(accountContext)?.updateDuration(databaseAttachment.attachmentId.rowId, databaseAttachment.attachmentId.uniqueId, duration)
                    } else {
                        ALog.w(TAG, "current audio slide type is not DatabaseAttachment")
                    }
                    it.onNext(true)
                    it.onComplete()

                }.subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                        }, {
                            ALog.e(TAG, "updateMediaDuration error", it)
                        })
            }
        }

        if (duration <= 0) {
            audio_timestamp.visibility = View.GONE
        } else {
            audio_timestamp.visibility = View.VISIBLE
            audio_timestamp.text = DateUtils.convertMinuteAndSecond(duration)
        }
        audio_decoration.layoutParams = audio_decoration.layoutParams.apply {
            width = min(3.dp2Px() * (duration / 1000).toInt(), 150.dp2Px())
            if (width == 0) width = 5.dp2Px()
        }
    }

    /**
     * play
     *
     * @param toPlay
     */
    private fun doPlayAction(toPlay: Boolean) {
        PermissionUtil.checkStorage(context) { aBoolean ->
            if (aBoolean) {
                realDoPlayAction(toPlay)
            }
        }
    }

    private fun realDoPlayAction(toPlay: Boolean) {
        val slide = this.slide?:return
        try {
            if (toPlay) {
                togglePlayToPause()
                if (ChatsAudioPlayer.instance.isPlaying(slide)) {
                    ChatsAudioPlayer.instance.resume(progress)
                } else {
                    ChatsAudioPlayer.instance.play(null, slide, this)
                }
            } else {
                ChatsAudioPlayer.instance.stop()
                onStop(slide)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "realDoPlay fail", ex)
        }

    }

    /**
     * display control
     * @param target
     */
    private fun displayControl(target: View?) {

        if (mCurrentControlView != null) {
            if (mCurrentControlView === target) {
                return
            }
            mCurrentControlView?.visibility = View.GONE
            mCurrentControlView = null
        }

        if (target != null) {
            target.visibility = View.VISIBLE
        }
        mCurrentControlView = target

    }


}
