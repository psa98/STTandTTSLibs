package com.pon.speech_to_text_wrapper.classInternal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE
import android.util.Log

class BlueToothSCOConnectionReceiver : BroadcastReceiver() {

    /*
    * это заготовка бродкаст ресивера на будущее.
    * По итогам ограниченных тестов на нескольких устройствах динамическое отслеживание соединения
    * с устройством не требуется, единственного вызова startBluetoothSco() при инициализации
    * SttApi вполне достаточно для полноценной работы с блютуз спикерфоном как входным микрофоном.
    * Как выходной динамик для TTS он подключается автоматически
    * В случае появления конкретных проблем с отдельными устрйствами или на sdk 35-36 мы их
    * появления
     */
    internal var am: AudioManager? = null

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED == intent.action) {
            val state = intent.getIntExtra(
                AudioManager.EXTRA_SCO_AUDIO_STATE,
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED
            )
            val prevState = intent.getIntExtra(
                EXTRA_SCO_AUDIO_PREVIOUS_STATE,
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED
            )
            when (state) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                    Log.i("ScoReceiver!!!", "SCO Audio Connected")
                }

                AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                    Log.i("ScoReceiver!!!", "SCO Audio Connecting")
                }

                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                    Log.i("ScoReceiver!!!", "SCO Audio Disconnected")
                    /*
                    отключение устройства физически, кнопкой, почему то не вызывает этого интента,
                    однако, неудачное нажатие кнопки "телефона" вверху устройства вызывает временное
                    разъединение (до следующего запроса данных для распознавания с микрофона).
                    Вероятно эту кнопку у устройства в кабине  следует заблокировать
                    накладкой или изолентой, как и выключение микрофона
                    */
                }

                AudioManager.SCO_AUDIO_STATE_ERROR -> {
                    Log.i("ScoReceiver!!!", "SCO Audio Error")
                }
            }
        }

    }


}