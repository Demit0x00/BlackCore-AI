package com.blackcore.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Dark=Color(0xFF080B10); private val Card=Color(0xFF121923); private val Cyan=Color(0xFF43D9F5); private val Gray=Color(0xFF9AA8B8)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent { CoreTheme { val vm:ChatViewModel=viewModel(); CoreApp(vm) } }}
}
@Composable fun CoreTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=darkColorScheme(primary=Cyan,background=Dark,surface=Card,onBackground=Color.White,onSurface=Color.White),content=content)}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CoreApp(vm:ChatViewModel){
 val s by vm.state.collectAsState(); var settings by remember{mutableStateOf(false)}
 Scaffold(containerColor=Dark,topBar={TopAppBar(title={Column{Text("BLACKCORE",fontWeight=FontWeight.Black);Text("AI • ONLINE ASSISTANT",style=MaterialTheme.typography.labelSmall,color=Gray)}},actions={IconButton(onClick=vm::newChat){Icon(Icons.Default.Add,"New chat",tint=Cyan)};IconButton(onClick={settings=true}){Icon(Icons.Default.Settings,"Settings",tint=Cyan)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Dark))},
 bottomBar={Surface(color=Dark){Column(Modifier.fillMaxWidth().padding(12.dp)){Row(Modifier.fillMaxWidth().background(Card,RoundedCornerShape(24.dp)).padding(8.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(value=s.input,onValueChange=vm::setInput,modifier=Modifier.weight(1f),placeholder={Text("Message BlackCore…",color=Gray)},maxLines=4,colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Color.Transparent,unfocusedBorderColor=Color.Transparent,focusedContainerColor=Card,unfocusedContainerColor=Card));IconButton(onClick=vm::send,enabled=s.input.isNotBlank()&&!s.loading,modifier=Modifier.background(Cyan,RoundedCornerShape(50))){Icon(Icons.Default.Send,"Send",tint=Dark)}}
 Text("Periksa ulang informasi penting yang diberikan AI.",style=MaterialTheme.typography.labelSmall,color=Gray,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=6.dp))}}},){padding->Column(Modifier.fillMaxSize().padding(padding)){
 Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).background(if(s.configured)Color(0xFF44E39A) else Color(0xFFFFB84D),RoundedCornerShape(50)));Spacer(Modifier.width(8.dp));Text(if(s.configured)"API READY" else "SET UP API IN SETTINGS",style=MaterialTheme.typography.labelMedium,color=Gray);Spacer(Modifier.weight(1f));Text(s.model.ifBlank{"No model"},style=MaterialTheme.typography.labelSmall,color=Cyan)}
 if(s.messages.isEmpty()) Welcome { vm.setInput(it) } else LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(s.messages,key={it.id}){Bubble(it)};if(s.loading)item{Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(18.dp),color=Cyan,strokeWidth=2.dp);Spacer(Modifier.width(8.dp));Text("BlackCore sedang berpikir…",color=Gray)}}}
 s.error?.let{Text(it,color=Color(0xFFFF7272),modifier=Modifier.padding(horizontal=16.dp,vertical=4.dp))}
 }}
 if(settings)SettingsDialog(s.config,{vm.saveConfig(it);settings=false},{settings=false})
}
@Composable fun Welcome(onPrompt:(String)->Unit){Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.SmartToy,null,tint=Cyan,modifier=Modifier.size(64.dp));Text("BlackCore AI",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Your personal AI workspace.",color=Gray);Spacer(Modifier.height(24.dp));listOf("Jelaskan konsep yang sulit","Bantu saya menulis kode","Buat rencana belajar").forEach{prompt->OutlinedButton(onClick={onPrompt(prompt)},modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)){Text(prompt)}}}}
@Composable fun Bubble(m:ChatMessage){val user=m.role=="user";Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user)Arrangement.End else Arrangement.Start){Column(Modifier.widthIn(max=320.dp).background(if(user)Color(0xFF123340) else Card,RoundedCornerShape(18.dp)).padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(if(user)Icons.Default.Person else Icons.Default.SmartToy,null,tint=Cyan,modifier=Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(if(user)"You" else "BlackCore",style=MaterialTheme.typography.labelMedium,color=Cyan)};Spacer(Modifier.height(6.dp));Text(m.content)}}}
@Composable fun SettingsDialog(config:AIConfig,onSave:(AIConfig)->Unit,onDismiss:()->Unit){var url by remember{mutableStateOf(config.endpoint)};var key by remember{mutableStateOf(config.apiKey)};var model by remember{mutableStateOf(config.model)};var prompt by remember{mutableStateOf(config.systemPrompt)}
 AlertDialog(onDismissRequest=onDismiss,title={Text("AI Settings")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(url,{url=it},label={Text("Chat completions URL")},singleLine=true);OutlinedTextField(key,{key=it},label={Text("API key")},singleLine=true);OutlinedTextField(model,{model=it},label={Text("Model ID")},singleLine=true);OutlinedTextField(prompt,{prompt=it},label={Text("System prompt")},minLines=2);Text("Endpoint harus kompatibel dengan OpenAI Chat Completions.",style=MaterialTheme.typography.labelSmall,color=Gray)}},confirmButton={Button(onClick={onSave(AIConfig(url.trim(),key.trim(),model.trim(),prompt))}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}
}