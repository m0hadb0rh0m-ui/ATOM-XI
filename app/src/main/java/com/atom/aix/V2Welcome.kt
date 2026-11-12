package com.atom.aix

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeWorkspace(onStart:()->Unit){
 val context=LocalContext.current
 val prefs=remember{context.getSharedPreferences("atom_v2_local",Context.MODE_PRIVATE)}
 var accepted by remember{mutableStateOf(prefs.getBoolean("onboarding_accepted",false))}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
  Text("ATOM AI X",style=MaterialTheme.typography.displaySmall,color=MaterialTheme.colorScheme.primary)
  Text("مرحبًا بك في مساحة العمل الذكية",style=MaterialTheme.typography.headlineMedium)
  Text("يمكنك إنشاء المستندات، إدارة المشاريع والمحادثات، استيراد الملفات، البحث في مصادرها، جدولة تذكيرات، واستخدام الإملاء الصوتي.")
  Text("الخصوصية: تُخزن الملفات والذاكرة المحلية على هذا الجهاز. لا تُرسل إلى خدمة سحابية من هذه الشاشات. إذا استخدمت محرك Gemini أو Ollama في المحادثة، تُرسل رسائلك إلى المزود الذي تختاره.")
  Row{
   Checkbox(checked=accepted,onCheckedChange={accepted=it;prefs.edit().putBoolean("onboarding_accepted",it).apply()})
   Text("فهمت طبيعة التخزين المحلي وحدود الخدمات.",modifier=Modifier.padding(top=12.dp))
  }
  Button(enabled=accepted,onClick=onStart,modifier=Modifier.fillMaxWidth()){Text("الدخول إلى التطبيق")}
  Text("تسجيل الحسابات والمزامنة السحابية غير متاحين قبل تجهيز خدمة تسجيل الدخول الآمنة.",style=MaterialTheme.typography.bodySmall)
 }
}
