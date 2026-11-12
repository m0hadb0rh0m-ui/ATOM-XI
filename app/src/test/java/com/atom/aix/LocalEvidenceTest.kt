package com.atom.aix
import org.junit.Assert.*
import org.junit.Test

class LocalEvidenceTest {
 @Test fun matchesAndCitesActualLine(){
  val result=LocalEvidence.find("digital governance","intro\ndigital governance improves access\nother line","report.pdf")
  assertEquals(1,result.size)
  assertTrue(result.first().contains("report.pdf"))
  assertTrue(result.first().contains("السطر 2"))
  assertTrue(result.first().contains("digital governance"))
 }
 @Test fun noFabricatedEvidence(){
  assertTrue(LocalEvidence.find("astronomy","math chapter","notes.txt").isEmpty())
 }
 @Test fun preservesDocumentOrderForTies(){
  val result=LocalEvidence.find("education","education one\neducation two","doc")
  assertTrue(result[0].contains("السطر 1"))
 }
}
