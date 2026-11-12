package com.atom.aix

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.net.URLEncoder

/** Local evidence retrieval: deterministic matching, with explicit source line citations. */
internal object LocalEvidence {
 fun find(question:String,source:String,sourceName:String,max:Int=8):List<String>{
  val words=question.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter{it.length>=3}.distinct()
  if(words.isEmpty())return emptyList()
  return source.lines().mapIndexed{index,line->
   val score=words.count{line.contains(it,ignoreCase=true)}
   Triple(score,index+1,line.trim())
  }.filter{it.first>0&&it.third.isNotBlank()}.sortedWith(compareByDescending<Triple<Int,Int,String>>{it.first}.thenBy{it.second}).take(max).map{
   "[$sourceName، السطر ${it.second}] ${it.third.take(550)}"
  }
 }
}

@Composable
fun LocalResearchWorkspace(){
 val context=LocalContext.current
 val prefs=remember{context.getSharedPreferences("atom_v2_local",android.content.Context.MODE_PRIVATE)}
 val document=prefs.getString("advanced_document","").orEmpty().ifBlank{prefs.getString("knowledge_content","").orEmpty()}
 val name=prefs.getString("advanced_document_name","").orEmpty().ifBlank{prefs.getString("knowledge_name","ملف محلي").orEmpty()}
 var question by remember{mutableStateOf("")}
 var evidence by remember{mutableStateOf(emptyList<String>())}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("البحث والمصادر",style=MaterialTheme.typography.headlineMedium)
  Text("بحث موثّق في الملفات المستوردة محليًا. لا تُختلق مراجع أو اقتباسات.")
  OutlinedTextField(value=question,onValueChange={question=it},label={Text("سؤال البحث")},modifier=Modifier.fillMaxWidth())
  Button(enabled=question.isNotBlank(),onClick={evidence=LocalEvidence.find(question,document,name)}){Text("البحث في الملف المحلي")}
  if(document.isBlank())Text("استورد ملفًا من قسم المعرفة أولًا.")
  evidence.forEach{Text(it)}
  if(question.isNotBlank()&&evidence.isEmpty())Text("لا توجد أدلة مستخرجة بعد. النتائج ليست إجابة مولدة.")
  OutlinedButton(enabled=question.isNotBlank(),onClick={
   val url="https://www.google.com/search?q="+URLEncoder.encode(question,"UTF-8")
   context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))
  }){Text("فتح البحث على الويب في المتصفح")}
  Text("فتح المتصفح لا يعني أن التطبيق قرأ النتائج أو تحقق منها. البحث العميق الآلي متعدد المصادر يحتاج خدمة بحث واتصالًا بالخادم.",style=MaterialTheme.typography.bodySmall)
 }
}

@Composable
fun LocalAgentWorkspace(){
 val context=LocalContext.current
 val prefs=remember{context.getSharedPreferences("atom_v2_local",android.content.Context.MODE_PRIVATE)}
 var goal by remember{mutableStateOf("")}
 var selected by remember{mutableStateOf("research")}
 var output by remember{mutableStateOf("")}
 var paused by remember{mutableStateOf(false)}
 val source=prefs.getString("advanced_document","").orEmpty().ifBlank{prefs.getString("knowledge_content","").orEmpty()}
 val sourceName=prefs.getString("advanced_document_name","").orEmpty().ifBlank{prefs.getString("knowledge_name","مصدر محلي").orEmpty()}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("الوكلاء والأدوات",style=MaterialTheme.typography.headlineMedium)
  Text("مسارات عمل محلية محددة: استخراج الأدلة أو تحليل نص أو حساب. لا تدّعي توليد إجابات ذكاء اصطناعي.")
  listOf("research" to "وكيل الأدلة","summary" to "وكيل تحليل النص","math" to "وكيل الحساب").forEach{(key,label)->
   Row{RadioButton(selected=selected==key,onClick={selected=key});Text(label,modifier=Modifier.padding(top=12.dp))}
  }
  OutlinedTextField(value=goal,onValueChange={goal=it},modifier=Modifier.fillMaxWidth(),label={Text("المهمة أو العملية الحسابية")})
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Button(enabled=goal.isNotBlank(),onClick={
    paused=false
    output=when(selected){
     "math"->runCatching{AtomCalculator.evaluate(goal.replace('×','*').replace('÷','/'))}.getOrElse{it.message?:"خطأ"}
     "research"->LocalEvidence.find(goal,source,sourceName).joinToString("\n\n").ifBlank{"لا يوجد دليل مطابق في المستند المحلي."}
     else->{
      val matches=LocalEvidence.find(goal,source,sourceName,12)
      if(matches.isEmpty())"لا توجد مقاطع تدعم الموضوع في المستند." else "المقاطع المرتبطة بالموضوع (وليست ملخصًا مولدًا):\n"+matches.joinToString("\n")
     }
    }
   }){Text("تنفيذ")}
   OutlinedButton(onClick={paused=!paused},enabled=output.isNotBlank()){Text(if(paused)"استئناف العرض" else "إيقاف العرض")}
   TextButton(onClick={output="";paused=false}){Text("مسح")}
  }
  if(output.isNotBlank()&&!paused)Text(output)
  if(paused)Text("العرض متوقف مؤقتًا. المهمة المحلية مكتملة بالفعل.")
 }
}
