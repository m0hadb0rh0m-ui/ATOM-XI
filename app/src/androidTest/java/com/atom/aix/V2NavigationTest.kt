package com.atom.aix

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V2NavigationTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()

 @Test fun allElevenDestinationsNavigate(){
  rule.onNodeWithText("الترحيب").performScrollTo().performClick()
  rule.onNodeWithText("مرحبًا بك في مساحة العمل الذكية").assertExists()
  rule.onNodeWithText("المحادثة").performScrollTo().performClick()
  rule.onNodeWithText("كيف يمكنني مساعدتك؟").assertExists()
  rule.onNodeWithText("الرئيسية").performScrollTo().performClick()
  rule.onNodeWithText("مساحة عملك الذكية").assertExists()
  rule.onNodeWithText("لوحة العمل").performScrollTo().performClick()
  rule.onNodeWithText("محرر نصوص محلي — لا يحتاج اتصالًا بالإنترنت.").assertExists()
  rule.onNodeWithText("الوكلاء").performScrollTo().performClick()
  rule.onNodeWithText("الوكلاء والأدوات").assertExists()
  rule.onNodeWithText("المعرفة").performScrollTo().performClick()
  rule.onNodeWithText("تحليل المستندات والصور").assertExists()
  rule.onNodeWithText("الأتمتة").performScrollTo().performClick()
  rule.onNodeWithText("الأتمتة والتذكيرات").assertExists()
  rule.onNodeWithText("المشاريع").performScrollTo().performClick()
  rule.onNodeWithText("المشاريع والمكتبة").assertExists()
  rule.onNodeWithText("الصوت").performScrollTo().performClick()
  rule.onNodeWithText("مساحة المحادثة الصوتية").assertExists()
  rule.onNodeWithText("الاستخدام").performScrollTo().performClick()
  rule.onNodeWithText("الاستخدام المحلي").assertExists()
  rule.onNodeWithText("الإعدادات").performScrollTo().performClick()
  rule.onNodeWithText("الإعدادات العامة").assertExists()
 }
}
