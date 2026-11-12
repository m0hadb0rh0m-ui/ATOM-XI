package com.atom.aix

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun WorkspaceHome(onNavigate:(Int)->Unit) {
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
  Text("✧ ATOM AI X",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
  Text("مساحة عملك الذكية",style=MaterialTheme.typography.headlineMedium)
  Text("المحادثات والمستندات والمشاريع في مكان واحد.",color=MaterialTheme.colorScheme.onSurfaceVariant)
  listOf(Triple("المحادثة","سجل محادثات محلي",0),Triple("لوحة العمل","محرر نصوص محلي مع حفظ تلقائي",2),Triple("المشاريع","إدارة مشاريع محلية",6)).forEach{(name,description,index)->
   ElevatedCard(onClick={onNavigate(index)},modifier=Modifier.fillMaxWidth()){
    Column(Modifier.padding(20.dp)){Text(name,style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(5.dp));Text(description)}
   }
  }
  Text("خدمات الذكاء الاصطناعي السحابية ستُفعّل بعد إعداد الخادم الآمن.",style=MaterialTheme.typography.bodySmall)
 }
}

@Composable
fun UnavailableSection(title:String,description:String){
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
  Text(description,color=MaterialTheme.colorScheme.onSurfaceVariant)
  AssistChip(onClick={},enabled=false,label={Text("غير متاح حاليًا")})
 }
}

@Composable
fun LocalDocumentWorkspace(){
 val context=LocalContext.current
 val prefs=remember{context.getSharedPreferences("atom_v2_local",Context.MODE_PRIVATE)}
 var title by remember{mutableStateOf(prefs.getString("document_title","مستند جديد").orEmpty())}
 var body by remember{mutableStateOf(prefs.getString("document_body","").orEmpty())}
 var saved by remember{mutableStateOf(true)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("لوحة العمل",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
  Text("محرر نصوص محلي — لا يحتاج اتصالًا بالإنترنت.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(value=title,onValueChange={title=it;prefs.edit().putString("document_title",it).apply();saved=true},label={Text("عنوان المستند")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(value=body,onValueChange={body=it;prefs.edit().putString("document_body",it).apply();saved=true},label={Text("المحتوى")},modifier=Modifier.fillMaxWidth().heightIn(min=300.dp),minLines=12)
  Text(if(saved)"تم الحفظ محليًا تلقائيًا" else "غير محفوظ")
  Button(onClick={
   val intent=android.content.Intent(android.content.Intent.ACTION_SEND).apply{
    type="text/plain";putExtra(android.content.Intent.EXTRA_SUBJECT,title)
    putExtra(android.content.Intent.EXTRA_TEXT,"# "+title+"\n\n"+body)
   }
   context.startActivity(android.content.Intent.createChooser(intent,"تصدير أو مشاركة المستند"))
  },enabled=body.isNotBlank()){Text("تصدير أو مشاركة النص")}
 }
}

@Composable
fun LocalProjectsWorkspace(){
 val context=LocalContext.current
 val prefs=remember{context.getSharedPreferences("atom_v2_local",Context.MODE_PRIVATE)}
 var projects by remember{mutableStateOf(prefs.getString("projects","[]").orEmpty())}
 var name by remember{mutableStateOf("")}
 var deleteName by remember{mutableStateOf<String?>(null)}
 val items=remember(projects){runCatching{val arr=JSONArray(projects);(0 until arr.length()).map{arr.getString(it)}}.getOrDefault(emptyList())}
 fun store(newItems:List<String>){val arr=JSONArray();newItems.forEach{arr.put(it)};projects=arr.toString();prefs.edit().putString("projects",projects).apply()}
 Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text("المشاريع والمكتبة",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
  Text("مشاريع محلية على الجهاز. المزامنة والمرفقات ستضاف لاحقًا.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(value=name,onValueChange={name=it},modifier=Modifier.fillMaxWidth(),label={Text("اسم مشروع جديد")})
  Button(enabled=name.isNotBlank(),onClick={store(items+name.trim());name=""}){Text("إنشاء مشروع")}
  items.forEach{item->ElevatedCard(Modifier.fillMaxWidth()){
   Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){
    Text(item,modifier=Modifier.weight(1f));TextButton(onClick={deleteName=item}){Text("حذف")}
   }
  }}
  if(items.isEmpty())Text("لا توجد مشاريع بعد.")
 }
 if(deleteName!=null)AlertDialog(onDismissRequest={deleteName=null},title={Text("حذف المشروع؟")},text={Text("سيُحذف اسم المشروع من القائمة المحلية.")},confirmButton={TextButton(onClick={store(items.filterNot{it==deleteName});deleteName=null}){Text("حذف")}},dismissButton={TextButton(onClick={deleteName=null}){Text("إلغاء")}})
}

@Composable
internal fun ChatHistoryManager(db:AtomDatabase,onOpen:(Long)->Unit,onChanged:()->Unit){
 var query by remember{mutableStateOf("")}
 var version by remember{mutableIntStateOf(0)}
 var renameId by remember{mutableStateOf<Long?>(null)}
 var renameText by remember{mutableStateOf("")}
 var deleteId by remember{mutableStateOf<Long?>(null)}
 val sessions=remember(query,version){if(query.isBlank())db.sessions() else db.searchSessions(query)}
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("سجل المحادثات",style=MaterialTheme.typography.titleLarge)
  OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),label={Text("بحث في العناوين والرسائل")})
  sessions.forEach{(id,title)->
   ElevatedCard(Modifier.fillMaxWidth()){
    Column(Modifier.padding(12.dp)){
     Text((if(db.isPinned(id))"📌 " else "")+title)
     Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
      TextButton(onClick={onOpen(id)}){Text("فتح")}
      TextButton(onClick={renameId=id;renameText=title}){Text("تسمية")}
      TextButton(onClick={db.togglePin(id);version++;onChanged()}){Text(if(db.isPinned(id))"إلغاء التثبيت" else "تثبيت")}
      TextButton(onClick={deleteId=id}){Text("حذف")}
     }
    }
   }
  }
  if(sessions.isEmpty())Text("لا توجد نتائج.")
 }
 if(renameId!=null)AlertDialog(onDismissRequest={renameId=null},title={Text("إعادة تسمية المحادثة")},text={OutlinedTextField(value=renameText,onValueChange={renameText=it},singleLine=true)},confirmButton={TextButton(enabled=renameText.isNotBlank(),onClick={db.renameSession(renameId!!,renameText.trim());renameId=null;version++;onChanged()}){Text("حفظ")}},dismissButton={TextButton(onClick={renameId=null}){Text("إلغاء")}})
 if(deleteId!=null)AlertDialog(onDismissRequest={deleteId=null},title={Text("حذف المحادثة؟")},text={Text("سيتم حذف الرسائل المحفوظة في هذه المحادثة من الجهاز.")},confirmButton={TextButton(onClick={db.deleteSession(deleteId!!);deleteId=null;version++;onChanged()}){Text("حذف")}},dismissButton={TextButton(onClick={deleteId=null}){Text("إلغاء")}})
}
