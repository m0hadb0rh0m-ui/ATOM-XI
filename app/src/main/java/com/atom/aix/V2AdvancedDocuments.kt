package com.atom.aix

import android.content.Context
import android.net.Uri
import android.text.Html
import android.graphics.BitmapFactory
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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object DocumentReader {
 fun extract(context:Context,uri:Uri):String{
  val name=uri.lastPathSegment.orEmpty().lowercase()
  val type=context.contentResolver.getType(uri).orEmpty()
  val bytes=context.contentResolver.openInputStream(uri)?.use{it.readNBytes(8*1024*1024+1)}
   ?:error("تعذر قراءة الملف")
  require(bytes.size<=8*1024*1024){"حجم الملف أكبر من 8 ميغابايت"}
  val isPdf=type=="application/pdf"||name.endsWith(".pdf")
  val isDocx=type.contains("wordprocessingml")||name.endsWith(".docx")
  val isXlsx=type.contains("spreadsheetml")||name.endsWith(".xlsx")
  val result=when{
   isPdf->{
    PDFBoxResourceLoader.init(context.applicationContext)
    PDDocument.load(bytes).use{pdf->
     require(pdf.numberOfPages<=100){"الحد الأقصى 100 صفحة"}
     PDFTextStripper().getText(pdf)
    }
   }
   isDocx||isXlsx->extractZip(bytes,isXlsx)
   else->bytes.toString(Charsets.UTF_8)
  }
  return result.take(300000)
 }
 private fun extractZip(bytes:ByteArray,xlsx:Boolean):String{
  val files=mutableMapOf<String,String>()
  ZipInputStream(ByteArrayInputStream(bytes)).use{zip->
   var entry=zip.nextEntry
   var count=0
   while(entry!=null){
    count++;require(count<=500){"ملف يحتوي على عناصر كثيرة"}
    val path=entry.name
    val needed=if(xlsx)path=="xl/sharedStrings.xml"||path.matches(Regex("xl/worksheets/sheet[0-9]+.xml")) else path=="word/document.xml"
    if(needed){
     val data=zip.readNBytes(3*1024*1024+1)
     require(data.size<=3*1024*1024){"محتوى مضغوط كبير"}
     files[path]=data.toString(Charsets.UTF_8)
    }
    zip.closeEntry();entry=zip.nextEntry
   }
  }
  fun decode(s:String):String=Html.fromHtml(s.replace(Regex("<(/?)(?:w:p|row|c|si|t)(?:\\s[^>]*)?>"),"\n").replace(Regex("<[^>]*>"),""),Html.FROM_HTML_MODE_LEGACY).toString()
  if(!xlsx)return decode(files["word/document.xml"]?:error("ملف Word غير صالح"))
  val shared=Regex("<si(?:\\s[^>]*)?>(.*?)</si>",RegexOption.DOT_MATCHES_ALL).findAll(files["xl/sharedStrings.xml"].orEmpty()).map{decode(it.groupValues[1]).trim()}.toList()
  val sheets=files.filterKeys{it.startsWith("xl/worksheets/sheet")}.toSortedMap()
  require(sheets.isNotEmpty()){"لم تُعثر أوراق Excel"}
  return buildString{
   sheets.forEach{(sheet,xml)->
    appendLine("## $sheet")
    Regex("<row(?:\\s[^>]*)?>(.*?)</row>",RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach{row->
     val cells=Regex("<c\\b([^>]*)>(.*?)</c>",RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1]).map{c->
      val v=Regex("<v>(.*?)</v>",RegexOption.DOT_MATCHES_ALL).find(c.groupValues[2])?.groupValues?.get(1).orEmpty()
      if(c.groupValues[1].contains("t=\"s\""))shared.getOrNull(v.toIntOrNull()?:-1).orEmpty() else if(v.isNotEmpty())v else decode(c.groupValues[2]).trim()
     }.toList()
     appendLine(cells.joinToString(" | "))
    }
   }
  }
 }
}

@Composable
fun AdvancedDocumentWorkspace(){
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 val prefs=remember{context.getSharedPreferences("atom_v2_local",Context.MODE_PRIVATE)}
 var text by remember{mutableStateOf(prefs.getString("advanced_document","").orEmpty())}
 var filename by remember{mutableStateOf(prefs.getString("advanced_document_name","").orEmpty())}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 var query by remember{mutableStateOf("")}
 val recognizer=remember{TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)}
 DisposableEffect(recognizer){onDispose{recognizer.close()}}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->
  if(uri!=null){
   busy=true;error=""
   val mime=context.contentResolver.getType(uri).orEmpty()
   if(mime.startsWith("image/")){
    try{
     val bytes=context.contentResolver.openInputStream(uri)?.use{it.readNBytes(8*1024*1024+1)}?:error("تعذر فتح الصورة")
     require(bytes.size<=8*1024*1024){"الصورة كبيرة جدًا"}
     val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("صيغة صورة غير مدعومة")
     recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener{
      text=it.text;filename=uri.lastPathSegment.orEmpty();prefs.edit().putString("advanced_document",text).putString("advanced_document_name",filename).apply();busy=false
     }.addOnFailureListener{error=it.message?:"فشل التعرف على النص";busy=false}
    }catch(e:Exception){error=e.message?:"تعذر قراءة الصورة";busy=false}
   }else scope.launch{
    try{
     val extracted=withContext(Dispatchers.IO){DocumentReader.extract(context,uri)}
     text=extracted;filename=uri.lastPathSegment.orEmpty()
     prefs.edit().putString("advanced_document",text).putString("advanced_document_name",filename).apply()
    }catch(e:Exception){error=e.message?:"فشل تحليل المستند"}
    finally{busy=false}
   }
  }
 }
 val hits=remember(text,query){if(query.isBlank())emptyList() else text.lines().mapIndexed{index,line->(index+1) to line}.filter{it.second.contains(query,ignoreCase=true)}.take(30)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("تحليل المستندات والصور",style=MaterialTheme.typography.headlineMedium)
  Text("استخراج النص من PDF وWord وExcel والصور، والبحث محليًا مع رقم السطر.")
  Button(onClick={launcher.launch(arrayOf("application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","image/*","text/plain"))}){Text("استيراد مستند أو صورة")}
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
  if(filename.isNotBlank())Text("المصدر: $filename")
  OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),label={Text("بحث في المستند")})
  hits.forEach{(number,line)->Text("[$filename، السطر $number] "+line.take(500))}
  if(text.isNotBlank())Text("النص المستخرج: "+text.take(3500),style=MaterialTheme.typography.bodySmall)
  OutlinedButton(enabled=text.isNotBlank(),onClick={text="";filename="";prefs.edit().remove("advanced_document").remove("advanced_document_name").apply()}){Text("حذف المستند المحلي")}
  Text("الصور تستخدم OCR لاتينيًا؛ النص العربي داخل الصور قد لا يُقرأ بدقة. المستندات الممسوحة ضوئيًا تحتاج OCR منفصلًا.",style=MaterialTheme.typography.bodySmall)
 }
}
