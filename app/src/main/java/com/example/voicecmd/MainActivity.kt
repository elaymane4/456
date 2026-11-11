package com.example.voicecmd

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var commands: CommandHandler
    private lateinit var tvHeard: TextView
    private lateinit var tvReply: TextView

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (text != null) process(text)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvHeard = findViewById(R.id.tvHeard)
        tvReply = findViewById(R.id.tvReply)
        commands = CommandHandler(this)
        tts = TextToSpeech(this, this)

        findViewById<Button>(R.id.btnMic).setOnClickListener { startListening() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-MA") // غيّرها إلى ar-SA أو ar-EG حسب لهجتك
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلم الآن...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "خدمة التعرف على الصوت غير متوفرة (ثبّت تطبيق Google)", Toast.LENGTH_LONG).show()
        }
    }

    private fun process(text: String) {
        tvHeard.text = "سمعتك: $text"
        val reply = commands.handle(text)
        tvReply.text = "الرد: $reply"
        tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale("ar")
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
