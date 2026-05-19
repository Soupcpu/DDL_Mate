package com.deadlinemate.ui.deepseek

import android.content.Context
import android.content.Intent
import android.net.Uri

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadlinemate.deepseek.DeepSeekConfig
import com.deadlinemate.deepseek.statusText
import com.deadlinemate.ui.components.GlassPanel
import com.deadlinemate.ui.components.PageTopBar
import com.deadlinemate.ui.i18n.AppLanguage
import com.deadlinemate.ui.i18n.LocalAppLanguage
import com.deadlinemate.ui.i18n.text
import com.deadlinemate.ui.theme.AppBg
import com.deadlinemate.ui.theme.AppBlue
import com.deadlinemate.ui.theme.AppLine
import com.deadlinemate.ui.theme.AppRed
import com.deadlinemate.ui.theme.AppSubtext
import com.deadlinemate.ui.theme.AppText
import kotlinx.coroutines.launch

@Composable
fun DeepSeekSettingsScreen(
    config: DeepSeekConfig?,
    pageBg: Color = AppBg,
    onBack: () -> Unit,
    onSave: (DeepSeekConfig) -> Unit,
    onClear: () -> Unit,
    onTest: suspend (DeepSeekConfig) -> Result<Unit>
) {
    val language = LocalAppLanguage.current
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(DeepSeekConfig.DEFAULT_BASE_URL) }
    var model by remember { mutableStateOf(DeepSeekConfig.DEFAULT_MODEL) }
    var keyVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(config) {
        apiKey = config?.apiKey.orEmpty()
        baseUrl = config?.baseUrl ?: DeepSeekConfig.DEFAULT_BASE_URL
        model = config?.model ?: DeepSeekConfig.DEFAULT_MODEL
        keyVisible = false
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(language.text("清除 DeepSeek 配置？", "Clear DeepSeek config?")) },
            text = { Text(language.text("清除后，语音添加和截图识别将无法使用，需要重新配置 API Key 后才能开启。", "After clearing, voice add and screenshot recognition will be unavailable until you configure an API Key again.")) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClear()
                    apiKey = ""
                    message = language.text("配置已清除", "Configuration cleared")
                }) { Text(language.text("确认清除", "Clear"), color = AppRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(language.text("取消", "Cancel")) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PageTopBar("Settings", language.text("DeepSeek API 设置", "DeepSeek API Settings"), "‹", onBack)

        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 28.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                StatusBlock(
                    config = DeepSeekConfig(apiKey, baseUrl, model, apiKey.isNotBlank())
                        .takeIf { apiKey.isNotBlank() }
                )
                Field("API Key") {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        singleLine = true,
                        placeholder = { Text("sk-...", color = AppSubtext) },
                        trailingIcon = {
                            TextButton(onClick = { keyVisible = !keyVisible }) {
                                Text(if (keyVisible) language.text("隐藏", "Hide") else language.text("显示", "Show"), color = AppBlue, fontSize = 12.sp)
                            }
                        },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp),
                        colors = fieldColors()
                    )
                }
                Field("Base URL") {
                    StyledField(baseUrl, { baseUrl = it }, DeepSeekConfig.DEFAULT_BASE_URL)
                }
                Field(language.text("模型", "Model")) {
                    StyledField(model, { model = it }, DeepSeekConfig.DEFAULT_MODEL)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(language.text("智能解析", "Smart Parsing"), color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text(
                            if (apiKey.isNotBlank()) language.text("保存后自动开启，语音和截图会调用 DeepSeek。", "Automatically enabled after saving. Voice and screenshots will use DeepSeek.")
                            else language.text("输入并保存 API Key 后自动开启。", "Enter and save an API Key to enable it automatically."),
                            color = AppSubtext,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        if (apiKey.isNotBlank()) language.text("自动开启", "Auto On") else language.text("待配置", "Not Set"),
                        color = if (apiKey.isNotBlank()) AppBlue else AppSubtext,
                        fontWeight = FontWeight.Black
                    )
                }
                message?.let {
                    Box(Modifier.fillMaxWidth().background(AppBlue.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Text(it, color = AppBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                PrimaryButton(language.text("保存配置", "Save Config")) {
                    when {
                        apiKey.isBlank() -> message = language.text("请补全 API Key", "Please enter API Key")
                        baseUrl.isBlank() -> message = language.text("请补全 Base URL", "Please enter Base URL")
                        model.isBlank() -> message = language.text("请补全模型名称", "Please enter model name")
                        else -> {
                            onSave(DeepSeekConfig(apiKey.trim(), baseUrl.trim(), model.trim(), enabled = true))
                            keyVisible = false
                            message = language.text("配置已保存，增强模式已开启", "Configuration saved. Enhanced mode is enabled.")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(if (testing) language.text("测试中...", "Testing...") else language.text("测试连接", "Test Connection"), Modifier.weight(1f), enabled = !testing) {
                        when {
                            apiKey.isBlank() -> message = language.text("请补全 API Key", "Please enter API Key")
                            baseUrl.isBlank() -> message = language.text("请补全 Base URL", "Please enter Base URL")
                            model.isBlank() -> message = language.text("请补全模型名称", "Please enter model name")
                            else -> scope.launch {
                                testing = true
                                val result = onTest(DeepSeekConfig(apiKey.trim(), baseUrl.trim(), model.trim(), enabled = true))
                                testing = false
                                message = if (result.isSuccess) {
                                    language.text("连接成功", "Connection successful")
                                } else {
                                    language.text("连接失败，请检查 API Key、Base URL、模型名称或网络状态", "Connection failed. Check API Key, Base URL, model name, or network.")
                                }
                            }
                        }
                    }
                    DangerButton(language.text("清除配置", "Clear Config"), Modifier.weight(1f)) { showClearConfirm = true }
                }
                SecondaryButton(
                    text = language.text("获取 DeepSeek API", "Get DeepSeek API"),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    openDeepSeekPlatform(context)
                }
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
            Text(
                language.text(
                    "语音和截图会先在本地识别为文字。启用智能解析后，App 只会把识别出的文字发送给 DeepSeek，用于提取任务名称、截止时间、重要性和重复规则。原始音频和原始截图不会上传。",
                    "Voice and screenshots are converted to text locally first. With smart parsing enabled, only recognized text is sent to DeepSeek to extract task title, deadline, importance, and repeat rules. Raw audio and screenshots are not uploaded."
                ),
                color = AppSubtext,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

private fun openDeepSeekPlatform(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.deepseek.com/api_keys"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun StatusBlock(config: DeepSeekConfig?) {
    val language = LocalAppLanguage.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppBlue.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .border(1.dp, AppBlue.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(language.text("当前状态", "Current Status"), color = AppSubtext, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(config.statusText(if (language == AppLanguage.English) "English" else "中文"), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, color = AppSubtext, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 2.dp, bottom = 7.dp))
        content()
    }
}

@Composable
private fun StyledField(value: String, onValue: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        placeholder = { Text(placeholder, color = AppSubtext) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = fieldColors()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White.copy(alpha = 0.78f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.78f),
    focusedBorderColor = AppLine,
    unfocusedBorderColor = AppLine,
    focusedTextColor = AppText,
    unfocusedTextColor = AppText
)

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp).background(AppBlue, RoundedCornerShape(18.dp)),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp).background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(18.dp)).border(1.dp, AppLine, RoundedCornerShape(18.dp)),
        colors = ButtonDefaults.textButtonColors(contentColor = AppText, disabledContentColor = AppSubtext)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun DangerButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(50.dp).background(AppRed.copy(alpha = 0.10f), RoundedCornerShape(18.dp)),
        colors = ButtonDefaults.textButtonColors(contentColor = AppRed)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}
