package com.pon.speech_to_text_wrapper

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.google.gson.annotations.SerializedName
import com.pon.speech_to_text_wrapper.SttClassApi.RecognizerAPI.sttInitialized
import com.pon.speech_to_text_wrapper.SttClassApi.RecognizerAPI.voskSpeechRecognizer
import com.pon.speech_to_text_wrapper.classInternal.AudioManagerApi
import com.pon.speech_to_text_wrapper.classInternal.VoskSpeechRecognizer

import kotlinx.coroutines.*

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


@Suppress("unused")
class SttClassApi {

    private var recognizer: RecognizerAPI = RecognizerAPI
    private var audioManager: AudioManagerApi = AudioManagerApi
    val state: ApiState
        get() = voskSpeechRecognizer.state
    var useBluetoothMic: Boolean = false
        set(value) {
            audioManager.bluetoothPriority = value
            field = value
            // при изменении этого параметра во время работы распознавания меняем микрофон
            if (state != ApiState.WORKING_MIC )return
            if (value) audioManager.turnScoOn()
            else audioManager.turnScoOff()
        }

    fun isInitialized() = recognizer.sttInitialized

    /**
     * Асинхронно инициализирует
     * Инициализация происходит только если распознаватель еще не был инициализирован.
     *
     * @param context контекст Android приложения
     * @param useBluetoothMic - использовать ли bluetooth микрофон при работе,
     * по умолчанию false - игнорировать микрофон. Параметр может быть переключен после
     * методом setUseBluetoothMic (...)
     * @return Deferred с объектом RecognizerAPI
     *
     * пример использования:
     *
     *            CoroutineScope(Dispatchers.IO).launch {
     *              val result = runCatching { SttApi.getRecognizerAsync(context).await() }
     *              result.onFailure {
     *                  it.printStackTrace()
     *                  recordButtonText = "Не удалось инициализировать API"
     *                  recordButtonEnabled = false
     *                  }
     *                  result.onSuccess {
     *                      recordButtonEnabled = true
     *                      recordButtonText = "Начать распознавание"
     *                      mainScope.launch {
     *                      it.allWords.collect {
     *                          currentText = it.toString()
     *                      }
     *                  }
     *              }
     *
     */
    fun initSTT(context: Context, useBluetoothMic: Boolean = false): Deferred<SttClassApi> {
        val deferred = CompletableDeferred<SttClassApi>()
        if (!audioManager.isInitialised) audioManager.init(
            context.applicationContext as Application,
            useBluetoothMic
        )
        this.useBluetoothMic = useBluetoothMic
        CoroutineScope(Dispatchers.IO).launch {
            if (recognizer.sttInitialized) {
                deferred.complete(this@SttClassApi)
            } else
                voskSpeechRecognizer.prepare(
                    context.applicationContext as Application,
                    onVoskReady = {
                        sttInitialized = true
                        deferred.complete(this@SttClassApi)
                    },
                    onInitError = {
                        sttInitialized = false
                        deferred.completeExceptionally(it)
                    })
        }
        return deferred
    }

    /**
     * Флоу с продолжительностью текущего сеанса записи в мс. Данные обновляются 10 раз в секунду,
     * сбрасываются в 0 при начале нового сеанса распознавания.
     */
    val recordTime: StateFlow<Long> = voskSpeechRecognizer.recordTime.asStateFlow()


    /**
     * Флоу с последними распознанными словами. Новые слова поступают в поток только после завершения
     * предложения (паузы в речи или вызова stop ()).
     */
    val lastWords: StateFlow<String> = voskSpeechRecognizer.lastWords.asStateFlow()

    /**
     * Флоу с последними распознанными словами. Строка состоит из всех слов распознанных в текущем
     * сеансе, с момента инициализации API. Новые слова поступают в поток после завершения предложения
     * и присоединяются в конец строки. Длина строки ограничена тысячей последних распознанных слов,
     * константный параметр MAX_WORDS_STORED.
     * Может использоваться для разбора длинных распознанных предложений и текстов
     */
    val allWords: StateFlow<String> =
        voskSpeechRecognizer.allWords.asStateFlow()

    /**
     * Флоу с частичными результатами распознавания. Слова поступают в поток по мере распознавания,
     * до завершения распознавания фразы. Слова могут уточняться по мере составления полной фразы        */
    val partialWords: StateFlow<String> =
        voskSpeechRecognizer.partialResult.asStateFlow()

    /**
     * Флоу  с текущим состоянием API распознавания.
     *  CREATED_NOT_READY,   // Создано, но не готово
     *  INITIALISED_READY,   // Инициализировано и готово
     *  WORKING_MIC,         // Распознает речь с микрофона
     *  FINISHED_AND_READY   // Завершило работу и готово к началу нового сеанса распознавания речи
     */
    val apiState: StateFlow<ApiState> = voskSpeechRecognizer.apiState.asStateFlow()


    /**
     * Запускает распознавание с микрофона.
     *
     * @param onError при указании лямбды, она вызывается при ошибке старта распознавания
     * @throws IllegalStateException если API не инициализировано
     */
    fun startMic(onError: (Exception) -> Unit = {}) {
        if (!sttInitialized) throw IllegalStateException("API не инициализировано")
        voskSpeechRecognizer.recognizeMic(onError)
        if (useBluetoothMic) audioManager.turnScoOn()
    }

    /**
     * Останавливает распознавание речи с микрофона.
     *
     * @throws IllegalStateException если API не инициализировано
     */
    fun stopMic() {
        if (!sttInitialized) throw IllegalStateException("API не инициализировано")
        voskSpeechRecognizer.stop()
        if (useBluetoothMic) audioManager.turnScoOff()
    }

    /**
     * Ставит распознавание с микрофона на паузу, если в данный момент идет распознавание с микрофона.
     *
     * @throws IllegalStateException если API не инициализировано
     */
    fun pauseMic() {
        if (!sttInitialized) throw IllegalStateException("API не инициализировано")
        voskSpeechRecognizer.pause(true)
    }

    /**
     * Снимает паузу с процессса распознавания с микрофона, если оно в данный момент временно
     * приостановлено вызовом pauseMic()
     *
     * @throws IllegalStateException если API не инициализировано
     */
    fun unpauseMic() {
        if (!sttInitialized) throw IllegalStateException("API не инициализировано")
        voskSpeechRecognizer.pause(false)
    }

    /**
     * Останавливает распознавание, освобождает ресурсы моделей распознавания речи.
     * Последущий вызов других методов API до его повторной инициализации через
     * getRecognizerAsync(...) будет вызывать исключение
     */
    fun releaseModels() {
        if (!sttInitialized) throw IllegalStateException("API не инициализировано")
        audioManager.turnScoOff()
        voskSpeechRecognizer.release()
        sttInitialized = false
    }

    /**
     * Проверка возможности вызова в данный момент метода startMic(...)
     */
    val sttReadyToStart: Boolean
        get() = state == ApiState.FINISHED_AND_READY || state == ApiState.INITIALISED_READY

    /**
     * Проверка, работает ли в данный  момент распознавание с микрофона.
     */
    val sttWorking: Boolean
        get() = state == ApiState.WORKING_MIC

    fun getMicrophones(): List<AudioDeviceInfo> {
        val am = audioManager.am
        if (am == null) throw IllegalStateException("Init api first")
        val list = am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        return list
    }

    fun isScoAvailable() =
        getMicrophones().firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }


    /**
     * Состояния API распознавания речи.
     */
    enum class ApiState {
        CREATED_NOT_READY,   // Создано, но не готово
        INITIALISED_READY,   // Инициализировано и готово
        WORKING_MIC,         // Распознает речь с микрофона
        FINISHED_AND_READY   // Завершило работу и готово к началу нового сеанса распознавания речи
    }

    internal object RecognizerAPI {
        internal var sttInitialized = false
        internal val voskSpeechRecognizer: VoskSpeechRecognizer = VoskSpeechRecognizer
        /**
         * Флоу с последними распознанными словами. Слова поступают в поток после завершения предложения -
         * после паузы в речи).
         */

    }

    internal class SentenceResult {
        @SerializedName("result")
        // более подробная информация о распознанных словах может быть включена в настройках Vosk
        // но пока не требуется
        var result: List<WordResult> = emptyList()

        @SerializedName("text")
        var text: String = ""
    }

    internal class WordResult {
        @SerializedName("conf")
        var conf = 0.0

        @SerializedName("end")
        var end = 0.0

        @SerializedName("start")
        var start = 0.0

        @SerializedName("word")
        var word: String = ""
    }

    internal class PartialResult {
        @SerializedName("partial")
        var partial = ""
    }
}
