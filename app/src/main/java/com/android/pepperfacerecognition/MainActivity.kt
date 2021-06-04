package com.android.pepperfacerecognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
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
import edmt.dev.edmtdevcognitiveface.Contract.IdentifyResult
import edmt.dev.edmtdevcognitiveface.Contract.Person
import edmt.dev.edmtdevcognitiveface.Contract.TrainingStatus
import edmt.dev.edmtdevcognitiveface.FaceServiceClient
import edmt.dev.edmtdevcognitiveface.FaceServiceRestClient
import edmt.dev.edmtdevcognitiveface.Rest.ClientException
import io.fotoapparat.Fotoapparat
import io.fotoapparat.log.logcat
import io.fotoapparat.log.loggers
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.selector.front
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    lateinit var binding: ActivityMainBinding

    companion object {
        private const val API_KEY = "acff5f3d46c749959a6b43d0da5acc20"
        private const val API_LINK =
            "https://pepperrecognition.cognitiveservices.azure.com/face/v1.0"
        var customerName = ""
        private const val TAG = "recognition_log"
        private var personGroupID = "workplace"
    }

    var fotoapparat: Fotoapparat? = null
    var fotoapparatState: FotoapparatState? = null
    var cameraStatus: CameraState? = null
    val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private val faceServiceClient: FaceServiceClient = FaceServiceRestClient(API_LINK, API_KEY)


    var faceDetected: Array<Face>? = null
    var pictureBitmap: Bitmap? = null
    var inputStream: InputStream? = null

    private var qiContext: QiContext? = null
    private var timestampedImageHandleFuture: Future<TimestampedImageHandle>? = null
    var takePictureFuture: Future<TakePicture>? = null
    private var humanAwareness: HumanAwareness? = null
    var chatFuture: Future<Void>? = null
    var chat: Chat? = null
    var qiChatbot: QiChatbot? = null
    private var detectedBookmark: Bookmark? = null
    private var identifyBookmark: Bookmark? = null
    private var unknownBookmark: Bookmark? = null
    private var variable: QiChatVariable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        QiSDK.register(this, this)
        setContentView(binding.root)

        //TODO Uncoment this to create new group
//        binding.groupNameTV.text = "Group Name:  " + personGroupID
//        binding.createGroup.setOnClickListener {
//            binding.createGroupDone.visibility = View.VISIBLE
//            binding.personNameEditText.visibility = View.VISIBLE
//        }
//        binding.createGroupDone.setOnClickListener {
//            if (binding.personNameEditText.text!!.isNotEmpty()) {
//                binding.createGroupDone.visibility = View.GONE
//                personGroupID = binding.personNameEditText.text.toString()
//                AddPersonGroupTask().createPersonGroup(personGroupID, personGroupID)
//                binding.personNameEditText.visibility = View.GONE
//                binding.groupNameTV.text = "Group Name: " + personGroupID
//                binding.personNameEditText.text!!.clear()
//                findHumansAround()
//            }
//        }

        createFotoaparat()
        cameraStatus = CameraState.FRONT
        binding.fabCamera.setOnClickListener {
            takePhoto()
        }
        binding.doneButton.setOnClickListener {
            AddPersonGroupTask().addPersonToGroup(
                personGroupID,
                binding.personNameEditText.text.toString(),
                inputStream!!
            )
            AddPersonGroupTask().trainingAi(personGroupID)
            Handler().postDelayed({
                binding.takenPhotoLayout.visibility = View.GONE
                findHumansAround()
            }, 3000)
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


    private fun recognitionFace() {
        d("TAGqqqqqq", "shevida")
        val outputStream = ByteArrayOutputStream()
        pictureBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        inputStream = ByteArrayInputStream(outputStream.toByteArray())
        DetectTask().execute(inputStream)
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

        chat = ChatBuilder.with(qiContext)
            .withChatbot(qiChatbot)
            .build()
        val bookmarks: Map<String, Bookmark> = topic.bookmarks
        detectedBookmark = bookmarks["detected"]
        identifyBookmark = bookmarks["cant_identify"]
        unknownBookmark = bookmarks["unknown"]

        chatFuture = chat!!.async().run()

        findHumansAround()

        qiChatbot!!.addOnBookmarkReachedListener {
            when (it.name) {
                "YES" -> {
                    binding.cameraView.visibility = View.VISIBLE
                    binding.fabCamera.visibility = View.VISIBLE
                    binding.yesButton.visibility = View.GONE
                    binding.noButton.visibility = View.GONE
                }
                "NO" -> {
                    binding.cameraView.visibility = View.GONE
                    binding.fabCamera.visibility = View.GONE
                    binding.yesButton.visibility = View.GONE
                    binding.noButton.visibility = View.GONE
                }
                "ASK_ADD" -> {
                    binding.yesButton.visibility = View.VISIBLE
                    binding.noButton.visibility = View.VISIBLE
                }
            }
        }

    }

    private fun assignVariable(varName: String, value: String?, bookmark: Bookmark?) {
        Thread {
            variable = qiChatbot!!.variable(varName)
            variable?.async()?.setValue(value)!!.andThenConsume {
                qiChatbot?.goToBookmark(
                    bookmark,
                    AutonomousReactionImportance.HIGH,
                    AutonomousReactionValidity.IMMEDIATE
                )
            }
        }.start()
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

    private fun findHumansAround() {
        val humanAwareness = this.humanAwareness
        val humansAroundFuture = humanAwareness?.async()?.humansAround
        humansAroundFuture?.andThenConsume {
            Log.i("TAG", it.size.toString() + " human(s) around.")
            this.runOnUiThread {
                Toast.makeText(this@MainActivity, "${it.size} human(s) around.", Toast.LENGTH_SHORT)
                    .show()
            }
            if (it.size == 0) {
                this.runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "${it.size} No human(s) around.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                Handler(this.mainLooper).postDelayed({
                    findHumansAround()
                }, 5000)

            } else {
                takePictureFuture = TakePictureBuilder.with(qiContext).buildAsync()
                takePicture()
            }
        }
    }

    private fun takePicture() {
        if (qiContext == null) {
            Toast.makeText(this, "Can't see you", Toast.LENGTH_SHORT).show()
            return
        }
        timestampedImageHandleFuture = takePictureFuture!!.andThenCompose { takePicture ->
            Log.i(TAG, "take picture launched!")
            takePicture.async().run()
        }

        timestampedImageHandleFuture?.andThenConsume { timestampedImageHandle ->
            Log.i(TAG, "Picture taken")
            val encodedImageHandle: EncodedImageHandle = timestampedImageHandle.image

            val encodedImage: EncodedImage = encodedImageHandle.value
            Log.i(TAG, "PICTURE RECEIVED!")
            // get the byte buffer and cast it to byte array
            val buffer = encodedImage.data
            buffer.rewind()
            val pictureBufferSize: Int = buffer.remaining()
            val pictureArray = ByteArray(pictureBufferSize)
            buffer.get(pictureArray)

            Log.i(TAG, "PICTURE RECEIVED! ($pictureBufferSize Bytes)")
            // display picture
            pictureBitmap = BitmapFactory.decodeByteArray(pictureArray, 0, pictureBufferSize)
//            uploadImageToFirebaseStorage()
            runOnUiThread {
                recognitionFace()
            }
        }
    }


    override fun onRobotFocusLost() {
        this.qiContext = null
        QiSDK.unregister(this, this)
    }

    override fun onRobotFocusRefused(reason: String?) {

    }

    internal inner class DetectTask : AsyncTask<InputStream?, String?, Array<Face>?>() {

        override fun onPostExecute(faces: Array<Face>?) {
            if (faces == null) {
                Toast.makeText(this@MainActivity, "No Face detected", Toast.LENGTH_LONG)
                    .show()
                Handler(this@MainActivity.mainLooper).postDelayed({
                    findHumansAround()
                }, 5000)
            } else {
                faceDetected = faces
                if (faceDetected!!.isNotEmpty()) {
                    IdentificationTask().execute(faceDetected!![0].faceId)

                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "No Face to detect",
                        Toast.LENGTH_LONG
                    ).show()

                    assignVariable("cant_see", "Hello stranger, I can't see you, please come closer", unknownBookmark!!)

                    Handler(this@MainActivity.mainLooper).postDelayed({
                        findHumansAround()
                    }, 8000)
                }
            }
        }

        override fun doInBackground(vararg params: InputStream?): Array<Face>? {
            try {
                return faceServiceClient.detect(params[0], true, false, null)
            } catch (e: ClientException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }
    }

    internal inner class PersonDetectionTask : AsyncTask<UUID?, String?, Person?>() {

        override fun onPostExecute(person: Person?) {
            d("TAG", "blablabla")

            if (!person!!.name.isNullOrEmpty()) {
                customerName = person!!.name.split(" ")[0]
                Toast.makeText(this@MainActivity, "Hello $customerName", Toast.LENGTH_SHORT).show()
                Handler(this@MainActivity.mainLooper).postDelayed({
                    findHumansAround()
                }, 5000)

                assignVariable("var", customerName, detectedBookmark!!)

            } else {
                Handler(this@MainActivity.mainLooper).postDelayed({
                    findHumansAround()
                }, 5000)
            }
        }

        override fun doInBackground(vararg params: UUID?): Person? {
            try {
                return faceServiceClient.getPerson(personGroupID, params[0])
            } catch (e: ClientException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }
    }

    internal inner class IdentificationTask : AsyncTask<UUID?, String?, Array<IdentifyResult>?>() {

        override fun onPostExecute(identifyResults: Array<IdentifyResult>?) {
            if (identifyResults != null && identifyResults.isNotEmpty()) {
                if (identifyResults[0].candidates.size > 0) {
                    PersonDetectionTask().execute(identifyResults[0].candidates[0].personId)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Can't Identify",
                        Toast.LENGTH_SHORT
                    ).show()
                    assignVariable("unknown_customer", "Hello, I don't know you", identifyBookmark!!)

                }
            }
        }

        override fun doInBackground(vararg params: UUID?): Array<IdentifyResult>? {
            try {
                val trainingStatus = faceServiceClient.getPersonGroupTrainingStatus(personGroupID)
                if (trainingStatus.status != TrainingStatus.Status.Succeeded) {
                    d("ERROR", "Person Group Training status is " + trainingStatus.status)
                    return null
                }
                return faceServiceClient.identity(personGroupID, params, 1)
            } catch (e: ClientException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }
    }
}
