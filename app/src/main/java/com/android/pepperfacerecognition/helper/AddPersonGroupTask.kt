package com.android.pepperfacerecognition.helper

import android.util.Log.d
import edmt.dev.edmtdevcognitiveface.Contract.TrainingStatus
import edmt.dev.edmtdevcognitiveface.FaceServiceRestClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream


class AddPersonGroupTask: CoroutineScope by CoroutineScope(IO) {

    var faceServiceClient: FaceServiceRestClient = FaceServiceRestClient(
        "https://pepperrecognition.cognitiveservices.azure.com/face/v1.0",
        "acff5f3d46c749959a6b43d0da5acc20"
    )

    fun createPersonGroup(personGroupId: String, PersonGroupName: String) {

        launch {
            try {
                faceServiceClient.createPersonGroup(personGroupId, PersonGroupName, null)
                d("TAG1", "Create Person Group Succeed")
            } catch (ex: Exception) {
                d("TAG1", "ERROR$ex")
            }
        }
    }

    fun addPersonToGroup(personGroupId: String, personGroupName: String, imagePath: InputStream) {
        d("TAG1", imagePath.toString())
        launch {
            try {
                faceServiceClient.getPersonGroup(personGroupId)
                d("TAG1", imagePath.toString())
                val personResult =
                    faceServiceClient.createPerson(personGroupId, personGroupName, null)
                d("TAG1", "SUCCESS")
                faceServiceClient.getPersonGroup(personGroupId)

                faceServiceClient.addPersonFace(
                    personGroupId,
                    personResult.personId,
                    imagePath,
                    null,
                    null
                )

                faceServiceClient.trainPersonGroup(personGroupId)

            } catch (ex: Exception) {
                d("TAG1", "ERROR$ex")
            }
        }

    }

    fun trainingAi(personGroupId: String) {
        var training: TrainingStatus? = null
        launch {
            d("groupid", personGroupId)
            d("groupid", personGroupId)
            while (true) {
                d("trainingStatus", faceServiceClient.toString())
                training = faceServiceClient.getPersonGroupTrainingStatus(personGroupId)
                if (training!!.status != TrainingStatus.Status.Running) {
                    d("TAG", "Status: ${training!!.status}")
                    break
                }
                d("TAG", "Waiting for training Ai...")
                delay(1000)
            }
        }

    }

}

