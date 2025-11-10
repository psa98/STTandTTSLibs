package com.ponomarev.sttandttsdemo


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.speech.tts.Voice
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import c.ponom.swenska.tts.SpeakerApi
import c.ponom.swenska.tts.SpeakerApi.compareTo
import com.pon.speech_to_text_wrapper.SttClassApi
import com.pon.speech_to_text_wrapper.SttClassApi.ApiState.CREATED_NOT_READY
import com.pon.speech_to_text_wrapper.SttClassApi.ApiState.FINISHED_AND_READY
import com.pon.speech_to_text_wrapper.SttClassApi.ApiState.INITIALISED_READY
import com.pon.speech_to_text_wrapper.SttClassApi.ApiState.WORKING_MIC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


class TestActivity() : ComponentActivity() {

    private var recognizerApi: SttClassApi = SttClassApi()
    private var speakApi: SpeakerApi = SpeakerApi


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScreen() }
    }

    @Composable
    private fun MainScreen() {
        val context = LocalContext.current
        var apiState by rememberSaveable { mutableStateOf(CREATED_NOT_READY) }
        var recordButtonEnabled by rememberSaveable { mutableStateOf(false) }
        var recordButtonText by rememberSaveable { mutableStateOf("Подготовка оборудования") }
        var permissionsButtonEnabled by rememberSaveable { mutableStateOf(false) }
        var permissionsButtonText by rememberSaveable { mutableStateOf("") }
        var currentText by rememberSaveable { mutableStateOf("") }
        var partialText by rememberSaveable { mutableStateOf("") }
        var useBT by rememberSaveable { mutableStateOf(true) }
        var seconds by rememberSaveable { mutableStateOf("") }
        var textToSpeak by rememberSaveable { mutableStateOf("Введите сюда текст для произношения") }
        var sayWordButtonText by rememberSaveable { mutableStateOf("Подготовка оборудования") }
        var sayWordButtonEnabled by rememberSaveable { mutableStateOf(false) }
        var permissions by rememberSaveable { mutableStateOf(hasPermissions(context)) }


        suspend fun CoroutineScope.initSTT() {
            Log.e("", "initSTT: =")
            /*
          возможный вариант без try
          val result = runCatching { recognizerApi.initSTT(context).await() }
          result.onFailure {
              it.printStackTrace()
              recordButtonText = "Не удалось инициализировать API"
              recordButtonEnabled = false
          }
          result.onSuccess {
              recordButtonEnabled = true
              recordButtonText = "Начать распознавание"
              mainScope.launch {
                  it.allWords.collect {
                      currentText = it.toString()
                  }
              }
          }
           */
            try {
                recognizerApi.initSTT(context, true).await()
                recordButtonEnabled = true
                recordButtonText = "Начать распознавание"
                launch {
                    recognizerApi.recordTime.collect {
                        seconds = (it / 1000f).toString()
                    }
                }
                launch {
                    recognizerApi.allWords.collect {
                        currentText = it.toString()
                    }
                }
                launch {
                    recognizerApi.partialWords.collect {
                        partialText = it.toString()
                    }
                }
                launch {
                    recognizerApi.apiState.collect {

                        apiState = it
                        when (apiState) {
                            CREATED_NOT_READY -> {
                                recordButtonText = "Идет подготовка оборудования"
                                recordButtonEnabled = false
                            }

                            INITIALISED_READY -> {
                                recordButtonText = "Начать распознавание"
                                recordButtonEnabled = true

                            }

                            WORKING_MIC -> {
                                recordButtonText = "Остановить запись"
                                recordButtonEnabled = true
                            }

                            FINISHED_AND_READY -> {
                                recordButtonText = "Начать распознавание"
                                recordButtonEnabled = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                recordButtonText = "Не удалось инициализировать API"
                recordButtonEnabled = false
            }
        }
        var scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        )
        {
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    recordButtonEnabled = false
                    recordButtonText = "Идет подготовка оборудования"
                    permissions = true
                } else {
                    recordButtonEnabled = false
                    recordButtonText = "Разрешение для микрофона не получено"
                }
            }
            PermissionsButton(launcher, permissionsButtonEnabled, permissionsButtonText)
            TextField(
                value = "Все распознанные слова:\n$currentText",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )

            TextField(
                value = "Слова по мере распознавания:\n$partialText",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
            Text(text = seconds)
            Row {
                Text(text = "Использовать Bluetooth \n микрофон")
                Checkbox(useBT, onCheckedChange = {
                    useBT = it
                    recognizerApi.useBluetoothMic = it
                })
            }

            RecordButton(recordButtonEnabled, recordButtonText)

            TextField(
                value = textToSpeak,
                onValueChange = { textToSpeak = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                singleLine = false,
                readOnly = false,
                placeholder = { Text(text = "Введите текст для произнесения") }
            )

            Button(
                onClick = {
                    speakApi.speakPhrase(
                        textToSpeak,
                        callbackOnStart = { if (recognizerApi.state == WORKING_MIC) recognizerApi.pauseMic() },
                        callbackOnError = {},
                        callbackOnEnd = { if (recognizerApi.state == WORKING_MIC) recognizerApi.unpauseMic() })
                },
                enabled = sayWordButtonEnabled,
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(text = sayWordButtonText)
            }

            SetVoiceButton(sayWordButtonEnabled)

            LaunchedEffect(permissions) {
                if (!permissions) {
                    permissionsButtonEnabled = true
                    permissionsButtonText = "Нажмите для получения разрешения"
                    recordButtonEnabled = false
                    recordButtonText = "Разрешение для микрофона не получено"
                    return@LaunchedEffect
                }
                permissionsButtonEnabled = false
                permissionsButtonText = "Разрешение на доступ к микрофону предоставлено"
                recordButtonEnabled = false
                recordButtonText = "Подготовка оборудования"

                speakApi.prepare(context) { success ->
                    if (success) {
                        sayWordButtonEnabled = true
                        textToSpeak = "Проверка голоса"
                        sayWordButtonText = "Произнести фразу"
                    } else {
                        sayWordButtonEnabled = false
                        textToSpeak = "Проверка голоса"
                        sayWordButtonText = "Ошибка API TTS"
                    }
                }
                initSTT()
            }
        }
    }

    @Composable
    private fun ColumnScope.SetVoiceButton(sayWordButtonEnabled: Boolean) {
        Button(
            onClick = {
                setTextToSpeechVoice()
            },
            enabled = sayWordButtonEnabled,
            modifier = Modifier
                .wrapContentWidth()
                .align(Alignment.CenterHorizontally)
        ) {
            Text(text = "Выбор голоса")
        }
    }

    @Composable
    private fun PermissionsButton(
        launcher: ManagedActivityResultLauncher<String, Boolean>,
        permissionsButtonEnabled: Boolean,
        permissionsButtonText: String
    ) {
        Button(
            onClick = {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            },
            enabled = permissionsButtonEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(permissionsButtonText)
        }
    }

    @Composable
    private fun ColumnScope.RecordButton(
        recordButtonEnabled: Boolean,
        recordButtonText: String
    ) {
        Button(
            onClick = {
                if (!recognizerApi.isInitialized()) return@Button
                if (recognizerApi.state == WORKING_MIC) {
                    recognizerApi.stopMic()
                    return@Button
                }
                if (recognizerApi.sttReadyToStart == true) {
                    recognizerApi.startMic()
                }
            },
            enabled = recordButtonEnabled,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(recordButtonText)
        }
    }

    private fun hasPermissions(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED
    }

    fun setTextToSpeechVoice() {
        // получаем список голосов русской локали
        var supportedVoices = SpeakerApi.getVoiceList()
            .sortedWith { v1: Voice, v2: Voice -> v1.compareTo(v2) }
            .filter {
                it.locale.language.startsWith("ru", true)
            }
        // нас интересуют голоса не требующие сети
        val offlineVoices = supportedVoices.filter { !it.isNetworkConnectionRequired }
        val currentVoice = SpeakerApi.currentVoice()
        if (supportedVoices.isEmpty() || currentVoice == null) {
            AlertDialog.Builder(this)
                .setTitle("Выбор голоса")
                .setMessage("Нет установленных голосов для русского языка")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val currentVoiceName = currentVoice.name
        // нас интересуют голоса не требующие сети (но если других русских нет и такие пойдут)
        if (offlineVoices.isNotEmpty()) supportedVoices = offlineVoices
        val voicesArray = arrayOfNulls<CharSequence>(supportedVoices.size)
        supportedVoices.forEachIndexed { index: Int, v: Voice ->
            voicesArray[index] = "Голос ${index + 1} ${v.name} "
        }
        AlertDialog.Builder(this)
            .setTitle("Текущий голос: $currentVoiceName")
            .setItems(voicesArray) { _, i ->
                SpeakerApi.setVoice(supportedVoices[i])
                SpeakerApi.speakPhrase("Выбран новый голос")
            }
            .setPositiveButton("Отмена", null)
            .create()
            .show()
    }

    /**
     * Освобождаем все ресурсы и много памяти при уничтожении активити
     * Повторное подключение к Api распознавания потребует нового вызова
     * SttApi.getRecognizerAsync(...)
     */
    override fun onDestroy() {

        runCatching {
            recognizerApi.stopMic()
            recognizerApi.releaseModels()
        }
        speakApi.release()
        super.onDestroy()
    }

    /**
     * при переключении из приложения останавливаем распознавание - все равно у приложения не во
     * фронте доступ к микрофону блокируется, если нет форграунд сервиса
     */
    override fun onPause() {
        if (recognizerApi.apiState.value == WORKING_MIC) recognizerApi.stopMic()
        super.onPause()

    }

}