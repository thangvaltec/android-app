/*
* GUISample.kt
*
*	All Rights Reserved, Copyright(c) FUJITSU FRONTECH LIMITED 2025
*/

package com.fujitsu.frontech.palmsecure_gui_sample

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.fujitsu.frontech.palmsecure.util.PalmSecureConstant
import com.fujitsu.frontech.palmsecure_sample.data.PsThreadResult
import com.fujitsu.frontech.palmsecure_sample.service.PsService
import com.fujitsu.frontech.palmsecure_sample.xml.PsFileAccessorIni
import kotlin.math.cos
import kotlin.math.sin


interface GUISampleListener {
    fun guiSampleNotifyProgressMessage(message: String)
    fun guiSampleNotifyGuidance(message: String)
    fun guiSampleNotifySilhouette(bitmap: Bitmap)
    fun guiSampleNotifyPosture(posture: Posture)
    fun guiSampleNotifyResult(result: Result)
    fun guiSampleNotifyOffset(offset: Offset)
    fun guiSampleNotifyCount(count: Int)
}

class Constants {
    companion object {
        const val IMAGE_WIDTH: Float = 640f
        const val IMAGE_HEIGHT: Float = 640f
        const val HAND_IMAGE_WIDTH: Float = 640f
        const val HAND_IMAGE_HEIGHT: Float = 480f
        const val CIRCLE_RAD: Float = 265f
        const val LEVEL_RAD: Float = 20f
        private const val DIST_LIMIT: Float = 200f - 50f
        const val DIST_OFFSET_VERIFY: Float = 70f - 50f
        private const val DIST_CIRCLE_MIN: Float = 20f
        private const val POSTURE_DEG_MAX: Float = 90f
        const val DIST_BORDER: Float = 150f - 50f
        const val DIST_MIN_ENROLL: Float = 40f - 50f
        const val DIST_MIN_VERIFY: Float = 35f - 50f
        const val IMAGE_CENTER_X: Float = IMAGE_WIDTH / 2
        const val IMAGE_CENTER_Y: Float = IMAGE_HEIGHT / 2
        const val CIRCLE_LINE: Float = IMAGE_CENTER_X - CIRCLE_RAD
        const val DIST_ADJUST: Float = (CIRCLE_RAD - DIST_CIRCLE_MIN) / DIST_LIMIT
        const val POSTURE_ADJUST: Float = CIRCLE_RAD / POSTURE_DEG_MAX
        const val HAND_IMAGE_TOP: Float = (IMAGE_HEIGHT - HAND_IMAGE_HEIGHT) / 2
    }
}

enum class Offset {
    ENROLL,
    VERIFY
}

enum class Status {
    NO_HANDS,
    NONE,
    ONLY_Z,
    ONLY_XYZ,
    ONLY_XYZ_AND_YAW,
    ALL,
    AWAY_HANDS
}

enum class Result {
    SUCCESSFUL,
    FAILED,
    CANCELED,
    ERROR
}

data class Posture (
    var x: Float,
    var y: Float,
    var z: Float,
    var pitch: Float,
    var roll: Float,
    var yaw: Float,
    var status: Status
)

@SuppressLint("SetTextI18n")
class GUISample(parent: MainActivity) : View.OnClickListener, GUISampleListener, AutoCloseable {
    private val activity = parent
    private val dp = activity.resources.displayMetrics.density
    private val soundPool = SoundPool.Builder().setAudioAttributes(AudioAttributes
        .Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(
            AudioAttributes.CONTENT_TYPE_SPEECH).build()).setMaxStreams(2).build()
    private val okSound = soundPool.load(activity, R.raw.ok, 1)
    private val ngSound = soundPool.load(activity, R.raw.ng, 1)
    private val enrollButton = activity.findViewById<Button>(R.id.enrollButton)
    private val verifyButton = activity.findViewById<Button>(R.id.verifyButton)
    private val cancelButton = activity.findViewById<Button>(R.id.cancelButton)
    private val endButton = activity.findViewById<Button>(R.id.endButton)
    private val progressMessage = activity.findViewById<TextView>(R.id.progressMessage)
    private val guideMessage = activity.findViewById<TextView>(R.id.guideMessage)
    private val imageView = activity.findViewById<ImageView>(R.id.imageView)
    private val msgView = activity.findViewById<ImageView>(R.id.messageView)
    private val okImg = BitmapFactory.decodeResource(activity.getResources(), R.drawable.ok)
    private val ngImg = BitmapFactory.decodeResource(activity.getResources(), R.drawable.ng)
    private val image = Bitmap.createBitmap((Constants.IMAGE_WIDTH * dp).toInt(),
        (Constants.IMAGE_HEIGHT * dp).toInt(), Bitmap.Config.ARGB_8888)
    private val image2 = Bitmap.createBitmap((Constants.IMAGE_WIDTH * dp).toInt(),
        (Constants.IMAGE_HEIGHT * dp).toInt(), Bitmap.Config.ARGB_8888)
    private var handImage = Bitmap.createBitmap((Constants.HAND_IMAGE_WIDTH * dp).toInt(),
        (Constants.HAND_IMAGE_HEIGHT * dp).toInt(), Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(image)
    private val canvas2 = Canvas(image2)
    private val paint = Paint()
    private var offset = Offset.ENROLL
    private var count = 0
    private var waiting = 0
    private var refresh = 0
    private var posture = Posture(0f, 0f, 0f, 0f, 0f, 0f, Status.NO_HANDS)

    // event
    private val handler = Handler(Looper.getMainLooper())
    private val guiListener: GUISampleListener = this
    private val service = PsService(activity, this)

    init {

        // resource string
        enrollButton.text = activity.getString(R.string.EnrollBtn)
        verifyButton.text = activity.getString(R.string.VerifyBtn)
        cancelButton.text = activity.getString(R.string.CancelBtn)
        endButton.text = activity.getString(R.string.ExitBtn)

        setProgressMessage("")
        setGuideMessage("")
        paintNoHand()

        // initialize PalmSecure
        service.Ps_Sample_Apl_Java_InitAuthDataFile()

        var resInit = PsThreadResult()
        resInit.result = -1L
        if (PsFileAccessorIni.GetInstance(activity) != null) {
            val param = GUIHelper(activity).createInitParam()
            resInit = service.Ps_Sample_Apl_Java_Request_InitLibrary(param)
        }
        if (resInit.result != PalmSecureConstant.JAVA_BioAPI_OK) {
            val err = if (resInit.result == -1L) "Read Error: "+PsFileAccessorIni.FileName
                else GUIHelper(activity).getErrorMessage(resInit)
            setGuideMessage(err)
            enrollButton.isEnabled = false
            verifyButton.isEnabled = false
            cancelButton.isEnabled = false
            endButton.isEnabled = false

            // initialize Error
            AlertDialog.Builder(activity)
                .setCancelable(false)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(guideMessage.text)
                .setPositiveButton("OK") { _, _ -> activity.finish() }
                .create().show()
        }
        else {
            service.Ps_Sample_Apl_Java_InitIdList()

            val lvl = PsFileAccessorIni.GetInstance(activity).GetValueInteger(PsFileAccessorIni.MessageLevel)
            when (lvl) {
                2 -> {
                    msgView.visibility = View.INVISIBLE
                }
                else -> {
                    guideMessage.visibility = View.INVISIBLE
                    progressMessage.visibility = View.INVISIBLE
                }
            }
            enrollButton.setOnClickListener(this)
            verifyButton.setOnClickListener(this)
            cancelButton.setOnClickListener(this)
            endButton.setOnClickListener(this)
            setButtonEnable(true)
        }
    }

    override fun close() {

        service.Ps_Sample_Apl_Java_Request_TermLibrary()
    }

    override fun guiSampleNotifyProgressMessage(message: String) {
        handler.post {
            setProgressMessage(message)
        }
    }

    override fun guiSampleNotifyGuidance(message: String) {
        handler.post {
            setGuideMessage(message)
        }
    }

    override fun guiSampleNotifySilhouette(bitmap: Bitmap) {
        handler.post {
            setHandImage(bitmap)
        }
    }

    override fun guiSampleNotifyPosture(posture: Posture) {
        handler.post {
            setPosture(posture)
        }
    }

    override fun guiSampleNotifyResult(result: Result) {
        handler.post {
            paintResult(result)
            setButtonEnable(true)
        }
    }

    override fun guiSampleNotifyOffset(offset: Offset) {
        handler.post {
            this.offset = offset
        }
    }

    override fun guiSampleNotifyCount(count: Int) {
        handler.post {
            this.count = count
        }
    }

    override fun onClick(view: View) {
        when (view) {
            enrollButton -> enrollClickEvent()
            verifyButton -> verifyClickEvent()
            cancelButton -> cancelClickEvent()
            endButton -> endClickEvent()
        }
    }

    private fun enrollClickEvent() {
        setButtonEnable(false)
        resetPosture(Status.NONE)

        // enroll
        val param = GUIHelper(activity).createThreadParam(PsService.USER)
        service.Ps_Sample_Apl_Java_Request_Enroll(param)

        guiListener.guiSampleNotifyOffset(Offset.ENROLL)
        guiListener.guiSampleNotifyProgressMessage(
            activity.getString(R.string.WorkEnrollStart)
        )
    }

    private fun verifyClickEvent() {
        setButtonEnable(false)
        resetPosture(Status.NONE)

        // verify
        val param = GUIHelper(activity).createThreadParam(PsService.USER)
        service.Ps_Sample_Apl_Java_Request_Verify(param)

        guiListener.guiSampleNotifyOffset(Offset.VERIFY)
        guiListener.guiSampleNotifyProgressMessage(
            activity.getString(R.string.WorkVerifyStart)
        )
    }

    private fun cancelClickEvent() {
        setButtonEnable(true)

        // cancel
        service.Ps_Sample_Apl_Java_Request_Cancel()
    }

    private fun endClickEvent() {

        activity.finish()
    }

    private fun setProgressMessage(message: String) {
        progressMessage.text = message
    }

    private fun setGuideMessage(message: String) {
        guideMessage.text = message
    }

    private fun setButtonEnable(flag: Boolean) {
        enrollButton.isEnabled = flag
        verifyButton.isEnabled = flag && service.Ps_Sample_Apl_Java_RegisteredId(PsService.USER)
        cancelButton.isEnabled = !flag
        endButton.isEnabled = flag

        val anim = imageView.background
        if (anim is Animatable)
            if (flag) {
                imageView.setBackground(null)
                imageView.setBackgroundResource(R.drawable.guide)
            }
            else anim.start()
    }

    private fun paintResult(result: Result) {
        when (result) {
            Result.CANCELED -> {
                msgView.setBackgroundResource(R.color.black)
            }
            Result.SUCCESSFUL -> {
                imageView.setImageBitmap(okImg)
                soundPool.play(okSound, 1.0f, 1.0f, 0, 0, 1.0f)
                val anim = imageView.background
                if (anim is Animatable && count > 0) {
                    anim.stop()
                    msgView.setBackgroundResource(R.drawable.enr_3)
                }
            }
            Result.FAILED -> {
                imageView.setImageBitmap(ngImg)
                soundPool.play(ngSound, 1.0f, 1.0f, 0, 0, 1.0f)
                val anim = imageView.background
                if (anim is Animatable && count > 0) {
                    anim.stop()
                    msgView.setBackgroundResource(R.drawable.enr_3)
                }
            }
            Result.ERROR -> {
                msgView.setBackgroundResource(R.color.black)
                if (!guideMessage.isVisible) {
                    // Lib Error
                    AlertDialog.Builder(activity)
                        .setCancelable(false)
                        .setTitle(android.R.string.dialog_alert_title)
                        .setMessage(guideMessage.text)
                        .setPositiveButton("OK", null)
                        .create().show()
                }
            }
        }
        count = 0
        waiting = 0
        //setButtonEnable(true)
    }

    private fun setPosture(posture: Posture) {
        when (posture.status) {
            Status.NONE -> {
                resetPosture(Status.NONE)
            }
            Status.NO_HANDS -> {
                waiting = 0
                paintNoHand()
                // clear hand
                handImage.eraseColor(Color.BLACK)
            }
            Status.AWAY_HANDS -> {
                waiting = 1
                paintNoHand()
            }
            else -> {
                this.posture = posture.copy()
                if ((offset == Offset.ENROLL && posture.z < Constants.DIST_MIN_ENROLL)
                    || (offset == Offset.VERIFY && posture.z < Constants.DIST_MIN_VERIFY)) {
                    // clear hand
                    handImage.eraseColor(Color.BLACK)
                }
                paintAll()
                refresh = 1
            }
        }
    }

    private fun resetPosture(status: Status) {
        posture = Posture(0f, 0f, 0f, 0f, 0f, 0f, status)
    }

    private fun setHandImage(hand: Bitmap) {
        handImage = hand.copy(Bitmap.Config.ARGB_8888, true)
        when (refresh) {
            0 -> paintAll()
            else -> refresh = 0
        }
    }

    private fun paintScenery() {
        paint.style = Paint.Style.STROKE
        paint.setARGB(255, 201,219, 250)
        paint.strokeWidth = (Constants.CIRCLE_LINE - 30) * dp
        canvas.drawCircle(
            Constants.IMAGE_CENTER_X * dp, Constants.IMAGE_CENTER_Y * dp,
            (Constants.CIRCLE_RAD + Constants.CIRCLE_LINE / 2 - 15) * dp, paint)
    }

    private fun paintScenery2() {
        paint.style = Paint.Style.STROKE
        paint.setARGB(255, 201,219, 250)
        paint.strokeWidth = (Constants.CIRCLE_LINE - 30) * dp
        canvas2.drawCircle(
            Constants.IMAGE_CENTER_X * dp, Constants.IMAGE_CENTER_Y * dp,
            (Constants.CIRCLE_RAD + Constants.CIRCLE_LINE / 2 - 15) * dp, paint)
    }

    private fun paintHand() {
        val matrix = Matrix()
        matrix.preScale(-dp, dp)
        canvas.drawBitmap(Bitmap.createBitmap(
            handImage, 0, 0, handImage.width, handImage.height, matrix, false),
            0f, Constants.HAND_IMAGE_TOP * dp, null)
    }

    private fun paintCross() {
        paint.strokeWidth = 2 * dp
        paint.color = Color.GREEN
        canvas.drawLine(0f, Constants.IMAGE_CENTER_Y * dp,
            Constants.IMAGE_WIDTH * dp, Constants.IMAGE_CENTER_Y * dp, paint)
        paint.color = Color.CYAN
        canvas.drawLine(
            Constants.IMAGE_CENTER_X * dp, 0f,
            Constants.IMAGE_CENTER_X * dp, Constants.IMAGE_HEIGHT * dp, paint)
    }

    private fun paintDistance() {
//        val r = Constants.CIRCLE_RAD - ((posture.z - if (offset == Offset.VERIFY)
        val cr = when {
            (posture.z > Constants.DIST_BORDER) -> Constants.DIST_BORDER
            (posture.z < 0f) -> (posture.z * 2)
            else -> posture.z
        }
        val r = Constants.CIRCLE_RAD - ((cr - if (offset == Offset.VERIFY)
            Constants.DIST_OFFSET_VERIFY else 0f) * Constants.DIST_ADJUST)
        paint.setARGB(128, 255, 255, 255)
        paint.style = Paint.Style.FILL
        if (posture.status == Status.ONLY_Z) {
            canvas.drawCircle(
                Constants.IMAGE_CENTER_X * dp,
                Constants.IMAGE_CENTER_Y * dp,
                r * dp, paint)
        } else if (posture.status.ordinal >= Status.ONLY_XYZ.ordinal) {
            canvas.drawCircle(
                (posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                (posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                r * dp, paint)
        }
    }

    private fun rotate(p: PointF, deg: Float): PointF {
        val rad = Math.PI * -deg / 180f
        return PointF((p.x * cos(rad) - p.y * sin(rad)).toFloat(),
            (p.x * sin(rad) + p.y * cos(rad)).toFloat())
    }

    private fun paintPosture() {
        if (posture.status.ordinal >= Status.ONLY_XYZ_AND_YAW.ordinal) {
            val r = Constants.CIRCLE_RAD - ((posture.z - if (offset == Offset.VERIFY)
                Constants.DIST_OFFSET_VERIFY else 0f) * Constants.DIST_ADJUST)
            var p = rotate(PointF(r, 0f), posture.yaw)
            var q = rotate(PointF(-r, 0f), posture.yaw)
            paint.strokeWidth = dp
            paint.color = Color.GREEN
            canvas.drawLine(
                (p.x + posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                (p.y + posture.y * Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                (q.x + posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                (q.y + posture.y * Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                paint)
            p = rotate(PointF(0f, r), posture.yaw)
            q = rotate(PointF(0f, -r), posture.yaw)
            paint.color = Color.CYAN
            canvas.drawLine(
                (p.x + posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                (p.y + posture.y * Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                (q.x + posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                (q.y + posture.y * Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                paint)
            if (posture.status == Status.ALL) {
                p = rotate(PointF(posture.roll * Constants.POSTURE_ADJUST,
                    posture.pitch * Constants.POSTURE_ADJUST
                ), posture.yaw)
                paint.setARGB(128, 255, 0, 0)
                canvas.drawCircle(
                    (p.x + posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                    (p.y + posture.y * Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                    Constants.LEVEL_RAD * dp, paint)
            }
        }
    }

    private fun paintMessage() {
        when (count) {
            1 -> msgView.setBackgroundResource(R.drawable.gauge1)
            2 -> msgView.setBackgroundResource(R.drawable.gauge2)
            3 -> msgView.setBackgroundResource(R.drawable.gauge3)
            else -> msgView.setBackgroundResource(R.color.black)
        }
        val anim = msgView.background
        if (anim is Animatable) anim.start()
    }

    private fun paintAll() {
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        canvas.drawRect(Rect(0, 0, canvas.width, canvas.height), paint)
        paintHand()
        paintCross()
        paintScenery()
        paintDistance()
        paintPosture()
        imageView.setImageBitmap(image)

        paintMessage()
    }

    private fun paintNoHand() {
        paintScenery2()
        imageView.setImageBitmap(image2)
        when (waiting) {
            1 -> imageView.setBackgroundResource(R.drawable.up_1)
            else -> {
                imageView.setBackgroundResource(R.drawable.guide)
                val anim = imageView.background
                if (anim is Animatable) {
                    if (!anim.isRunning && count == 1) anim.start()
                }
            }
        }

        paintMessage()
    }
}