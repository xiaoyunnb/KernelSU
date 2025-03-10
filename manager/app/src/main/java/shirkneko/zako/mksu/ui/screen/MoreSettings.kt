package shirkneko.zako.mksu.ui.screen

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shirkneko.zako.mksu.R
import shirkneko.zako.mksu.ui.component.SwitchItem
import shirkneko.zako.mksu.ui.theme.CardConfig
import shirkneko.zako.mksu.ui.theme.ThemeConfig
import shirkneko.zako.mksu.ui.theme.saveCustomBackground
import shirkneko.zako.mksu.ui.util.getSuSFS
import shirkneko.zako.mksu.ui.util.getSuSFSFeatures
import shirkneko.zako.mksu.ui.util.susfsSUS_SU_0
import shirkneko.zako.mksu.ui.util.susfsSUS_SU_2
import shirkneko.zako.mksu.ui.util.susfsSUS_SU_Mode

fun saveCardConfig(context: Context) {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    with(prefs.edit()) {
        putFloat("card_alpha", CardConfig.cardAlpha)
        putBoolean("custom_background_enabled", CardConfig.cardElevation == 0.dp)
        apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun MoreSettingsScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    // SELinux 状态
    var selinuxEnabled by remember {
        mutableStateOf(Shell.cmd("getenforce").exec().out.firstOrNull() == "Enforcing")
    }



    // 卡片配置状态
    var cardAlpha by rememberSaveable { mutableStateOf(CardConfig.cardAlpha) }
    var showCardSettings by remember { mutableStateOf(false) }
    var isCustomBackgroundEnabled by rememberSaveable {
        mutableStateOf(ThemeConfig.customBackgroundUri != null)
    }

    // 初始化卡片配置
    LaunchedEffect(Unit) {
        CardConfig.apply {
            cardAlpha = prefs.getFloat("card_alpha", 0.85f)
            cardElevation = if (prefs.getBoolean("custom_background_enabled", false)) 0.dp else CardConfig.defaultElevation
        }
    }

    // 图片选择器
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.saveCustomBackground(it)
            isCustomBackgroundEnabled = true
            CardConfig.cardElevation = 0.dp
            saveCardConfig(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_settings)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp)
        ) {
            // SELinux 开关
            SwitchItem(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.selinux),
                summary = if (selinuxEnabled)
                    stringResource(R.string.selinux_enabled) else
                    stringResource(R.string.selinux_disabled),
                checked = selinuxEnabled
            ) { enabled ->
                val command = if (enabled) "setenforce 1" else "setenforce 0"
                Shell.getShell().newJob().add(command).exec().let { result ->
                    if (result.isSuccess) selinuxEnabled = enabled
                }
            }

            // region SUSFS 配置（仅在支持时显示）
            val suSFS = getSuSFS()
            val isSUS_SU = getSuSFSFeatures()
            if (suSFS == "Supported") {
                if (isSUS_SU == "CONFIG_KSU_SUSFS_SUS_SU") {
                    var isEnabled by rememberSaveable {
                        mutableStateOf(susfsSUS_SU_Mode() == "2")
                    }

                    LaunchedEffect(Unit) {
                        isEnabled = susfsSUS_SU_Mode() == "2"
                    }

                    SwitchItem(
                        icon = Icons.Filled.VisibilityOff,
                        title = stringResource(id = R.string.settings_susfs_toggle),
                        summary = stringResource(id = R.string.settings_susfs_toggle_summary),
                        checked = isEnabled
                    ) {
                        if (it) {
                            susfsSUS_SU_2()
                        } else {
                            susfsSUS_SU_0()
                        }
                        prefs.edit().putBoolean("enable_sus_su", it).apply()
                        isEnabled = it
                    }
                }
            }
            // endregion

            // 自定义背景开关
            SwitchItem(
                icon = Icons.Filled.Wallpaper,
                title = stringResource(id = R.string.settings_custom_background),
                summary = stringResource(id = R.string.settings_custom_background_summary),
                checked = isCustomBackgroundEnabled
            ) { isChecked ->
                if (isChecked) {
                    pickImageLauncher.launch("image/*")
                } else {
                    context.saveCustomBackground(null)
                    isCustomBackgroundEnabled = false
                    CardConfig.cardElevation = CardConfig.defaultElevation
                    CardConfig.cardAlpha = 1f
                    saveCardConfig(context)
                }
            }

            // 卡片管理展开控制
            if (ThemeConfig.customBackgroundUri != null) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.ExpandMore, null) },
                    headlineContent = { Text(stringResource(R.string.settings_card_manage)) },
                    modifier = Modifier.clickable { showCardSettings = !showCardSettings }
                )

                if (showCardSettings) {
                    // 透明度 Slider
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Opacity, null) },
                        headlineContent = { Text(stringResource(R.string.settings_card_alpha)) },
                        supportingContent = {
                            Slider(
                                value = cardAlpha,
                                onValueChange = { newValue ->
                                    cardAlpha = newValue
                                    CardConfig.cardAlpha = newValue
                                    prefs.edit().putFloat("card_alpha", newValue).apply()
                                },
                                onValueChangeFinished = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        saveCardConfig(context)
                                    }
                                },
                                valueRange = 0f..1f,
                                colors = getSliderColors(cardAlpha),
                                thumb = {
                                    SliderDefaults.Thumb(
                                        interactionSource = remember { MutableInteractionSource() },
                                        thumbSize = DpSize(0.dp, 0.dp)
                                    )
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

// 保存配置到 SharedPreferences


// Slider 颜色配置（从 Settings.kt 迁移）
@Composable
fun getSliderColors(value: Float): SliderColors {
    val activeColor = Color.Magenta.copy(alpha = value)
    return SliderDefaults.colors(
        activeTrackColor = activeColor,
        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
    )
}