package com.atom.aix
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
 val prefs=remember{context.getSharedPreferences("atom_settings",Activity.MODE_PRIVATE)}
 var apiKey by remember{mutableStateOf(prefs.getString("gemini_key","").orEmpty())}
 var model by remember{mutableStateOf(prefs.getString("model","gemini-2.5-flash").orEmpty())}
 var engine by remember{mutableStateOf(prefs.getString("engine","Gemini").orEmpty())}
 var ollamaUrl by remember{mutableStateOf(prefs.getString("ollama_url","http://192.168.1.2:11434").orEmpty())}
 var ollamaModel by remember{mutableStateOf(prefs.getString("ollama_model","atom").orEmpty())}
 var temperature by remember{mutableFloatStateOf(prefs.getFloat("temperature",0.2f))}
 var tab by remember{mutableIntStateOf(0)}
 var input by remember{mutableStateOf("")}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 val lines=remember{mutableStateListOf<ChatLine>()}
 val scope=rememberCoroutineScope()
 val db=remember{AtomDatabase(context)}
 var sessionId by remember{mutableLongStateOf(db.latestSessionId()?:db.createSession())}
 var version by remember{mutableIntStateOf(0)}
 LaunchedEffect(sessionId){lines.clear();lines.addAll(db.load(sessionId))}
 val tabs=listOf("محادثة","3D","صور","فيديو","إعدادات")
 MaterialTheme(colorScheme=darkColorScheme()){
  Scaffold(topBar={TopAppBar(title={Text("⚛️ ATOM AI X")})}){padding->
   Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=12.dp)){
    ScrollableTabRow(selectedTabIndex=tab){tabs.forEachIndexed{i,title->Tab(selected=tab==i,onClick={tab=i},text={Text(title)})}}
    when(tab){
     0->{
      Row(verticalAlignment=Alignment.CenterVertically){
       Button(onClick={sessionId=db.createSession();version++}){Text("+ جلسة")}
       Spacer(Modifier.width(8.dp))
       var expanded by remember{mutableStateOf(false)}
       Box{
        OutlinedButton(onClick={expanded=true}){Text("السجل")}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
         val history=remember(version){db.sessions()}
         history.forEach{(id,title)->DropdownMenuItem(text={Text(title+" #"+id)},onClick={sessionId=id;expanded=false})}
        }
       }
      }
      LazyColumn(Modifier.weight(1f).fillMaxWidth()){
       items(lines){line->Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){
        Column(Modifier.padding(12.dp)){Text(if(line.role=="user") "أنت" else "ATOM AI X",style=MaterialTheme.typography.labelMedium);Text(line.text)}
       }}
      }
      if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
      OutlinedTextField(value=input,onValueChange={input=it},modifier=Modifier.fillMaxWidth(),label={Text("اكتب رسالتك")})
      Button(enabled=!busy&&input.isNotBlank()&&(engine=="Ollama"||apiKey.isNotBlank()),modifier=Modifier.fillMaxWidth(),onClick={
       val message=input.trim();input="";error=""
       val userLine=ChatLine("user",message);lines.add(userLine);db.add(sessionId,userLine);busy=true
       val snapshot=lines.toList()
       scope.launch{
        try{val answer=withContext(Dispatchers.IO){if(engine=="Ollama") askOllama(ollamaUrl,ollamaModel,temperature,snapshot) else ask(apiKey,model,snapshot)};val line=ChatLine("model",answer);lines.add(line);db.add(sessionId,line)}
        catch(e:Exception){error=e.message?:"تعذر الاتصال"}
        finally{busy=false}
       }
      }){Text(if(busy) "جارٍ الإرسال..." else "إرسال")}
      if(engine=="Gemini"&&apiKey.isBlank()) Text("أضف مفتاح Gemini API من الإعدادات")
     }
     4->{
      Spacer(Modifier.height(16.dp))
      Text("محرك الذكاء الاصطناعي")
      Row(verticalAlignment=Alignment.CenterVertically) {
       listOf("Gemini","Ollama").forEach { candidate ->
        RadioButton(selected=engine==candidate,onClick={engine=candidate;prefs.edit().putString("engine",candidate).apply()})
        Text(candidate)
       }
      }
      if(engine=="Ollama") {
       OutlinedTextField(value=ollamaUrl,onValueChange={ollamaUrl=it;prefs.edit().putString("ollama_url",it).apply()},modifier=Modifier.fillMaxWidth(),label={Text("Ollama server URL (IP:11434)")})
       OutlinedTextField(value=ollamaModel,onValueChange={ollamaModel=it;prefs.edit().putString("ollama_model",it).apply()},modifier=Modifier.fillMaxWidth(),label={Text("Ollama model (atom / qwen2.5-coder:7b)")})
       Text("Temperature: "+temperature)
       Slider(value=temperature,onValueChange={temperature=it;prefs.edit().putFloat("temperature",it).apply()},valueRange=0f..1.5f)
       Text("Ollama يحتاج خادمًا يعمل على جهاز آخر، ولا يعمل localhost الخاص بالكمبيوتر مباشرة من الهاتف.")
      }

      OutlinedTextField(value=apiKey,onValueChange={apiKey=it;prefs.edit().putString("gemini_key",it).apply()},modifier=Modifier.fillMaxWidth(),label={Text("Gemini API Key")},visualTransformation=PasswordVisualTransformation())
      listOf("gemini-2.5-flash","gemini-2.5-pro").forEach{candidate->
       Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=model==candidate,onClick={model=candidate;prefs.edit().putString("model",candidate).apply()});Text(candidate)}
      }
      Text("المفتاح محفوظ في تفضيلات التطبيق المحلية؛ يفضل استخدام خادم وسيط في الإنتاج.")
     }
     else->{Spacer(Modifier.height(24.dp));Text("هذا القسم قيد التطوير، ولا يدعم التوليد حاليًا.")}
    }
   }
  }
 }
}
