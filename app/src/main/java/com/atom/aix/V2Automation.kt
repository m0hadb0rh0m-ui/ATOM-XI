package com.atom.aix

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class AtomReminderWorker(context:Context,params:WorkerParameters):Worker(context,params){
 override fun doWork():Result{
  val title=inputData.getString("title")?:"تذكير ATOM"
  val channel="atom_v2_reminders"
  val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  manager.createNotificationChannel(NotificationChannel(channel,"ATOM Reminders",NotificationManager.IMPORTANCE_DEFAULT))
  if(Build.VERSION.SDK_INT>=33&&applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return Result.failure()
  val notification=Notification.Builder(applicationContext,channel)
   .setSmallIcon(android.R.drawable.ic_popup_reminder)
   .setContentTitle("ATOM AI X")
   .setContentText(title)
   .setAutoCancel(true).build()
  manager.notify(id.hashCode(),notification)
  return Result.success()
 }
}

@Composable
fun LocalAutomationWorkspace(){
 val context=LocalContext.current
 val prefs=remember{context.getSharedPreferences("atom_v2_local",Context.MODE_PRIVATE)}
 var records by remember{mutableStateOf(prefs.getString("reminders","[]").orEmpty())}
 var title by remember{mutableStateOf("")}
 var delay by remember{mutableStateOf("15")}
 var message by remember{mutableStateOf("")}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
  message=if(granted)"تم السماح بالإشعارات." else "الإشعارات غير مسموحة؛ قد لا يظهر التذكير."
 }
 val items=remember(records){runCatching{
  val arr=JSONArray(records);(0 until arr.length()).map{arr.getJSONObject(it)}
 }.getOrDefault(emptyList())}
 fun store(list:List<JSONObject>){val a=JSONArray();list.forEach{a.put(it)};records=a.toString();prefs.edit().putString("reminders",records).apply()}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("الأتمتة والتذكيرات",style=MaterialTheme.typography.headlineMedium)
  Text("تذكيرات محلية مجدولة عبر WorkManager، تعمل بعد مغادرة التطبيق. قد يؤخرها Android لتوفير البطارية.")
  if(Build.VERSION.SDK_INT>=33)OutlinedButton(onClick={permission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("السماح بالإشعارات")}
  OutlinedTextField(value=title,onValueChange={title=it},label={Text("نص التذكير")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(value=delay,onValueChange={delay=it},label={Text("بعد كم دقيقة؟")},modifier=Modifier.fillMaxWidth())
  Button(enabled=title.isNotBlank()&&(delay.toLongOrNull()?:0)>0,onClick={
   val minutes=delay.toLongOrNull()?:0
   if(minutes in 1..525600){
    val id=UUID.randomUUID()
    val work=OneTimeWorkRequestBuilder<AtomReminderWorker>()
     .setInputData(workDataOf("title" to title.trim()))
     .setInitialDelay(minutes,TimeUnit.MINUTES).addTag("atom_reminder").build()
    WorkManager.getInstance(context).enqueueUniqueWork("atom_${id}",ExistingWorkPolicy.REPLACE,work)
    val obj=JSONObject().put("id",id.toString()).put("work",work.id.toString()).put("title",title.trim()).put("time",System.currentTimeMillis()+minutes*60000)
    store(items+obj);title="";message="تمت جدولة التذكير محليًا."
   }else message="اختر مدة من دقيقة إلى سنة."
  }){Text("جدولة التذكير")}
  if(message.isNotBlank())Text(message)
  items.forEach{item->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
   Text(item.optString("title"))
   Text("الوقت المتوقع: "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.optLong("time"))))
   TextButton(onClick={
    runCatching{WorkManager.getInstance(context).cancelWorkById(UUID.fromString(item.optString("work")))}
    store(items.filterNot{it.optString("id")==item.optString("id")})
   }){Text("إلغاء التذكير")}
  }}}
  Text("هذه تذكيرات محلية وليست وكلاء يعملون دوريًا في السحابة. تنفيذ مهام AI في الخلفية يتطلب ربط الخادم.",style=MaterialTheme.typography.bodySmall)
 }
}
