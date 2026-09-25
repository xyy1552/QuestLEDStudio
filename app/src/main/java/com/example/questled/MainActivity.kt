package com.example.questled

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.Socket
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private var questIp = ""
    private var connectPort = 5555
    private var effectJob: Job? = null

    private var currentR = 255
    private var currentG = 0
    private var currentB = 255
    private var masterBrightness = 1.0f
    private var fxDelayMs = 50L

    private lateinit var tvStatus: TextView
    private lateinit var vLivePreview: View
    private lateinit var tvRgbCode: TextView
    private lateinit var tvRedVal: TextView
    private lateinit var tvGreenVal: TextView
    private lateinit var tvBlueVal: TextView
    private lateinit var tvSpeedVal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind Connections UI
        val etIp = findViewById<EditText>(R.id.etIp)
        val etPort = findViewById<EditText>(R.id.etPort)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)

        // Bind Preview & Sliders UI
        vLivePreview = findViewById(R.id.vLivePreview)
        tvRgbCode = findViewById(R.id.tvRgbCode)
        tvRedVal = findViewById(R.id.tvRedVal)
        tvGreenVal = findViewById(R.id.tvGreenVal)
        tvBlueVal = findViewById(R.id.tvBlueVal)
        tvSpeedVal = findViewById(R.id.tvSpeedVal)

        val switchPower = findViewById<Switch>(R.id.switchPower)
        val seekMasterBrightness = findViewById<SeekBar>(R.id.seekMasterBrightness)
        val seekSpeed = findViewById<SeekBar>(R.id.seekSpeed)
        val seekRed = findViewById<SeekBar>(R.id.seekRed)
        val seekGreen = findViewById<SeekBar>(R.id.seekGreen)
        val seekBlue = findViewById<SeekBar>(R.id.seekBlue)

        // 1. Connect Button Action
        btnConnect.setOnClickListener {
            questIp = etIp.text.toString().trim()
            connectPort = etPort.text.toString().trim().toIntOrNull() ?: 5555

            if (questIp.isEmpty()) {
                Toast.makeText(this, "Enter Quest IP Address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val socket = Socket(questIp, connectPort)
                    socket.close()
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Status: Target Connected ($questIp:$connectPort)"
                        tvStatus.setTextColor(Color.parseColor("#00E676"))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Error: ${e.localizedMessage}"
                        tvStatus.setTextColor(Color.parseColor("#FF5252"))
                    }
                }
            }
        }

        // 2. Power Toggle
        switchPower.setOnCheckedChangeListener { _, isChecked ->
            stopEffect()
            if (isChecked) {
                sendRgbToQuest(currentR, currentG, currentB)
            } else {
                sendRgbToQuest(0, 0, 0)
            }
        }

        // 3. Brightness & Speed Seekbars
        seekMasterBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                masterBrightness = progress / 100f
                if (switchPower.isChecked && effectJob == null) {
                    sendRgbToQuest(currentR, currentG, currentB)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                fxDelayMs = progress.coerceAtLeast(10).toLong()
                tvSpeedVal.text = "FX Animation Speed: ${fxDelayMs}ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 4. Color Seekbars
        val rgbListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                stopEffect()
                currentR = seekRed.progress
                currentG = seekGreen.progress
                currentB = seekBlue.progress

                tvRedVal.text = "Red Channel: $currentR"
                tvGreenVal.text = "Green Channel: $currentG"
                tvBlueVal.text = "Blue Channel: $currentB"

                if (switchPower.isChecked) {
                    sendRgbToQuest(currentR, currentG, currentB)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekRed.setOnSeekBarChangeListener(rgbListener)
        seekGreen.setOnSeekBarChangeListener(rgbListener)
        seekBlue.setOnSeekBarChangeListener(rgbListener)

        // 5. Preset Color Buttons Setup
        val presetButtons = mapOf(
            R.id.btnColorRed to intArrayOf(255, 0, 0),
            R.id.btnColorGreen to intArrayOf(0, 255, 0),
            R.id.btnColorBlue to intArrayOf(0, 0, 255),
            R.id.btnColorCyan to intArrayOf(0, 255, 255),
            R.id.btnColorMagenta to intArrayOf(255, 0, 255),
            R.id.btnColorYellow to intArrayOf(255, 255, 0),
            R.id.btnColorOrange to intArrayOf(255, 128, 0),
            R.id.btnColorWhite to intArrayOf(255, 255, 255)
        )

        presetButtons.forEach { (btnId, rgb) ->
            findViewById<Button>(btnId).setOnClickListener {
                stopEffect()
                currentR = rgb[0]
                currentG = rgb[1]
                currentB = rgb[2]

                seekRed.progress = currentR
                seekGreen.progress = currentG
                seekBlue.progress = currentB

                if (switchPower.isChecked) {
                    sendRgbToQuest(currentR, currentG, currentB)
                }
            }
        }

        // 6. FX Animation Engines Setup
        findViewById<Button>(R.id.btnFxRainbow).setOnClickListener {
            startEffect {
                var hue = 0f
                while (coroutineContext.isActive) {
                    val color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                    sendRgbToQuest(Color.red(color), Color.green(color), Color.blue(color))
                    hue = (hue + 5f) % 360f
                    delay(fxDelayMs)
                }
            }
        }

        findViewById<Button>(R.id.btnFxChristmas).setOnClickListener {
            startEffect {
                while (coroutineContext.isActive) {
                    sendRgbToQuest(255, 0, 0)
                    delay(fxDelayMs * 10)
                    sendRgbToQuest(0, 255, 0)
                    delay(fxDelayMs * 10)
                }
            }
        }

        findViewById<Button>(R.id.btnFxPoliceSiren).setOnClickListener {
            startEffect {
                while (coroutineContext.isActive) {
                    sendRgbToQuest(255, 0, 0)
                    delay(fxDelayMs * 4)
                    sendRgbToQuest(0, 0, 255)
                    delay(fxDelayMs * 4)
                }
            }
        }

        findViewById<Button>(R.id.btnFxPoliceStrobe).setOnClickListener {
            startEffect {
                while (coroutineContext.isActive) {
                    repeat(3) {
                        sendRgbToQuest(255, 0, 0)
                        delay(60)
                        sendRgbToQuest(0, 0, 0)
                        delay(60)
                    }
                    repeat(3) {
                        sendRgbToQuest(0, 0, 255)
                        delay(60)
                        sendRgbToQuest(0, 0, 0)
                        delay(60)
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnFxPulse).setOnClickListener {
            startEffect {
                var factor = 0f
                var step = 0.05f
                while (coroutineContext.isActive) {
                    val r = (currentR * factor).toInt()
                    val g = (currentG * factor).toInt()
                    val b = (currentB * factor).toInt()
                    sendRgbToQuest(r, g, b)

                    factor += step
                    if (factor >= 1.0f || factor <= 0.0f) step = -step
                    delay(fxDelayMs)
                }
            }
        }

        findViewById<Button>(R.id.btnFxFire).setOnClickListener {
            startEffect {
                while (coroutineContext.isActive) {
                    val r = Random.nextInt(200, 256)
                    val g = Random.nextInt(20, 90)
                    val b = 0
                    sendRgbToQuest(r, g, b)
                    delay(Random.nextLong(40, 120))
                }
            }
        }

        findViewById<Button>(R.id.btnFxDisco).setOnClickListener {
            startEffect {
                while (coroutineContext.isActive) {
                    sendRgbToQuest(Random.nextInt(0, 256), Random.nextInt(0, 256), Random.nextInt(0, 256))
                    delay(fxDelayMs * 2)
                }
            }
        }

        findViewById<Button>(R.id.btnFxCustomStrobe).setOnClickListener {
            startEffect {
                while (coroutineContext.isActive) {
                    sendRgbToQuest(currentR, currentG, currentB)
                    delay(fxDelayMs * 5)
                    sendRgbToQuest(0, 0, 0)
                    delay(fxDelayMs * 5)
                }
            }
        }

        findViewById<Button>(R.id.btnStopFx).setOnClickListener {
            stopEffect()
            sendRgbToQuest(0, 0, 0)
        }

        // Initialize display
        updateLiveUiPreview(255, 0, 255)
    }

    // Direct Socket Dispatcher
    private fun sendRgbToQuest(r: Int, g: Int, b: Int) {
        val finalR = (r * masterBrightness).toInt().coerceIn(0, 255)
        val finalG = (g * masterBrightness).toInt().coerceIn(0, 255)
        val finalB = (b * masterBrightness).toInt().coerceIn(0, 255)

        runOnUiThread { updateLiveUiPreview(finalR, finalG, finalB) }

        if (questIp.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket(questIp, connectPort)
                val out: OutputStream = socket.getOutputStream()
                val cmd = "su -c 'echo $finalR > /sys/class/leds/red/brightness; " +
                          "echo $finalG > /sys/class/leds/green/brightness; " +
                          "echo $finalB > /sys/class/leds/blue/brightness'\n"
                out.write(cmd.toByteArray())
                out.flush()
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateLiveUiPreview(r: Int, g: Int, b: Int) {
        val colorInt = Color.rgb(r, g, b)
        val hex = String.format("#%02X%02X%02X", r, g, b)

        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorInt)
            setStroke(8, Color.parseColor("#00E5FF"))
        }

        vLivePreview.background = shape
        tvRgbCode.text = "RGB($r, $g, $b) | $hex"
        tvRgbCode.setTextColor(if (r + g + b < 100) Color.WHITE else colorInt)
    }

    private fun startEffect(block: suspend () -> Unit) {
        stopEffect()
        effectJob = CoroutineScope(Dispatchers.IO).launch { block() }
    }

    private fun stopEffect() {
        effectJob?.cancel()
        effectJob = null
    }
}
