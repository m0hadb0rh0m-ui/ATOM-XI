package com.atom.aix
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
 val listState=rememberLazyListState()
 LaunchedEffect(lines.size){if(lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)}
 MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF151515),background=Color.White,surface=Color.White)){
  Scaffold(topBar={TopAppBar(title={Text("ATOM AI X",fontWeight=FontWeight.SemiBold)},navigationIcon={TextButton(onClick={tab=if(tab==0) 4 else 0}){Text("☰",style=MaterialTheme.typography.headlineSmall)}},actions={TextButton(onClick={sessionId=db.createSession();version++;tab=0}){Text("✎",style=MaterialTheme.typography.headlineSmall)}})}){padding->
   Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=12.dp)){
    if(tab!=0) ScrollableTabRow(selectedTabIndex=tab){tabs.forEachIndexed{i,title->Tab(selected=tab==i,onClick={tab=i},text={Text(title)})}}
    when(tab){
     0->{
      var expanded by remember { mutableStateOf(false) }
      Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
       Box {
        TextButton(onClick={expanded=true}){Text("☰  المحادثات",color=Color.DarkGray)}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
         val history=remember(version){db.sessions()}
         history.forEach{(id,title)->DropdownMenuItem(text={Text(title+" #"+id)},onClick={sessionId=id;expanded=false})}
         DropdownMenuItem(text={Text("⚙️ الإعدادات")},onClick={tab=4;expanded=false})
         DropdownMenuItem(text={Text("🎨 الصور والفيديو و3D")},onClick={tab=2;expanded=false})
        }
       }
       Spacer(Modifier.weight(1f))
       Text(engine+" ▾",color=Color.Gray,modifier=Modifier.padding(end=12.dp))
      }
      if(lines.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
       Column(horizontalAlignment=Alignment.CenterHorizontally){
        Text("⚛",style=MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        Text("كيف يمكنني مساعدتك؟",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
       }
      } else LazyColumn(state=listState,modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=16.dp)){
       items(lines){line->
        if(line.role=="user"){
         Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.End){
          Text(line.text,modifier=Modifier.widthIn(max=310.dp).background(Color(0xFFF1F1F1),RoundedCornerShape(22.dp)).padding(14.dp),color=Color(0xFF202020))
         }
        } else {
         Column(Modifier.fillMaxWidth().padding(vertical=12.dp)){
          Text("⚛ ATOM",fontWeight=FontWeight.Bold,color=Color(0xFF202020))
          Spacer(Modifier.height(8.dp))
          Text(line.text,color=Color(0xFF202020))
         }
        }
       }
      }
      if(busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
      if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
      if(engine=="Gemini"&&apiKey.isBlank()) TextButton(onClick={tab=4}){Text("أضف مفتاح Gemini API من الإعدادات")}
      Row(Modifier.fillMaxWidth().padding(bottom=8.dp,top=6.dp),verticalAlignment=Alignment.Bottom){
       TextButton(onClick={tab=2},modifier=Modifier.padding(end=2.dp)){Text("+",style=MaterialTheme.typography.headlineMedium)}
       OutlinedTextField(value=input,onValueChange={input=it},modifier=Modifier.weight(1f),placeholder={Text("اسأل ATOM AI X")},shape=RoundedCornerShape(28.dp),maxLines=5)
       Spacer(Modifier.width(6.dp))
       FilledIconButton(enabled=!busy&&input.isNotBlank()&&(engine=="Ollama"||apiKey.isNotBlank()),onClick={
        val message=input.trim();input="";error=""
        val userLine=ChatLine("user",message);lines.add(userLine);db.add(sessionId,userLine);busy=true
        val snapshot=lines.toList()
        scope.launch{
         try{
          val answer=withContext(Dispatchers.IO){if(engine=="Ollama") askOllama(ollamaUrl,ollamaModel,temperature,snapshot) else ask(apiKey,model,snapshot)}
          val line=ChatLine("model",answer);lines.add(line);db.add(sessionId,line)
         }catch(e:Exception){error=e.message?:"تعذر الاتصال"}
         finally{busy=false}
        }
       }){Text("↑",style=MaterialTheme.typography.titleLarge)}
      }
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
