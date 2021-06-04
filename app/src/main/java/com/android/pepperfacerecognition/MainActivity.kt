package com.android.pepperfacerecognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.util.Log.d
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.camera.TakePicture
import com.aldebaran.qi.sdk.`object`.conversation.*
import com.aldebaran.qi.sdk.`object`.humanawareness.HumanAwareness
import com.aldebaran.qi.sdk.`object`.image.EncodedImage
import com.aldebaran.qi.sdk.`object`.image.EncodedImageHandle
import com.aldebaran.qi.sdk.`object`.image.TimestampedImageHandle
import com.aldebaran.qi.sdk.builder.*
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.android.pepperfacerecognition.databinding.ActivityMainBinding
import com.android.pepperfacerecognition.helper.AddPersonGroupTask
import com.android.pepperfacerecognition.helper.CameraState
import com.android.pepperfacerecognition.helper.FotoapparatState
import edmt.dev.edmtdevcognitiveface.Contract.Face
import edmt.dev.edmtdevcognitiveface.FaceServiceClient
import edmt.dev.edmtdevcognitiveface.FaceServiceRestClient
import io.fotoapparat.Fotoapparat
import io.fotoapparat.log.logcat
import io.fotoapparat.log.loggers
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.selector.front
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    lateinit var binding: ActivityMainBinding

    companion object {
        const val API_KEY = "acff5f3d46c749959a6b43d0da5acc20"
        const val API_LINK =
            "https://pepperrecognition.cognitiveservices.azure.com/face/v1.0"
        var customerName = ""
        const val TAG = "recognition_log"
        val faceServiceClient: FaceServiceClient = FaceServiceRestClient(API_LINK, API_KEY)

        //        private var personGroupID = "workplace"
        var personGroupID = "zxcv"
        var variable: QiChatVariable? = null
        var qiChatbot: QiChatbot? = null
        var detectedBookmark: Bookmark? = null
        var identifyBookmark: Bookmark? = null
        var unknownBookmark: Bookmark? = null
        var faceDetected: Array<Face>? = null

    }

    var fotoapparat: Fotoapparat? = null
    var fotoapparatState: FotoapparatState? = null
    var cameraStatus: CameraState? = null
    val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )


    var pictureBitmap: Bitmap? = null
    var inputStream: InputStream? = null

    private var qiContext: QiContext? = null
    private var timestampedImageHandleFuture: Future<TimestampedImageHandle>? = null
    var takePictureFuture: TakePicture? = null
    private var humanAwareness: HumanAwareness? = null
    var chatFuture: Future<Void>? = null
    var chat: Chat? = null

    var scope: CoroutineScope = CoroutineScope(Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        QiSDK.register(this, this)
        setContentView(binding.root)

        //TODO Uncoment this to create new group
        binding.groupNameTV.text = "Group Name:  " + personGroupID
        binding.createGroup.setOnClickListener {
            binding.createGroupDone.visibility = View.VISIBLE
            binding.personNameEditText.visibility = View.VISIBLE
        }
        binding.createGroupDone.setOnClickListener {
            if (binding.personNameEditText.text!!.isNotEmpty()) {
                binding.createGroupDone.visibility = View.GONE
                personGroupID = binding.personNameEditText.text.toString()
                AddPersonGroupTask().createPersonGroup(personGroupID, personGroupID)
                binding.personNameEditText.visibility = View.GONE
                binding.groupNameTV.text = "Group Name: " + personGroupID
                binding.personNameEditText.text!!.clear()
                binding.cameraView.visibility = View.VISIBLE
                binding.fabCamera.visibility = View.VISIBLE
                binding.yesButton.visibility = View.GONE
                binding.noButton.visibility = View.GONE
            }
        }

        createFotoaparat()
        cameraStatus = CameraState.FRONT
        binding.fabCamera.setOnClickListener {
            takePhoto()
        }
        binding.doneButton.setOnClickListener {
            binding.takenPhotoLayout.visibility = View.GONE
            AddPersonGroupTask().addPersonToGroup(
                personGroupID,
                binding.personNameEditText.text.toString(),
                inputStream!!
            )
            AddPersonGroupTask().trainingAi(personGroupID)
            CoroutineScope(IO).launch {
                delay(5000)
                findHumansAround()
            }
        }

        binding.yesButton.setOnClickListener {
            binding.cameraView.visibility = View.VISIBLE
            binding.fabCamera.visibility = View.VISIBLE
            binding.yesButton.visibility = View.GONE
            binding.noButton.visibility = View.GONE

        }
        binding.noButton.setOnClickListener {
            binding.cameraView.visibility = View.GONE
            binding.fabCamera.visibility = View.GONE
            binding.yesButton.visibility = View.GONE
            binding.noButton.visibility = View.GONE
            findHumansAround()
        }

    }

    private fun takePhoto() {
        if (hasNoPermissions()) {
            requestPermission()
        } else {
            val photoResult = fotoapparat?.takePicture()
            photoResult!!.toBitmap()
                .whenAvailable { bitmapPhoto ->
                    binding.cameraView.visibility = View.GONE
                    binding.takenPhotoLayout.visibility = View.VISIBLE
                    binding.doneButton.visibility = View.VISIBLE
                    binding.personNameEditText.visibility = View.VISIBLE
                    binding.fabCamera.visibility = View.GONE
                    binding.takenPhotoImageView.setImageBitmap(bitmapPhoto!!.bitmap)
                    pictureBitmap = bitmapPhoto.bitmap
                    val outputStream = ByteArrayOutputStream()
                    pictureBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    inputStream = ByteArrayInputStream(outputStream.toByteArray())
                }
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasNoPermissions()) {
            requestPermission()
        } else {
            fotoapparat?.start()
            fotoapparatState = FotoapparatState.ON
        }
    }

    private fun hasNoPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, permissions, 0)
    }

    override fun onStop() {
        super.onStop()
        fotoapparat?.stop()
        FotoapparatState.OFF
    }

    override fun onResume() {
        super.onResume()
        if (!hasNoPermissions() && fotoapparatState == FotoapparatState.OFF) {
            val intent = Intent(baseContext, this::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun createFotoaparat() {
        fotoapparat = Fotoapparat(
            context = this,
            view = binding.cameraView,
            scaleType = ScaleType.CenterCrop,
            lensPosition = front(),
            logger = loggers(
                logcat()
            ),
            cameraErrorCallback = { error ->
                println("Recorder errors: $error")
            }
        )
    }

    private suspend fun recognitionFace() {
        d("TAGqqqqqq", "shevida")
        val outputStream = ByteArrayOutputStream()
        pictureBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        inputStream = ByteArrayInputStream(outputStream.toByteArray())
        IdentifyPerson(this).detectBackground(inputStream)
    }

    override fun onRobotFocusGained(qiContext: QiContext?) {
        this.qiContext = qiContext
        humanAwareness = qiContext!!.humanAwareness

        val topic = TopicBuilder.with(qiContext)
            .withResource(R.raw.chat)
            .build()

        qiChatbot = QiChatbotBuilder.with(qiContext)
            .withTopic(topic)
            .build()


        val bookmarks: Map<String, Bookmark> = topic.bookmarks
        detectedBookmark = bookmarks["detected"]
        identifyBookmark = bookmarks["cant_identify"]
        unknownBookmark = bookmarks["unknown"]

        qiChatbot!!.addOnBookmarkReachedListener {
            d("Bookmark Reached", "Bookmark Reached ${it.name} ")
            when (it.name) {
                "YES" -> {
                    CoroutineScope(Main).launch {
                        binding.cameraView.visibility = View.VISIBLE
                        binding.fabCamera.visibility = View.VISIBLE
                        binding.yesButton.visibility = View.GONE
                        binding.noButton.visibility = View.GONE
                    }
                }
                "NO" -> {
                    CoroutineScope(Main).launch {
                        binding.cameraView.visibility = View.GONE
                        binding.fabCamera.visibility = View.GONE
                        binding.yesButton.visibility = View.GONE
                        binding.noButton.visibility = View.GONE
                    }

                }
                "ASK_ADD" -> {
                    CoroutineScope(Dispatchers.Main).launch {
                        binding.yesButton.visibility = View.VISIBLE
                        binding.noButton.visibility = View.VISIBLE
                    }
                }
            }
        }
        chat = ChatBuilder.with(qiContext)
            .withChatbot(qiChatbot)
            .build()

        chatFuture = chat!!.async().run()

        findHumansAround()
    }

    fun jumpToBookmark(bookmark: Bookmark?): QiChatbot {
        bookmark?.let {
            qiChatbot!!.async()?.goToBookmark(
                it,
                AutonomousReactionImportance.HIGH,
                AutonomousReactionValidity.DELAYABLE
            )
        }
        return qiChatbot!!
    }

    fun findHumansAround() {
        scope.launch {
            val humans = withContext(IO) { humanAwareness!!.humansAround }
            Log.i("TAG", humans.size.toString() + " human(s) around.")
            Toast.makeText(this@MainActivity, "${humans.size} human(s) around.", Toast.LENGTH_SHORT)
                .show()
            if (humans.size == 0) {
                Toast.makeText(
                    this@MainActivity,
                    "${humans.size} No human(s) around.",
                    Toast.LENGTH_SHORT
                ).show()
                delay(5000)
                findHumansAround()
            } else {
                Log.i("FindHumansAround", takePicture())
            }
        }
    }

    private suspend fun takePicture(): String {
        return withContext(IO) {
            takePictureFuture = TakePictureBuilder.with(qiContext).build()
            val takePicture = async { takePictureFuture!!.run() }
            Log.i(TAG, "Picture taken")
            val encodedImageHandle: EncodedImageHandle = takePicture.await().image
            val encodedImage: EncodedImage = encodedImageHandle.value
            Log.i(TAG, "PICTURE RECEIVED!")
            // get the byte buffer and cast it to byte array
            val buffer = encodedImage.data
            buffer.rewind()
            val pictureBufferSize: Int = buffer.remaining()
            val pictureArray = ByteArray(pictureBufferSize)
            buffer.get(pictureArray)
            Log.i(TAG, "PICTURE RECEIVED! ($pictureBufferSize Bytes)")
            pictureBitmap = BitmapFactory.decodeByteArray(pictureArray, 0, pictureBufferSize)
            recognitionFace()
            return@withContext "Picture Taken,"
        }
    }



    override fun onRobotFocusLost() {
        this.qiContext = null
        QiSDK.unregister(this, this)
    }

    override fun onRobotFocusRefused(reason: String?) {

    }


}
