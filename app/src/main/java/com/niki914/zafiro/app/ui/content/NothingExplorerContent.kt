package com.niki914.zafiro.app.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.niki914.zafiro.app.R
import com.niki914.libterm.runtime.LibTerm
import com.niki914.libterm.runtime.TermResult
import com.niki914.uikit.infra.liquidScreenTopPadding
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.niki914.zafiro.business.permission.Permission
import com.niki914.zafiro.business.permission.PermissionManager
import com.niki914.zafiro.business.permission.PermissionState
import com.niki914.zafiro.service.requireService

private data class DiscoveryItem(val type:String,val name:String,val detail:String="")
private data class NothingDiscoveryState(
    val loading:Boolean=true,
    val error:String?=null,
    val items:List<DiscoveryItem> = emptyList()
)

@Composable
fun NothingExplorerContent() {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var state by remember { mutableStateOf(NothingDiscoveryState()) }
    var query by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    fun scan() {
        if (state.loading) return
        state=state.copy(loading=true,error=null)
        scope.launch { state=deepScan(context) }
    }

    LaunchedEffect(context) { state=deepScan(context) }

    val visible=remember(state.items,query) {
        val q=query.trim()
        if(q.isBlank()) state.items else state.items.filter {
            it.type.contains(q,true) || it.name.contains(q,true) || it.detail.contains(q,true)
        }
    }

    val packages=state.items.count{it.type=="PACKAGE"}
    val activities=state.items.count{it.type=="ACTIVITY"}
    val services=state.items.count{it.type=="SERVICE"}
    val overlays=state.items.count{it.type=="OVERLAY"}
    val props=state.items.count{it.type=="PROPERTY"}
    val settings=state.items.count{it.type=="SETTING"}
    val configs=state.items.count{it.type=="DEVICE CONFIG"}

    LazyColumn(
        contentPadding=PaddingValues(
            start=16.dp,end=16.dp,
            top=liquidScreenTopPadding()+12.dp,bottom=32.dp
        ),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("NOTHING EXPLORER",style=MaterialTheme.typography.headlineMedium)
                Text(
                    "Deep-scan Nothing OS through Shizuku and surface vendor hooks Android normally keeps out of sight.",
                    style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(modifier=Modifier.fillMaxWidth()) {
                Column(
                    modifier=Modifier.padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)
                ) {
                    Text("DISCOVERY MAP",style=MaterialTheme.typography.titleMedium)
                    Text("$packages packages · $activities activities · $services services")
                    Text("$overlays overlays · $props properties")
                    Text("$settings settings · $configs device_config")
                    Text(
                        if(state.loading) "Scanning vendor surface…" else "${state.items.size} Nothing-related findings",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Button(onClick=::scan,enabled=!state.loading,modifier=Modifier.fillMaxWidth()) {
                Text(if(state.loading) "Deep scanning…" else "Deep scan")
            }
        }

        item {
            OutlinedTextField(
                value=query,onValueChange={query=it},
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text("Search discovered surface")},
                placeholder={Text("glyph, aod, essential, display…")}
            )
        }
        actionMessage?.let { message ->
            item {
                Card(modifier=Modifier.fillMaxWidth()) {
                    Text(
                        message,
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier=Modifier.padding(16.dp)
                    )
                }
            }
        }

        state.error?.let { error ->
            item {
                Card(modifier=Modifier.fillMaxWidth()) {
                    Text(error,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(16.dp))
                }
            }
        }

        if(!state.loading && state.error==null && visible.isEmpty()) {
            item {
                Card(modifier=Modifier.fillMaxWidth()) {
                    Text("No Nothing-specific findings matched this search.",modifier=Modifier.padding(16.dp))
                }
            }
        }

        items(visible,key={"${it.type}:${it.name}:${it.detail}"}) { item ->
            DiscoveryCard(
                item=item,
                onLaunch=if(item.type=="ACTIVITY") {
                    {
                        scope.launch {
                            actionMessage=launchActivity(context,item.name)
                        }
                    }
                } else null
            )
        }
    }
}

@Composable
private fun DiscoveryCard(item:DiscoveryItem,onLaunch:(()->Unit)?=null) {
    Card(modifier=Modifier.fillMaxWidth()) {
        Column(
            modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp)
        ) {
            Text(item.name,style=MaterialTheme.typography.bodyLarge,fontFamily=FontFamily.Monospace)
            if(item.detail.isNotBlank()) {
                Text(
                    item.detail,
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily=FontFamily.Monospace
                )
            }
            Text(item.type,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            if(onLaunch!=null) {
                Button(onClick=onLaunch,modifier=Modifier.fillMaxWidth()) {
                    Text("Launch")
                }
            }
        }
    }
}

private suspend fun deepScan(context:android.content.Context):NothingDiscoveryState {
    if (requireService<PermissionManager>().status(Permission.SHIZUKU) != PermissionState.GRANTED) {
        return NothingDiscoveryState(loading=false,error=context.getString(R.string.control_shizuku_required))
    }
    val term=LibTerm.openShizukuTerm(context)
    return try {
        val command="""
            printf '__PACKAGES__\n'
            pm list packages
            printf '__ACTIVITIES__\n'
            cmd package query-activities --brief --components -a android.intent.action.MAIN | grep -Ei 'nothing|glyph|essential'
            printf '__SERVICES__\n'
            service list
            printf '__OVERLAYS__\n'
            cmd overlay list
            printf '__PROPS__\n'
            getprop
            printf '__GLOBAL__\n'
            settings list global
            printf '__SYSTEM__\n'
            settings list system
            printf '__SECURE__\n'
            settings list secure
            printf '__DEVICE_CONFIG__\n'
            device_config list
        """.trimIndent()

        var scanWarning: String? = null
        val output=when(val result=term.exec(command,timeoutMillis=25_000L)) {
            is TermResult.Success -> {
                check(!result.value.timedOut) { context.getString(R.string.control_scan_timeout) }
                scanWarning = result.value.stderr.toByteArray().decodeToString().trim()
                    .takeIf { it.isNotEmpty() }?.take(600)
                result.value.stdout.toByteArray().decodeToString()
            }
            is TermResult.Failure -> return NothingDiscoveryState(
                loading=false,error="Shizuku shell failed: ${result.failure}"
            )
        }
        NothingDiscoveryState(loading=false,items=parseDiscovery(output),error=scanWarning)
    } catch(t:Exception) {
        if (t is CancellationException) throw t
        NothingDiscoveryState(
            loading=false,
            error="Deep scan failed: ${t.message ?: t.javaClass.simpleName}"
        )
    } finally { withContext(NonCancellable) { term.close() } }
}

private fun parseDiscovery(output:String):List<DiscoveryItem> {
    var section=""
    val result=mutableListOf<DiscoveryItem>()

    output.lineSequence().forEach { raw ->
        val line=raw.trim()
        when(line) {
            "__PACKAGES__","__ACTIVITIES__","__SERVICES__","__OVERLAYS__","__PROPS__",
            "__GLOBAL__","__SYSTEM__","__SECURE__","__DEVICE_CONFIG__" -> {
                section=line
                return@forEach
            }
        }
        if(line.isBlank()) return@forEach

        when(section) {
            "__PACKAGES__" -> {
                val pkg=line.removePrefix("package:").trim()
                if(isNothingish(pkg)) result+=DiscoveryItem("PACKAGE",pkg)
            }
            "__ACTIVITIES__" -> {
                if(line.contains("/") && isNothingish(line)) result+=DiscoveryItem("ACTIVITY",line)
            }
            "__SERVICES__" -> if(isNothingish(line)) result+=DiscoveryItem("SERVICE",line)
            "__OVERLAYS__" -> {
                val x=line.removePrefix("[x]").removePrefix("[ ]").removePrefix("---").trim()
                if(isNothingish(x)) result+=DiscoveryItem("OVERLAY",x)
            }
            "__PROPS__" -> if(isNothingish(line)) {
                val key=line.substringAfter("[").substringBefore("]")
                val value=line.substringAfter("]: [","").substringBeforeLast("]")
                result+=DiscoveryItem("PROPERTY",key.ifBlank{line},value)
            }
            "__GLOBAL__","__SYSTEM__","__SECURE__" -> {
                val key=line.substringBefore('=').trim()
                val value=line.substringAfter('=',"").trim()
                if(isNothingish("$key $value")) {
                    val ns=section.removePrefix("__").removeSuffix("__").lowercase()
                    result+=DiscoveryItem("SETTING",key,"$ns · $value")
                }
            }
            "__DEVICE_CONFIG__" -> if(isNothingish(line)) {
                result+=DiscoveryItem(
                    "DEVICE CONFIG",
                    line.substringBefore('='),
                    line.substringAfter('=',"")
                )
            }
        }
    }

    return result.distinctBy{"${it.type}:${it.name}:${it.detail}"}
        .sortedWith(compareBy<DiscoveryItem>{typeOrder(it.type)}.thenBy{it.name.lowercase()})
}

private fun typeOrder(type:String)=when(type){
    "PACKAGE"->0;"ACTIVITY"->1;"SERVICE"->2;"OVERLAY"->3;"PROPERTY"->4;"SETTING"->5;"DEVICE CONFIG"->6;else->9
}

private fun isNothingish(text:String):Boolean {
    val h=text.lowercase()
    return NOTHING_SURFACE_KEYWORDS.any(h::contains)
}

private val NOTHING_SURFACE_KEYWORDS=listOf(
    "com.nothing","nothing","nt_","nt.","glyph","essential",
    "flip_to","aod","always_on","ambient_display"
)

private suspend fun launchActivity(
    context:android.content.Context,
    component:String
):String {
    if (!Regex("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+").matches(component)) return context.getString(R.string.control_invalid_component)
    val term=LibTerm.openShizukuTerm(context)
    return try {
        when(val result=term.exec("am start -n \"$component\"",timeoutMillis=10_000L)) {
            is TermResult.Success -> {
                val out=result.value.stdout.toByteArray().decodeToString().trim()
                val err=result.value.stderr.toByteArray().decodeToString().trim()
                if (result.value.timedOut || result.value.exitCode != 0 || err.isNotBlank()) {
                    "Launch failed: $component\n$err\n$out"
                } else "Launch requested: $component" + if(out.isBlank()) "" else "\n$out"
            }
            is TermResult.Failure -> "Launch failed: $component\n${result.failure}"
        }
    } catch(t:Exception) {
        if (t is CancellationException) throw t
        "Launch failed: $component\n${t.message ?: t.javaClass.simpleName}"
    } finally {
        withContext(NonCancellable) { term.close() }
    }
}
