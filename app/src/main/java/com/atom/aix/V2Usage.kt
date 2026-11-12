package com.atom.aix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun LocalUsageWorkspace(){
 val context=LocalContext.current
 val db=remember{AtomDatabase(context)}
 var revision by remember{mutableIntStateOf(0)}
 val sessions=remember(revision){db.sessions().size}
 val messages=remember(revision){db.messageCount()}
 val prompts=remember(revision){db.userMessageCount()}
 val prefs=remember{context.getSharedPreferences("atom_v2_local",android.content.Context.MODE_PRIVATE)}
 val memories=remember(revision){prefs.getString("memories","[]").orEmpty()}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text("الاستخدام المحلي",style=MaterialTheme.typography.headlineMedium)
  ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
   Text("المحادثات المحفوظة: $sessions")
   Text("الرسائل المحفوظة: $messages")
   Text("رسائل المستخدم: $prompts")
  }}
  Text("المستند المحلي: "+if(prefs.getString("advanced_document","").isNullOrBlank())"لا يوجد" else "موجود")
  Text("الذاكرة المحلية: "+if(memories=="[]")"فارغة" else "تحتوي بيانات")
  Button(onClick={revision++}){Text("تحديث الإحصاءات")}
  Text("هذه إحصاءات محلية فعلية وليست استهلاك Gemini أو عدد رموز API أو تكلفة الخدمة.",style=MaterialTheme.typography.bodySmall)
 }
}
