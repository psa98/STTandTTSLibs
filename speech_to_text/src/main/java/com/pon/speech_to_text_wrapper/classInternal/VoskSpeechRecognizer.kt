package com.pon.speech_to_text_wrapper.classInternal

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioRecord
import android.media.AudioRecord.OnRecordPositionUpdateListener
import android.util.Log
import com.google.gson.Gson
import com.pon.speech_to_text_wrapper.SttClassApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException


private  const val MAX_WORDS_STORED = 1000
private  const val SAMPLE_FREQ = 16000f

internal object VoskSpeechRecognizer : RecognitionListener {
    private var rec: Recognizer? = null
    private var currentModel: Model? = null
    private var speechService: SpeechService? = null
    internal var state = SttClassApi.ApiState.CREATED_NOT_READY
        set(value) {
            field = value
            apiState.value = value
        }
    internal val recordTime: MutableStateFlow<Long> = MutableStateFlow(0L)
    private val gson = Gson()
    internal var recorder: AudioRecord? = null
    private var callOnInitError: (Exception) -> Unit = {}
    internal val allWords: MutableStateFlow<String> = MutableStateFlow("")
    internal val lastWords: MutableStateFlow<String> = MutableStateFlow("")
    internal val partialResult: MutableStateFlow<String> = MutableStateFlow("")
    internal val apiState: MutableStateFlow<SttClassApi.ApiState> =
        MutableStateFlow(SttClassApi.ApiState.CREATED_NOT_READY)
    private var seanceString = ""
        set(value) {
            val shortenedString =
                value.split(" ").toList().takeLast(MAX_WORDS_STORED).joinToString(" ")
             field = shortenedString
            allWords.value = shortenedString
        }

    internal fun prepare(
        appContext: Context,
        onVoskReady: () -> Unit,
        onInitError: (e: Exception) -> Unit,
    ) {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        StorageService.unpack(
            appContext, "model-ru-ru", "model",
            { model ->
                currentModel = model
                state = SttClassApi.ApiState.INITIALISED_READY
                onVoskReady()
            }
        ) { exception ->
            state = SttClassApi.ApiState.CREATED_NOT_READY
            onInitError(exception)
        }
    }


    override fun onResult(hypothesis: String?) {
        if (hypothesis == null || hypothesis.trim().isEmpty()) return
        val newWordsString = gson.fromJson(hypothesis, SttClassApi.SentenceResult::class.java).text
        if (newWordsString.trim().isEmpty()) return
        lastWords.value = newWordsString
        seanceString = "$seanceString $newWordsString"
    }

    override fun onFinalResult(hypothesis: String?) {
        state = SttClassApi.ApiState.FINISHED_AND_READY
        if (hypothesis == null || hypothesis.trim().isEmpty()) {
            lastWords.value = ""
            return
        }
        val sentenceResult = gson.fromJson(hypothesis, SttClassApi.SentenceResult::class.java)
        val newWordsString = sentenceResult.text
        if (newWordsString.trim().isEmpty()) {
            lastWords.value = ""
             return
        }
        lastWords.value = newWordsString
        seanceString = "$seanceString $newWordsString"
    }


    internal fun setPreferredDevice (device: AudioDeviceInfo){
        if (!device.isSource) return
        recorder?.preferredDevice = device
    }

    override fun onPartialResult(hypothesis: String) {
        val result = gson.fromJson(hypothesis, SttClassApi.PartialResult::class.java).partial
        if (result.isEmpty()) return
        partialResult.value = result
    }

    override fun onError(e: Exception) {
        callOnInitError(e)
        stop()
    }

    override fun onTimeout() {
        callOnInitError(IOException("Не удалось получить доступ к микрофону, таймаут"))
        stop()
    }

    @Throws(IOException::class)
    fun recognizeMic(onInitError: (Exception) -> Unit = {}) {
        if ((state == SttClassApi.ApiState.INITIALISED_READY || state == SttClassApi.ApiState.FINISHED_AND_READY) && currentModel != null) {
            try {
                callOnInitError = onInitError
                val sampleRate = SAMPLE_FREQ
                rec = Recognizer(currentModel, sampleRate)
                speechService = SpeechService(rec, sampleRate)
                speechService?.startListening(this)
                partialResult.value = ""
                lastWords.value = ""
                state = SttClassApi.ApiState.WORKING_MIC
                //получим доступ к внутреннему объекту recorder SpeechService
                // (к примеру для управления предпочитаемым устройством)

                val speechServiceClass = speechService!!.javaClass
                val recorderField = speechServiceClass.declaredFields.first { it.name == "recorder" }!!
                recorderField.isAccessible = true
                recordTime.value = 0
                recorder
                recorder = recorderField.get(speechService) as AudioRecord
                recorder?.positionNotificationPeriod = (SAMPLE_FREQ/10).toInt()
                recorder?.setRecordPositionUpdateListener(updateListener)

            } catch (e: Exception) {
                e.printStackTrace()
                recorder = null
                onInitError(e)
            }
        } else onInitError(
            IllegalStateException(
                "Ошибка: api должно быть в состоянии INITIALISED_READY или" +
                        " FINISHED_AND_READY. Библиотека не инициализирована или ее ресурсы уже освобождены"
            )
        )
    }

    fun pause(on: Boolean) {
        if (state == SttClassApi.ApiState.WORKING_MIC) {
            speechService?.setPause(on)
        }
    }

    fun stop() {
        if (state == SttClassApi.ApiState.WORKING_MIC) speechService?.stop()
        recorder?.setRecordPositionUpdateListener(null)
        recorder = null
        rec?.close()
        state = SttClassApi.ApiState.FINISHED_AND_READY
    }

    fun release() {
        if (state == SttClassApi.ApiState.CREATED_NOT_READY) return
        stop()
        seanceString = ""
        speechService?.shutdown()
        currentModel?.close()
        currentModel = null
        speechService = null
        recorder?.setRecordPositionUpdateListener(null)
        recorder = null
        rec = null
        state = SttClassApi.ApiState.CREATED_NOT_READY
    }


    private val updateListener: OnRecordPositionUpdateListener =
        object : OnRecordPositionUpdateListener {
            override fun onPeriodicNotification(recorder: AudioRecord) {
                recordTime.value +=100 //добавляем 100 мс 10 раз в секунду
            }
            override fun onMarkerReached(recorder: AudioRecord?) { }
        }
}