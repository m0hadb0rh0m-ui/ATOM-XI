package com.atom.aix

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun VoiceConversationWorkspace(){
 val context=LocalContext.current
 var transcript by remember{mutableStateOf("")}
 var status by remember{mutableStateOf("جاهز")}
 var language by remember{mutableStateOf("ar")}
 var rate by remember{mutableFloatStateOf(1f)}
 val tts=remember{TextToSpeech(context){ }}
 DisposableEffect(tts){onDispose{tts.stop();tts.shutdown()}}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
  val text=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
  if(text.isNullOrBlank())status="لم يتم التقاط نص." else{transcript=if(transcript.isBlank())text else transcript+"\n"+text;status="تم تحويل الصوت إلى نص."}
 }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("مساحة المحادثة الصوتية",style=MaterialTheme.typography.headlineMedium)
  Text("إملاء صوتي متتابع مع تحويل الكلام إلى نص، وقراءة النص بصوت مسموع. يعتمد التعرف على خدمة الصوت الموجودة بالجهاز.")
  Row{
   listOf("ar" to "العربية","en" to "English").forEach{(code,label)->
    RadioButton(selected=language==code,onClick={language=code});Text(label,modifier=Modifier.padding(top=12.dp))
   }
  }
  Button(onClick={
   val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE,language)
    putExtra(RecognizerIntent.EXTRA_PROMPT,"تحدث الآن")
   }
   try{launcher.launch(intent);status="في انتظار نتيجة التعرف الصوتي"}catch(e:Exception){status="خدمة التعرف الصوتي غير متاحة على الجهاز"}
  }){Text("🎙️ ابدأ الإملاء الصوتي")}
  OutlinedTextField(value=transcript,onValueChange={transcript=it},modifier=Modifier.fillMaxWidth().heightIn(min=180.dp),label={Text("النص المتحدث أو المكتوب")})
  Text("سرعة النطق: "+String.format(Locale.US,"%.1f",rate))
  Slider(value=rate,onValueChange={rate=it},valueRange=0.5f..1.5f)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Button(enabled=transcript.isNotBlank(),onClick={
    val available=tts.setLanguage(Locale.forLanguageTag(language))
    if(available==TextToSpeech.LANG_MISSING_DATA||available==TextToSpeech.LANG_NOT_SUPPORTED)status="لغة النطق غير مدعومة."
    else{tts.setSpeechRate(rate);tts.speak(transcript,TextToSpeech.QUEUE_FLUSH,null,"atom_voice");status="جارٍ النطق"}
   }){Text("▶️ استمع")}
   OutlinedButton(onClick={tts.stop();status="توقف النطق"}){Text("⏹ إيقاف")}
   TextButton(onClick={transcript="";tts.stop();status="تم المسح"}){Text("مسح")}
  }
  Text(status)
  Text("هذا وضع إملاء ونطق تفاعلي، وليس اتصالًا صوتيًا مباشرًا مستمرًا مع نموذج AI. ذلك يتطلب خدمة بث صوتي في المرحلة السحابية.",style=MaterialTheme.typography.bodySmall)
 }
}
