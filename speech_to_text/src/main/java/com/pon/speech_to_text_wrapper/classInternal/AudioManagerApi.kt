package com.pon.speech_to_text_wrapper.classInternal

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context.AUDIO_SERVICE
import android.content.Context.RECEIVER_EXPORTED
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log


private const val TAG = "AudioManager"

@Suppress("DEPRECATION")
object AudioManagerApi {
    internal var am: AudioManager? = null
    private val receiver by lazy { BlueToothSCOConnectionReceiver().also { it.am = am } }
    var bluetoothPriority = false
        set(value) {
            if (field != value) {
                field = value
                context?.let {
                    if (value) {
                        startScoReceiver(it)
                        turnScoOn()
                    } else {
                        stopReceiver()
                        turnScoOff()
                    }
                }
            }

        }
    var isInitialised = false
    private var context: Application? = null
    private val intentFilter = IntentFilter().apply {
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED) // catches bluetooth ON/OFF (the major case)
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED) // catches when the actual bt device connects.
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
    }

    fun init(context: Application, bluetoothPriority: Boolean = false) {
        if (isInitialised) return
        isInitialised = true
        AudioManagerApi.context = context
        am = context.getSystemService(AUDIO_SERVICE) as AudioManager
        this.bluetoothPriority = bluetoothPriority
        if (bluetoothPriority) startScoReceiver(context)
    }

    /**
     * Запуск бродкаст ресивера, отслеживающего подключение и отключение аудио блютуз устройств
     * Пока внутри ресивера нет бизнес логики, задачи библиотеки реализуются и без нее
     */
    private fun startScoReceiver(applicationContext: Application) {
        if (Build.VERSION.SDK_INT > 33) applicationContext.registerReceiver(
            receiver,
            intentFilter,
            RECEIVER_EXPORTED
        )
        else applicationContext.registerReceiver(receiver, intentFilter)
    }


    /**
     * останов бродкаст ресивера, отслеживающего подключение и отключение аудио блютуз устройств
     * Вызывается так же при очистке и освобождении реcурсов  Api STT
     */
    private fun stopReceiver() {
        runCatching {  context?.unregisterReceiver(receiver)}
    }


    fun scoState() = am?.isBluetoothScoOn == true

    /**
     * Переводим основное (или единственное) блютуз устройство с параметрами гарнитуры/спикерфона
     * в режим работы приложения с его микрофоном.
     */
    fun turnScoOn() {
        // Метод задепрекейчен, но рекомендованный новый что то не заработал, смотри вариант кода ниже.
        // Возможно метод перестанет работать на sdk 35 или 36, тогда надо будет решать эту проблему
        bluetoothPriority = true
        context?.let {
            stopReceiver()
            startScoReceiver(it)
        }
        am?.startBluetoothSco()
        Log.i(TAG, "turnScoOn")
    }
    /*     рекомендованный, но, по итогам тестов, не работающий пока вариант для sdk >=34
         if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val devInfo = am.getDevices(GET_DEVICES_INPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            devInfo?.let {
                am.setCommunicationDevice(it)
            }

        } else
            am.startBluetoothSco()
    }
     */


    @Suppress("DEPRECATION")
    /**
     * Отключаем микрофон как микрофон по умолчанию для приложения для основного  (или единственного)
     * блютуз устройство с параметрами  гарнитуры/спикерфона. Вывод звука по умолчанию на это
     * устройство может быть продолжен до его отключения
     */
    fun turnScoOff() {
        runCatching {  context?.unregisterReceiver(receiver)}
        bluetoothPriority = false
        // Метод задепрекейчен, но рекомендованный новый что не заработал, смотри вариант кода ниже
        // возможно метод перестанет работать на sdk 35 или 36, тогда надо будет решать эту проблему
        am?.stopBluetoothSco()
        Log.i(TAG, "turnScoOff")
    }

}



