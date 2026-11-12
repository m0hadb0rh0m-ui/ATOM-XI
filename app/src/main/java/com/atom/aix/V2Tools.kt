package com.atom.aix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.MathContext

/** Local deterministic calculator. Does not execute user code or claim LLM reasoning. */
object AtomCalculator {
 fun evaluate(source:String):String {
  require(source.length in 1..200){"اكتب عملية حسابية قصيرة"}
  class Parser(val s:String){
   var i=0
   fun skip(){while(i<s.length&&s[i].isWhitespace())i++}
   fun eat(c:Char):Boolean{skip();if(i<s.length&&s[i]==c){i++;return true};return false}
   fun number():BigDecimal{
    skip();val start=i
    while(i<s.length&&(s[i].isDigit()||s[i]=='.'))i++
    require(i>start){"رقم غير صحيح"}
    return s.substring(start,i).toBigDecimal()
   }
   fun factor():BigDecimal {
    skip()
    if(eat('+'))return factor()
    if(eat('-'))return factor().negate()
    if(eat('(')){val n=expr();require(eat(')')){"القوس غير مغلق"};return n}
    return number()
   }
   fun term():BigDecimal{
    var n=factor()
    while(true){n=when{
     eat('*')->n.multiply(factor(),MathContext.DECIMAL64)
     eat('/')->{val d=factor();require(d.compareTo(BigDecimal.ZERO)!=0){"القسمة على صفر"};n.divide(d,MathContext.DECIMAL64)}
     else->return n
    }}
   }
   fun expr():BigDecimal{
    var n=term()
    while(true){n=when{
     eat('+')->n.add(term(),MathContext.DECIMAL64)
     eat('-')->n.subtract(term(),MathContext.DECIMAL64)
     else->return n
    }}
   }
  }
  val p=Parser(source);val result=p.expr();p.skip();require(p.i==source.length){"رموز غير مدعومة"}
  return result.stripTrailingZeros().toPlainString()
 }
}

@Composable
fun LocalToolsWorkspace(){
 var expression by remember{mutableStateOf("")}
 var result by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text("أدوات محلية",style=MaterialTheme.typography.headlineMedium)
  Text("حاسبة دقيقة للعمليات + و− و× و÷ والأقواس. تعمل دون إنترنت.")
  OutlinedTextField(value=expression,onValueChange={expression=it},modifier=Modifier.fillMaxWidth(),label={Text("مثال: (25+17)/2")})
  Button(onClick={result=runCatching{AtomCalculator.evaluate(expression.replace('×','*').replace('÷','/').replace('−','-'))}.getOrElse{it.message?:"خطأ في العملية"}}){Text("احسب")}
  if(result.isNotBlank())Text(result,style=MaterialTheme.typography.headlineSmall)
  Text("الوكلاء المتصلون بنماذج الذكاء الاصطناعي يحتاجون إلى الخادم. هذه الأداة حسابية محلية وليست وكيل AI.",style=MaterialTheme.typography.bodySmall)
 }
}
