package com.atom.aix
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val client=OkHttpClient.Builder().callTimeout(90,TimeUnit.SECONDS).build()
private fun ask(key:String,model:String,lines:List<ChatLine>):String {
 val contents=JSONArray()
 lines.takeLast(16).forEach{line->contents.put(JSONObject().put("role",if(line.role=="user") "user" else "model").put("parts",JSONArray().put(JSONObject().put("text",line.text))))}
 val payload=JSONObject().put("contents",contents)
 val request=Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent").header("x-goog-api-key",key).post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
 client.newCall(request).execute().use{response->
  val raw=response.body?.string().orEmpty()
  if(!response.isSuccessful) throw IllegalStateException("Gemini HTTP "+response.code+": "+raw.take(200))
  val parts=JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
  return buildString{if(parts!=null) for(i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())}.ifBlank{"لم يرجع النموذج إجابة نصية."}
 }
}

private fun askOllama(url:String, model:String, temperature:Float, lines:List<ChatLine>):String {
 val messages=JSONArray().put(JSONObject().put("role","system").put("content","أنت ATOM QUANTUM V6. أجب بدقة وبلغة المستخدم."))
 lines.takeLast(12).forEach { line ->
  messages.put(JSONObject().put("role",if(line.role=="user") "user" else "assistant").put("content",line.text))
 }
 val payload=JSONObject().put("model",model).put("messages",messages).put("stream",false)
  .put("options",JSONObject().put("temperature",temperature.toDouble()))
 val request=Request.Builder().url(url.trimEnd('/')+"/api/chat")
  .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
 client.newBuilder().callTimeout(300,TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
  val raw=response.body?.string().orEmpty()
  if(!response.isSuccessful) throw IllegalStateException("Ollama HTTP "+response.code+": "+raw.take(200))
  return JSONObject(raw).getJSONObject("message").getString("content").trim()
 }
}

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{AtomScreen()}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AtomScreen(){
 val context=LocalContext.current
 val tts=remember { TextToSpeech(context,null) }
 DisposableEffect(tts){onDispose{tts.stop();tts.shutdown()}}
 val prefs=remember{context.getSharedPreferences("atom_settings",Activity.MODE_PRIVATE)}
 var apiKey by remember{mutableStateOf(prefs.getString("gemini_key","").orEmpty())}
 var model by remember{mutableStateOf(prefs.getString("model","gemini-2.5-flash").orEmpty())}
 var engine by remember{mutableStateOf(prefs.getString("engine","Gemini").orEmpty())}
 var ollamaUrl by remember{mutableStateOf(prefs.getString("ollama_url","http://192.168.1.2:11434").orEmpty())}
 var ollamaModel by remember{mutableStateOf(prefs.getString("ollama_model","atom").orEmpty())}
 var temperature by remember{mutableFloatStateOf(prefs.getFloat("temperature",0.2f))}
 var uiLanguage by remember{mutableStateOf(prefs.getString("ui_language","ar").orEmpty())}
 var darkMode by remember{mutableStateOf(prefs.getBoolean("dark_mode",true))}
 var speechEnabled by remember{mutableStateOf(prefs.getBoolean("speech_enabled",true))}
 var speechRate by remember{mutableFloatStateOf(prefs.getFloat("speech_rate",1f))}
 var voiceLanguage by remember{mutableStateOf(prefs.getString("voice_language","ar").orEmpty())}
 var keepHistory by remember{mutableStateOf(prefs.getBoolean("keep_history",true))}
 var confirmClear by remember{mutableStateOf(false)}
 var tab by remember{mutableIntStateOf(0)}
 var input by remember{mutableStateOf("")}
 val voiceLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
  val recognized=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
  if(!recognized.isNullOrBlank()) input=recognized
 }

 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 val lines=remember{mutableStateListOf<ChatLine>()}
 val scope=rememberCoroutineScope()
 val db=remember{AtomDatabase(context)}
 var sessionId by remember{mutableLongStateOf(db.latestSessionId()?:db.createSession())}
 var version by remember{mutableIntStateOf(0)}
 LaunchedEffect(sessionId){lines.clear();lines.addAll(db.load(sessionId))}
 val tabs=listOf("محادثة","الرئيسية","لوحة العمل","الوكلاء","المعرفة","الأتمتة","المشاريع","الصوت","الاستخدام","الترحيب","الإعدادات")
 val listState=rememberLazyListState()
 LaunchedEffect(lines.size){if(lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)}
 MaterialTheme(colorScheme=if(darkMode) darkColorScheme(primary=Color(0xFFD4AF6A),onPrimary=Color(0xFF0B0D12),background=Color(0xFF0B0D12),surface=Color(0xFF12151C),onSurface=Color(0xFFEEF0F6),surfaceVariant=Color(0xFF1A1E28)) else lightColorScheme(primary=Color(0xFFA9812F),background=Color(0xFFF7F5F0),surface=Color.White,onSurface=Color(0xFF14161C))){
  CompositionLocalProvider(LocalLayoutDirection provides if(uiLanguage=="ar") LayoutDirection.Rtl else LayoutDirection.Ltr){
  Scaffold(topBar={TopAppBar(title={Text("ATOM AI X",fontWeight=FontWeight.SemiBold)},navigationIcon={TextButton(onClick={tab=if(tab==0) 10 else 0}){Text("☰",style=MaterialTheme.typography.headlineSmall)}},actions={TextButton(onClick={sessionId=db.createSession();version++;tab=0}){Text("✎",style=MaterialTheme.typography.headlineSmall)}})}){padding->
   Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=12.dp)){
    ScrollableTabRow(selectedTabIndex=tab){tabs.forEachIndexed{i,title->Tab(selected=tab==i,onClick={tab=i},text={Text(title)})}}
    when(tab){
     0->{
      var expanded by remember { mutableStateOf(false) }
      Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
       Box {
        TextButton(onClick={expanded=true}){Text("☰  المحادثات",color=MaterialTheme.colorScheme.onSurface)}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
         val history=remember(version){db.sessions()}
         history.forEach{(id,title)->DropdownMenuItem(text={Text(title+" #"+id)},onClick={sessionId=id;expanded=false})}
         DropdownMenuItem(text={Text("⚙️ الإعدادات")},onClick={tab=10;expanded=false})
         DropdownMenuItem(text={Text("📄 لوحة العمل")},onClick={tab=2;expanded=false})
        }
       }
       Spacer(Modifier.weight(1f))
       Text(engine+" ▾",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(end=12.dp))
      }
      if(lines.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
       Column(horizontalAlignment=Alignment.CenterHorizontally){
        Text("⚛",style=MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        Text(if(uiLanguage=="ar") "كيف يمكنني مساعدتك؟" else "How can I help?",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
       }
      } else LazyColumn(state=listState,modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=16.dp)){
       items(lines){line->
        if(line.role=="user"){
         Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.End){
          Text(line.text,modifier=Modifier.widthIn(max=310.dp).background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(22.dp)).padding(14.dp),color=MaterialTheme.colorScheme.onSurface)
         }
        } else {
         Column(Modifier.fillMaxWidth().padding(vertical=12.dp)){
          Text("⚛ ATOM",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
          Spacer(Modifier.height(8.dp))
          Text(line.text,color=MaterialTheme.colorScheme.onSurface)
          if(speechEnabled) TextButton(onClick={tts.language=Locale.forLanguageTag(voiceLanguage);tts.setSpeechRate(speechRate);tts.speak(line.text,TextToSpeech.QUEUE_FLUSH,null,"atom_reply")}){Text(if(uiLanguage=="ar") "🔊 استمع للرد" else "🔊 Listen")}
         }
        }
       }
      }
      if(busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
      if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
      if(engine=="Gemini"&&apiKey.isBlank()) TextButton(onClick={tab=10}){Text("أضف مفتاح Gemini API من الإعدادات")}
      Row(Modifier.fillMaxWidth().padding(bottom=8.dp,top=6.dp),verticalAlignment=Alignment.Bottom){
       TextButton(onClick={tab=2},modifier=Modifier.padding(end=2.dp)){Text("+",style=MaterialTheme.typography.headlineMedium)}
       OutlinedTextField(value=input,onValueChange={input=it},modifier=Modifier.weight(1f),placeholder={Text(if(uiLanguage=="ar") "اسأل ATOM AI X" else "Ask ATOM AI X")},shape=RoundedCornerShape(28.dp),maxLines=5)
       TextButton(onClick={
        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
         putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
         putExtra(RecognizerIntent.EXTRA_LANGUAGE,voiceLanguage)
         putExtra(RecognizerIntent.EXTRA_PROMPT,"تحدث إلى ATOM AI X")
        }
        try{voiceLauncher.launch(intent)}catch(e:Exception){error="التعرف الصوتي غير متاح على هذا الجهاز"}
       }){Text("🎙️")}
       Spacer(Modifier.width(6.dp))
       FilledIconButton(enabled=!busy&&input.isNotBlank()&&(engine=="Ollama"||apiKey.isNotBlank()),onClick={
        val message=input.trim();input="";error=""
        val userLine=ChatLine("user",message);lines.add(userLine);if(keepHistory) db.add(sessionId,userLine);busy=true
        val snapshot=lines.toList()
        scope.launch{
         try{
          val answer=withContext(Dispatchers.IO){if(engine=="Ollama") askOllama(ollamaUrl,ollamaModel,temperature,snapshot) else ask(apiKey,model,snapshot)}
          val line=ChatLine("model",answer);lines.add(line);if(keepHistory) db.add(sessionId,line)
         }catch(e:Exception){error=e.message?:"تعذر الاتصال"}
         finally{busy=false}
        }
       }){Text("↑",style=MaterialTheme.typography.titleLarge)}
      }
     }
     10->{
      val ar=uiLanguage=="ar"
      Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical=12.dp)){
       Text(if(ar) "الإعدادات العامة" else "General settings",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
       Spacer(Modifier.height(14.dp))
       Text(if(ar) "اللغة" else "Language",fontWeight=FontWeight.Bold)
       Row(verticalAlignment=Alignment.CenterVertically){
        listOf("ar" to "العربية","en" to "English").forEach{(code,label)->
         RadioButton(selected=uiLanguage==code,onClick={uiLanguage=code;prefs.edit().putString("ui_language",code).apply()})
         Text(label)
        }
       }
       Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
        Text(if(ar) "الوضع الليلي" else "Dark mode",modifier=Modifier.weight(1f))
        Switch(checked=darkMode,onCheckedChange={darkMode=it;prefs.edit().putBoolean("dark_mode",it).apply()})
       }
       HorizontalDivider()
       Spacer(Modifier.height(12.dp))
       Text(if(ar) "محرك الذكاء الاصطناعي" else "AI engine",fontWeight=FontWeight.Bold)
       Row(verticalAlignment=Alignment.CenterVertically){
        listOf("Gemini","Ollama").forEach{candidate->
         RadioButton(selected=engine==candidate,onClick={engine=candidate;prefs.edit().putString("engine",candidate).apply()})
         Text(candidate)
        }
       }
       if(engine=="Ollama"){
        OutlinedTextField(value=ollamaUrl,onValueChange={ollamaUrl=it;prefs.edit().putString("ollama_url",it).apply()},modifier=Modifier.fillMaxWidth(),label={Text("Ollama server URL")})
        OutlinedTextField(value=ollamaModel,onValueChange={ollamaModel=it;prefs.edit().putString("ollama_model",it).apply()},modifier=Modifier.fillMaxWidth(),label={Text("Ollama model")})
        Text("Temperature: "+temperature)
        Slider(value=temperature,onValueChange={temperature=it;prefs.edit().putFloat("temperature",it).apply()},valueRange=0f..1.5f)
       }else{
        OutlinedTextField(value=apiKey,onValueChange={apiKey=it;prefs.edit().putString("gemini_key",it).apply()},modifier=Modifier.fillMaxWidth(),label={Text("Gemini API Key")},visualTransformation=PasswordVisualTransformation())
        listOf("gemini-2.5-flash","gemini-2.5-pro").forEach{candidate->
         Row(verticalAlignment=Alignment.CenterVertically){
          RadioButton(selected=model==candidate,onClick={model=candidate;prefs.edit().putString("model",candidate).apply()})
          Text(candidate)
         }
        }
       }
       HorizontalDivider()
       Spacer(Modifier.height(12.dp))
       Text(if(ar) "الصوت" else "Voice",fontWeight=FontWeight.Bold)
       Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
        Text(if(ar) "إظهار زر قراءة الرد" else "Show spoken replies",modifier=Modifier.weight(1f))
        Switch(checked=speechEnabled,onCheckedChange={speechEnabled=it;prefs.edit().putBoolean("speech_enabled",it).apply()})
       }
       Text(if(ar) "سرعة النطق" else "Speech speed")
       Slider(value=speechRate,onValueChange={speechRate=it;prefs.edit().putFloat("speech_rate",it).apply()},valueRange=0.5f..1.5f)
       Row(verticalAlignment=Alignment.CenterVertically){
        listOf("ar" to "عربي","en" to "English").forEach{(code,label)->
         RadioButton(selected=voiceLanguage==code,onClick={voiceLanguage=code;prefs.edit().putString("voice_language",code).apply()})
         Text(label)
        }
       }
       Text(if(ar) "التعرف الصوتي يعمل عند الضغط على الميكروفون فقط." else "Speech recognition starts only when you tap the microphone.",style=MaterialTheme.typography.bodySmall)
       HorizontalDivider()
       Spacer(Modifier.height(12.dp))
       Text(if(ar) "الخصوصية والذاكرة" else "Privacy and memory",fontWeight=FontWeight.Bold)
       OutlinedButton(onClick={tab=11}){Text("إدارة الذاكرة المحلية") }
       Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
        Text(if(ar) "حفظ سجل المحادثات محليًا" else "Save local chat history",modifier=Modifier.weight(1f))
        Switch(checked=keepHistory,onCheckedChange={keepHistory=it;prefs.edit().putBoolean("keep_history",it).apply()})
       }
       Text(if(ar) "عند إيقاف الحفظ تبقى الرسائل الحالية على الشاشة فقط، ولا تُحذف المحادثات السابقة." else "Disabling history stops saving new messages; old chats remain until deleted.",style=MaterialTheme.typography.bodySmall)
       Spacer(Modifier.height(8.dp))
       OutlinedButton(onClick={confirmClear=true}){Text(if(ar) "حذف جميع المحادثات المحفوظة" else "Delete all saved conversations")}
       if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text(if(ar) "تأكيد الحذف" else "Confirm deletion")},text={Text(if(ar) "سيتم حذف كل المحادثات من هذا الجهاز نهائيًا." else "All local conversations will be permanently deleted.")},confirmButton={
        TextButton(onClick={db.clearAll();sessionId=db.createSession();lines.clear();version++;confirmClear=false}){Text(if(ar) "حذف" else "Delete")}
       },dismissButton={TextButton(onClick={confirmClear=false}){Text(if(ar) "إلغاء" else "Cancel")}})
       Text(if(ar) "يتم إرسال الرسائل إلى مزود الذكاء المختار. مفتاح Gemini محفوظ في تفضيلات التطبيق، وليس مشفرًا تشفيرًا مخصصًا." else "Messages go to the selected AI provider. The Gemini key is stored in app preferences without dedicated encryption.",style=MaterialTheme.typography.bodySmall)
       Spacer(Modifier.height(24.dp))
      }
     }
     1->{WorkspaceHome(onNavigate={tab=it})}
     2->{LocalDocumentWorkspace()}
     3->{LocalAgentWorkspace()}
     4->{Column(Modifier.fillMaxSize()){Row{TextButton(onClick={tab=12}){Text("بحث بالمصادر")};TextButton(onClick={tab=13}){Text("أدوات الحساب")}};AdvancedDocumentWorkspace()}}
     5->{LocalAutomationWorkspace()}
     6->{Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())){LocalProjectsWorkspace();HorizontalDivider();ChatHistoryManager(db,onOpen={sessionId=it;tab=0},onChanged={version++;if(db.sessions().none{it.first==sessionId})sessionId=db.latestSessionId()?:db.createSession()})}}
     7->{VoiceConversationWorkspace()}
     8->{LocalUsageWorkspace()}
     9->{WorkspaceHome(onNavigate={tab=it})}
     11->{LocalMemoryWorkspace()}
     12->{LocalResearchWorkspace()}
     13->{LocalToolsWorkspace()}
     else->{UnavailableSection("غير متاح","هذا القسم غير متاح حاليًا.")}
    }
   }
  }
  }
 }
}
