package com.android.pepperfacerecognition

import android.util.Log
import android.util.Log.d
import android.widget.Toast
import com.aldebaran.qi.sdk.`object`.conversation.AutonomousReactionImportance
import com.aldebaran.qi.sdk.`object`.conversation.AutonomousReactionValidity
import com.aldebaran.qi.sdk.`object`.conversation.Bookmark
import com.android.pepperfacerecognition.MainActivity.Companion.customerName
import com.android.pepperfacerecognition.MainActivity.Companion.detectedBookmark
import com.android.pepperfacerecognition.MainActivity.Companion.faceDetected
import com.android.pepperfacerecognition.MainActivity.Companion.faceServiceClient
import com.android.pepperfacerecognition.MainActivity.Companion.identifyBookmark
import com.android.pepperfacerecognition.MainActivity.Companion.personGroupID
import com.android.pepperfacerecognition.MainActivity.Companion.qiChatbot
import com.android.pepperfacerecognition.MainActivity.Companion.unknownBookmark
import com.android.pepperfacerecognition.MainActivity.Companion.variable
import edmt.dev.edmtdevcognitiveface.Contract.Face
import edmt.dev.edmtdevcognitiveface.Contract.IdentifyResult
import edmt.dev.edmtdevcognitiveface.Contract.Person
import edmt.dev.edmtdevcognitiveface.Contract.TrainingStatus
import edmt.dev.edmtdevcognitiveface.Rest.ClientException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import java.io.IOException
import java.io.InputStream
import java.util.*

class IdentifyPerson(val context: MainActivity) : CoroutineScope by CoroutineScope(IO) {

    private suspend fun identificationTaskPost(identifyResults: Array<IdentifyResult>?): String {
        return withContext(IO) {
            if (identifyResults != null && identifyResults.isNotEmpty()) {
                if (identifyResults[0].candidates.size > 0) {
                    personDetectionBackground(identifyResults[0].candidates[0].personId)
                } else {
                    assignVariable(
                        "unknown_customer",
                        "Hello, I don't know you",
                        identifyBookmark!!
                    )
                    return@withContext "Can't Identify"
                }
            }
            Log.i("WithContext", "identificationTaskPost  : Done ")
            return@withContext "All Done"
        }
    }

    private suspend fun identificationBackground(vararg params: UUID?) {
        withContext(IO) {
            try {
                val trainingStatus =
                    faceServiceClient.getPersonGroupTrainingStatus(personGroupID)
                if (trainingStatus.status != TrainingStatus.Status.Succeeded) {
                    d("ERROR", "Person Group Training status is " + trainingStatus.status)
                }
                val result =
                    identificationTaskPost(faceServiceClient.identity(personGroupID, params, 1))
                withContext(Main) {
                    Log.i("WithContext", "identificationBackground  : $result ")
                    if (result != "All Done") {
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: ClientException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    private suspend fun personDetectionPost(person: Person?): String {
        return withContext(IO) {
            d("TAG", "blablabla")
            if (!person!!.name.isNullOrEmpty()) {
                customerName = person.name.split(" ")[0]
                assignVariable("var", customerName, detectedBookmark!!)
                return@withContext "Hello $customerName"
            }
            Log.i("WithContext", "personDetectionPost  : Done ")
            return@withContext "All Done"
        }
    }

    private suspend fun personDetectionBackground(vararg params: UUID?) {
        withContext(IO) {
            try {
                val result =
                    personDetectionPost(faceServiceClient.getPerson(personGroupID, params[0]))
                withContext(Main) {
                    Log.i("WithContext", "personDetectionBackground  : $result ")
                    if (result != "All Done") {

                        Toast.makeText(
                            context,
                            result,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: ClientException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    private suspend fun detectPost(faces: Array<Face>?): String {
        return withContext(IO) {
            if (faces == null) {
                return@withContext "Face Didnt Recognized"
            } else {
                faceDetected = faces
                if (faceDetected!!.isNotEmpty()) {
                    identificationBackground(faceDetected!![0].faceId)
                } else {
                    assignVariable(
                        "cant_see",
                        "Hello stranger, I can't see you, please come closer",
                        unknownBookmark!!
                    )
                    return@withContext "No Face to detect"
                }
            }
            Log.i("WithContext", "detectPost : Done ")
            return@withContext "All Done"
        }
    }

    suspend fun detectBackground(vararg params: InputStream?) {
        coroutineScope {
            try {
                val result = detectPost(faceServiceClient.detect(params[0], true, false, null))
                withContext(Main) {
                    Log.i("CS:DetectBackground", "Toast : $result")
                    if (result != "All Done") {
                        Toast.makeText(
                            context,
                            result,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                Log.i("FirstFunc", "Starting Delay")
                delay(8000)
                Log.i("FirstFunc", "Delay Ended")
                context.findHumansAround()
            } catch (e: ClientException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}


fun assignVariable(varName: String, value: String?, bookmark: Bookmark?) {
    variable = qiChatbot!!.variable(varName)
    variable?.async()?.setValue(value)!!.andThenConsume {
        qiChatbot?.goToBookmark(
            bookmark,
            AutonomousReactionImportance.HIGH,
            AutonomousReactionValidity.IMMEDIATE
        )
    }
}


