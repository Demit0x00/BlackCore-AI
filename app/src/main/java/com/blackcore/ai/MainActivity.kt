package com.blackcore.ai

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin

private val Dark=Color(0xFF080B10); private val Card=Color(0xFF121923); private val Cyan=Color(0xFF43D9F5); private val Gray=Color(0xFF9AA8B8)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent { CoreTheme { val vm:ChatViewModel=viewModel(); CoreApp(vm) } }}
}
@Composable fun CoreTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=darkColorScheme(primary=Cyan,background=Dark,surface=Card,onBackground=Color.White,onSurface=Color.White),content=content)}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CoreApp(vm:ChatViewModel){
 val s by vm.state.collectAsState(); var settings by remember{mutableStateOf(false)}; var renameTarget by remember{mutableStateOf<ChatThread?>(null)}; var renameText by remember{mutableStateOf("")}; val drawer=rememberDrawerState(DrawerValue.Closed); val scope= rememberCoroutineScope()
 ModalNavigationDrawer(drawerState=drawer,drawerContent={
  ModalDrawerSheet(drawerContainerColor=Dark) {
   Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text("BLACKCORE",fontWeight=FontWeight.Black,color=Cyan);Spacer(Modifier.weight(1f));Text("AI",color=Gray)}
   Button(onClick={vm.newChat();scope.launch{drawer.close()}},modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp),colors=ButtonDefaults.buttonColors(containerColor=Card,contentColor=Cyan)){Icon(Icons.Default.Add,"");Spacer(Modifier.width(8.dp));Text("New chat")}
   Text("CHAT HISTORY",style=MaterialTheme.typography.labelMedium,color=Gray,modifier=Modifier.padding(start=16.dp,top=20.dp,bottom=8.dp))
   LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
    items(s.threads,key={it.id}){thread->
     val active=thread.id==s.activeThreadId
     Row(Modifier.fillMaxWidth().background(if(active)Card else Color.Transparent,RoundedCornerShape(12.dp)).padding(start=10.dp,end=4.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically){
      Text(thread.title,modifier=Modifier.weight(1f).padding(vertical=8.dp).clickableSafe { vm.openThread(thread.id);scope.launch{drawer.close()} },maxLines=1,overflow=TextOverflow.Ellipsis,color=if(active)Cyan else Color.White,style=MaterialTheme.typography.bodyMedium)
      IconButton(onClick={renameTarget=thread;renameText=thread.title}){Icon(Icons.Default.Edit,"Rename",tint=Gray,modifier=Modifier.size(18.dp))}
      IconButton(onClick={vm.deleteChat(thread.id)}){Icon(Icons.Default.Delete,"Delete",tint=Gray,modifier=Modifier.size(18.dp))}
     }
    }
   }
   HorizontalDivider(color=Card)
   TextButton(onClick={settings=true;scope.launch{drawer.close()}},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Settings,"");Spacer(Modifier.width(8.dp));Text("Settings")}
  }
 },content={
 Scaffold(containerColor=Dark,topBar={TopAppBar(navigationIcon={IconButton(onClick={scope.launch{drawer.open()}}){Icon(Icons.Default.Menu,"History",tint=Cyan)}},title={Column{Text("BLACKCORE",fontWeight=FontWeight.Black);Text("AI • ONLINE ASSISTANT",style=MaterialTheme.typography.labelSmall,color=Gray)}},actions={IconButton(onClick=vm::newChat){Icon(Icons.Default.Add,"New chat",tint=Cyan)};IconButton(onClick={settings=true}){Icon(Icons.Default.Settings,"Settings",tint=Cyan)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Dark))},
 bottomBar={Surface(color=Dark){Column(Modifier.fillMaxWidth().padding(12.dp)){Row(Modifier.fillMaxWidth().background(Card,RoundedCornerShape(24.dp)).padding(8.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(value=s.input,onValueChange=vm::setInput,modifier=Modifier.weight(1f),placeholder={Text("Message BlackCore…",color=Gray)},maxLines=4,colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Color.Transparent,unfocusedBorderColor=Color.Transparent,focusedContainerColor=Card,unfocusedContainerColor=Card));IconButton(onClick=if(s.loading)vm::stopGeneration else vm::send,enabled=s.loading||s.input.isNotBlank(),modifier=Modifier.background(if(s.loading)Color(0xFFFF6464) else Cyan,RoundedCornerShape(50))){Icon(if(s.loading)Icons.Default.Stop else Icons.Default.Send,if(s.loading)"Stop generation" else "Send",tint=Dark)}}
 Text("Periksa ulang informasi penting yang diberikan AI.",style=MaterialTheme.typography.labelSmall,color=Gray,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=6.dp))}}},){padding->Column(Modifier.fillMaxSize().padding(padding)){
 Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).background(if(s.configured)Color(0xFF44E39A) else Color(0xFFFFB84D),RoundedCornerShape(50)));Spacer(Modifier.width(8.dp));Text(if(s.configured)"API READY" else "SET UP API IN SETTINGS",style=MaterialTheme.typography.labelMedium,color=Gray);Spacer(Modifier.weight(1f));Text(s.model.ifBlank{"No model"},style=MaterialTheme.typography.labelSmall,color=Cyan)}
 if(s.messages.isEmpty()) Welcome { vm.setInput(it) } else LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(s.messages,key={it.id}){Bubble(it)};if(s.loading)item{Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(18.dp),color=Cyan,strokeWidth=2.dp);Spacer(Modifier.width(8.dp));Text("BlackCore sedang berpikir…",color=Gray)}}}
 s.error?.let{Text(it,color=Color(0xFFFF7272),modifier=Modifier.padding(horizontal=16.dp,vertical=4.dp))}
 }}
 })
 if(settings)SettingsDialog(s.config,{vm.saveConfig(it);settings=false},{settings=false})
 renameTarget?.let{target->AlertDialog(onDismissRequest={renameTarget=null},title={Text("Rename chat")},text={OutlinedTextField(renameText,{renameText=it},singleLine=true,label={Text("Conversation title")})},confirmButton={TextButton(onClick={vm.renameChat(target.id,renameText);renameTarget=null}){Text("Save")}},dismissButton={TextButton(onClick={renameTarget=null}){Text("Cancel")}})}
}
private fun Modifier.clickableSafe(onClick:()->Unit):Modifier=androidx.compose.foundation.clickable(onClick=onClick)
@Composable fun Welcome(onPrompt:(String)->Unit){Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.SmartToy,null,tint=Cyan,modifier=Modifier.size(64.dp));Text("BlackCore AI",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Your personal AI workspace.",color=Gray);Spacer(Modifier.height(24.dp));listOf("Jelaskan konsep yang sulit","Bantu saya menulis kode","Buat rencana belajar").forEach{prompt->OutlinedButton(onClick={onPrompt(prompt)},modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)){Text(prompt)}}}}
@Composable fun Bubble(m:ChatMessage){val user=m.role=="user";Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user)Arrangement.End else Arrangement.Start){Column(Modifier.widthIn(max=340.dp).background(if(user)Color(0xFF123340) else Card,RoundedCornerShape(18.dp)).padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(if(user)Icons.Default.Person else Icons.Default.SmartToy,null,tint=Cyan,modifier=Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(if(user)"You" else "BlackCore",style=MaterialTheme.typography.labelMedium,color=Cyan)};Spacer(Modifier.height(6.dp));if(user)SelectionContainer { Text(m.content,color=Color.White) } else MarkdownContent(m.content)}}}
@Composable fun MarkdownContent(markdown:String){
 val context=LocalContext.current
 val markwon=remember(context){Markwon.builder(context).usePlugin(TablePlugin.create(context)).usePlugin(StrikethroughPlugin.create()).build()}
 val parts=remember(markdown){splitFencedCode(markdown)}
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  parts.forEachIndexed { index,part -> if(part.code==null) {
   if(part.text.isNotBlank()) AndroidView(factory={TextView(it).apply{setTextColor(android.graphics.Color.WHITE);textSize=15f;setTextIsSelectable(true);setPadding(0,0,0,0)}},update={markwon.setMarkdown(it,part.text)})
  } else CodePanel(part.language,part.code) }
 }
}
data class MarkdownPart(val text:String="",val code:String?=null,val language:String="")
private fun splitFencedCode(input:String):List<MarkdownPart>{
 val regex=Regex("(?s)^```([^\n]*)\n(.*?)\n?```\s*$")
 val result=mutableListOf<MarkdownPart>();var cursor=0
 val fence=Regex("(?s)```([^\n]*)\n(.*?)\n?```")
 fence.findAll(input).forEach{m->if(m.range.first>cursor)result.add(MarkdownPart(text=input.substring(cursor,m.range.first)));result.add(MarkdownPart(code=m.groupValues[2],language=m.groupValues[1].trim()));cursor=m.range.last+1}
 if(cursor<input.length)result.add(MarkdownPart(text=input.substring(cursor)))
 if(result.isEmpty())result.add(MarkdownPart(text=input))
 return result
}
@Composable fun CodePanel(language:String,code:String){
 val clipboard=LocalClipboardManager.current
 Column(Modifier.fillMaxWidth().background(Color(0xFF070A0E),RoundedCornerShape(12.dp)).padding(10.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Text(language.ifBlank{"code"},color=Cyan,style=MaterialTheme.typography.labelSmall);Spacer(Modifier.weight(1f));TextButton(onClick={clipboard.setText(AnnotatedString(code))},contentPadding=PaddingValues(horizontal=8.dp)){Icon(Icons.Default.ContentCopy,"Copy code",modifier=Modifier.size(14.dp));Spacer(Modifier.width(4.dp));Text("Copy",style=MaterialTheme.typography.labelSmall)}}
  SelectionContainer { Text(highlightCode(code),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall) }
 }
}
private fun highlightCode(code:String):AnnotatedString{
 val keywords=setOf("fun","val","var","class","object","if","else","when","for","while","return","import","package","public","private","protected","static","void","int","String","const","let","function","def","async","await","try","catch","throw","new","true","false","null","None","this","super","extends","implements")
 val token=Regex(""""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|//[^\n]*|#[^\n]*|\b[A-Za-z_][A-Za-z0-9_]*\b|\b\d+(?:\.\d+)?\b")
 return AnnotatedString.Builder().apply{var pos=0;token.findAll(code).forEach{m->append(code.substring(pos,m.range.first));val t=m.value;val color=when{t.startsWith(""")||t.startsWith("'")->Color(0xFF9BE7A5);t.startsWith("//")||t.startsWith("#")->Color(0xFF7E8B98);t.firstOrNull()?.isDigit()==true->Color(0xFFFFC66D);t in keywords->Color(0xFF70B9FF);else->Color(0xFFE6EDF3)};pushStyle(SpanStyle(color=color,fontWeight=if(t in keywords)FontWeight.Bold else FontWeight.Normal));append(t);pop();pos=m.range.last+1};append(code.substring(pos)}.toAnnotatedString()
}
@Composable fun SettingsDialog(config:AIConfig,onSave:(AIConfig)->Unit,onDismiss:()->Unit){var url by remember{mutableStateOf(config.endpoint)};var key by remember{mutableStateOf(config.apiKey)};var model by remember{mutableStateOf(config.model)};var prompt by remember{mutableStateOf(config.systemPrompt)}
 AlertDialog(onDismissRequest=onDismiss,title={Text("AI Settings")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(url,{url=it},label={Text("Chat completions URL")},singleLine=true);OutlinedTextField(key,{key=it},label={Text("API key")},singleLine=true);OutlinedTextField(model,{model=it},label={Text("Model ID")},singleLine=true);OutlinedTextField(prompt,{prompt=it},label={Text("System prompt")},minLines=2);Text("Endpoint harus kompatibel dengan OpenAI Chat Completions.",style=MaterialTheme.typography.labelSmall,color=Gray)}},confirmButton={Button(onClick={onSave(AIConfig(url.trim(),key.trim(),model.trim(),prompt))}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}
