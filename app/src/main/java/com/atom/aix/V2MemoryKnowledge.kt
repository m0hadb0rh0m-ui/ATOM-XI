package com.atom.aix

import android.content.Context
import android.net.Uri
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
import org.json.JSONArray
import org.json.JSONObject

private fun readItems(context:Context,key:String):List<Pair<String,String>>{
 val text=context.getSharedPreferences("atom_v2_local",Context.MODE_PRIVATE).getString(key,"[]")?:"[]"
 return runCatching{
  val arr=JSONArray(text)
  (0 until arr.length()).map{val obj=arr.getJSONObject(it);obj.getString("name") to obj.getString("value")}
 }.getOrDefault(emptyList())
}
private fun writeItems(context:Context,key:String,items:List<Pair<String,String>>){
 val arr=JSONArray()
 items.forEach{(name,value)->arr.put(JSONObject().put("name",name).put("value",value))}
 context.getSharedPreferences("atom_v2_local",Context.MODE_PRIVATE).edit().putString(key,arr.toString()).apply()
}

@Composable
fun LocalMemoryWorkspace(){
 val context=LocalContext.current
 var enabled by remember{mutableStateOf(context.getSharedPreferences("atom_v2_local",Context.MODE_PRIVATE).getBoolean("memory_enabled",false))}
 var items by remember{mutableStateOf(readItems(context,"memories"))}
 var name by remember{mutableStateOf("")}
 var value by remember{mutableStateOf("")}
 var editing by remember{mutableStateOf<String?>(null)}
 var deleting by remember{mutableStateOf<String?>(null)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("الذاكرة المحلية",style=MaterialTheme.typography.headlineMedium)
  Text("تخزين تفضيلات يوافق عليها المستخدم. لا تُرسل الذاكرة إلى أي نموذج تلقائيًا.")
  Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
   Text("تمكين الذاكرة",modifier=Modifier.weight(1f))
   Switch(checked=enabled,onCheckedChange={enabled=it;context.getSharedPreferences("atom_v2_local",Context.MODE_PRIVATE).edit().putBoolean("memory_enabled",it).apply()})
  }
  OutlinedTextField(value=name,onValueChange={name=it},label={Text("اسم المعلومة")},modifier=Modifier.fillMaxWidth(),enabled=enabled)
  OutlinedTextField(value=value,onValueChange={value=it},label={Text("المعلومة")},modifier=Modifier.fillMaxWidth(),enabled=enabled)
  Button(enabled=enabled&&name.isNotBlank()&&value.isNotBlank(),onClick={
   items=items.filterNot{it.first==name.trim()}+(name.trim().take(100) to value.trim().take(4000))
   writeItems(context,"memories",items);name="";value=""
  }){Text("حفظ المعلومة")}
  items.forEach{(n,v)->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
   Text(n,style=MaterialTheme.typography.titleMedium);Text(v)
   Row{
    TextButton(onClick={editing=n;name=n;value=v}){Text("تعديل")}
    TextButton(onClick={deleting=n}){Text("حذف")}
   }
  }}}
  OutlinedButton(enabled=items.isNotEmpty(),onClick={deleting="__all__"}){Text("حذف جميع المعلومات")}
  Text("البيانات محفوظة في تفضيلات التطبيق المحلية. لا توجد مزامنة أو تشفير مخصص.",style=MaterialTheme.typography.bodySmall)
 }
 if(deleting!=null)AlertDialog(onDismissRequest={deleting=null},title={Text("تأكيد الحذف")},text={Text("سيُحذف المحتوى المحلي المحدد.")},confirmButton={TextButton(onClick={
  items=if(deleting=="__all__") emptyList() else items.filterNot{it.first==deleting}
  writeItems(context,"memories",items);deleting=null
 }){Text("حذف")}},dismissButton={TextButton(onClick={deleting=null}){Text("إلغاء")}})
}

@Composable
fun LocalKnowledgeWorkspace(){
 val context=LocalContext.current
 val prefs=remember{context.getSharedPreferences("atom_v2_local",Context.MODE_PRIVATE)}
 var fileName by remember{mutableStateOf(prefs.getString("knowledge_name","").orEmpty())}
 var content by remember{mutableStateOf(prefs.getString("knowledge_content","").orEmpty())}
 var query by remember{mutableStateOf("")}
 var error by remember{mutableStateOf("")}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->
  if(uri!=null)try{
   val type=context.contentResolver.getType(uri).orEmpty()
   val data=context.contentResolver.openInputStream(uri)?.use{stream->
    val bytes=stream.readNBytes(512*1024+1)
    require(bytes.size<=512*1024){"الحد الأقصى 512 كيلوبايت"}
    bytes.toString(Charsets.UTF_8)
   }?:error("تعذر قراءة الملف")
   val name=uri.lastPathSegment?.substringAfterLast('/')?:"مستند"
   fileName=name;content=data;error=""
   prefs.edit().putString("knowledge_name",name).putString("knowledge_content",data).apply()
  }catch(e:Exception){error=e.message?:"تعذر قراءة الملف"}
 }
 val matches=remember(content,query){
  if(query.isBlank())emptyList() else content.lines().mapIndexed{index,line->index+1 to line}.filter{it.second.contains(query,ignoreCase=true)}.take(30)
 }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("المعرفة المحلية",style=MaterialTheme.typography.headlineMedium)
  Text("استيراد TXT وCSV والبحث في النص دون إرسال الملف إلى الإنترنت.")
  Button(onClick={launcher.launch(arrayOf("text/plain","text/csv","text/*"))}){Text("اختيار ملف نصي")}
  if(fileName.isNotBlank())Text("الملف: $fileName")
  OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),label={Text("بحث داخل الملف")})
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
  matches.forEach{(line,text)->Text("السطر $line: "+text.take(350))}
  if(query.isNotBlank()&&matches.isEmpty())Text("لم تُعثر نتائج مطابقة.")
  OutlinedButton(enabled=content.isNotBlank(),onClick={fileName="";content="";query="";prefs.edit().remove("knowledge_name").remove("knowledge_content").apply()}){Text("حذف الملف المحلي")}
  Text("PDF وDOCX وOCR وRAG ليست مدعومة في هذه الشاشة حتى الآن.",style=MaterialTheme.typography.bodySmall)
 }
}
