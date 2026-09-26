package com.hsr.railfocus.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hsr.railfocus.R
import com.hsr.railfocus.ui.focus.*
import java.util.UUID

/**
 * 专注场景管理页面 - Material 3 Expressive 风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTypeSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val focusTypes by viewModel.focusTypes.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_focus_management),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.selection_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.settings_add_scene), fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val removableFocusTypes = focusTypes.filter { it.isRemovable }
        var typeToDelete by remember { mutableStateOf<FocusType?>(null) }

        if (removableFocusTypes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.settings_no_custom_scenes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(removableFocusTypes, key = { it.id }) { type ->
                    FocusTypeItem(
                        type = type,
                        modifier = Modifier.animateItem(),
                    ) { typeToDelete = type }
                }
            }
        }

        typeToDelete?.let { type ->
            AlertDialog(
                onDismissRequest = { typeToDelete = null },
                title = { Text(stringResource(R.string.settings_delete)) },
                text = { Text(stringResource(R.string.settings_delete_scene_confirm, type.displayName)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteFocusType(type.id)
                            typeToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { typeToDelete = null }) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                }
            )
        }
    }

    if (showAddDialog) {
        AddFocusTypeDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, iconName, color ->
                val id = UUID.randomUUID().toString()
                val containerColor = color.copy(alpha = 0.2f)
                val newType = FocusType(
                    id = id,
                    displayName = name,
                    icon = FocusType.ICON_MAP[iconName] ?: Icons.Default.Lightbulb,
                    iconName = iconName,
                    color = color,
                    containerColor = containerColor
                )
                viewModel.addFocusType(newType)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun FocusTypeItem(
    type: FocusType,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = type.containerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = type.color.copy(alpha = 0.25f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = null,
                        tint = type.color,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = type.color
                )
            }

            if (type.isRemovable) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.settings_delete),
                        tint = type.color
                    )
                }
            }
        }
    }
}

/**
 * 场景添加弹窗 - Material 3 Expressive 设计规范（三段式布局，呼应图 2）
 * 1. 顶部栏：紧凑关闭图标、粗体标题、模板选择胶囊与确认完成按钮
 * 2. 第一段：中心大尺寸触感圆形图标头像，带微妙高光与点击波纹
 * 3. 第二段：居中圆角标题输入栏，沉浸式占位符
 * 4. 第三段：外环光晕选中态的高对比色轮选择排，彩虹色轮打开全量 ColorPicker
 */
@Composable
private fun AddFocusTypeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Color) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf("Add") }
    var selectedColor by remember { mutableStateOf(Color(0xFF2E7D32)) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showFullColorPicker by remember { mutableStateOf(false) }

    val animatedColor by animateColorAsState(
        targetValue = selectedColor,
        animationSpec = spring(),
        label = "selectedColor"
    )

    val primaryColors = listOf(
        Color(0xFF2E7D32), // 经典绿 (图2同款)
        Color(0xFF102A45), // 藏青深蓝
        Color(0xFF4A154B), // 墨紫深红
        Color(0xFF007AFF), // 科技亮蓝
        Color(0xFFE65100), // 活力活力橙
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // --- 顶部操作行：关闭按钮、标题、完成胶囊按钮 ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            onClick = onDismiss,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.settings_cancel),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.fts_dialog_add),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // M3 Expressive 完成胶囊按钮
                    Surface(
                        onClick = {
                            if (name.isNotBlank()) {
                                val actualIcon = if (selectedIconName == "Add") "Lightbulb" else selectedIconName
                                onConfirm(name.trim(), actualIcon, selectedColor)
                            }
                        },
                        shape = CircleShape,
                        color = if (name.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (name.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        shadowElevation = if (name.isNotBlank()) 2.dp else 0.dp,
                    ) {
                        Text(
                            text = stringResource(R.string.action_done),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
                        )
                    }
                }

                // --- 模板快捷选择入口（左侧紧随顶栏） ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    Surface(
                        onClick = { showTemplateDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.fts_template),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 第一段：大尺寸圆形图标预览（M3 Expressive Hero 视觉焦点） ---
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .background(animatedColor)
                        .clickable { showIconPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    val currentIcon = if (selectedIconName == "Add") {
                        Icons.Default.Add
                    } else {
                        FocusTypeIcons.ICON_MAP[selectedIconName] ?: Icons.Default.Add
                    }
                    Icon(
                        imageVector = currentIcon,
                        contentDescription = stringResource(R.string.fts_change_icon_cd),
                        modifier = Modifier.size(62.dp),
                        tint = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // --- 第二段：居中圆角“标题”输入框 ---
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (name.isEmpty()) {
                            Text(
                                text = stringResource(R.string.fts_name_placeholder),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(animatedColor),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // --- 第三段：色彩选择排（带外环选中态与彩虹轮） ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primaryColors.forEach { color ->
                            val isSelected = (selectedColor == color)
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.dp,
                                        color = if (isSelected) color else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .padding(if (isSelected) 4.dp else 0.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = color },
                            )
                        }

                        // 彩虹渐变调色轮入口（打开完整 ColorPicker 调色盘）
                        Surface(
                            onClick = { showFullColorPicker = true },
                            shape = CircleShape,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.sweepGradient(
                                            listOf(
                                                Color(0xFFFF3B30),
                                                Color(0xFFFF9500),
                                                Color(0xFFFFCC00),
                                                Color(0xFF34C759),
                                                Color(0xFF007AFF),
                                                Color(0xFF5856D6),
                                                Color(0xFFFF2D55),
                                                Color(0xFFFF3B30),
                                            )
                                        ),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = stringResource(R.string.fts_palette_open_cd),
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 图标选择器（按三列网格排布，去掉全部下方文字） ---
    if (showIconPicker) {
        IconPickerSheet(
            selectedIconName = selectedIconName,
            tintColor = animatedColor,
            onDismiss = { showIconPicker = false },
            onIconSelected = { iconName ->
                selectedIconName = iconName
                showIconPicker = false
            }
        )
    }

    // --- 模板快捷选择弹窗 ---
    if (showTemplateDialog) {
        TemplateSelectionDialog(
            onDismiss = { showTemplateDialog = false },
            onTemplateSelected = { tName, tIcon, tColor ->
                name = tName
                selectedIconName = tIcon
                selectedColor = tColor
                showTemplateDialog = false
            }
        )
    }

    // --- 完整色彩选择器（全功能 HSV + 调色滑块 + 色彩板） ---
    if (showFullColorPicker) {
        FullColorPickerDialog(
            initialColor = selectedColor,
            onDismiss = { showFullColorPicker = false },
            onColorSelected = { color ->
                selectedColor = color
                showFullColorPicker = false
            }
        )
    }
}

/**
 * 图标选择器弹窗：严格按三列网格（3 Columns）排布全部场景图标，完全移除文字
 */
@Composable
private fun IconPickerSheet(
    selectedIconName: String,
    tintColor: Color,
    onDismiss: () -> Unit,
    onIconSelected: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.72f),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.fts_icon_section),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 核心：严格三列（GridCells.Fixed(3)）网格展示图标，无下方文字
                val allIcons = FocusTypeIcons.ICON_MAP.keys.toList()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(allIcons) { iconName ->
                        val isSelected = (selectedIconName == iconName)
                        val icon = FocusTypeIcons.ICON_MAP[iconName] ?: Icons.Default.Lightbulb
                        Surface(
                            onClick = { onIconSelected(iconName) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) tintColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (isSelected) BorderStroke(2.dp, tintColor) else null,
                            tonalElevation = if (isSelected) 4.dp else 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = iconName,
                                    modifier = Modifier.size(34.dp),
                                    tint = if (isSelected) tintColor else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 完整调色盘组件（Full Color Picker）：
 * 包含色相滑块（Hue）、饱和度滑块（Saturation）、明度滑块（Brightness）、
 * 实时十六进制颜色代码与 16 种经典配色网格。
 */
@Composable
private fun FullColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        val argb = android.graphics.Color.argb(
            255,
            (initialColor.red * 255).toInt(),
            (initialColor.green * 255).toInt(),
            (initialColor.blue * 255).toInt()
        )
        android.graphics.Color.colorToHSV(argb, hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.1f)) }

    val currentColor = remember(hue, saturation, value) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    val hexCode = remember(currentColor) {
        val r = (currentColor.red * 255).toInt().coerceIn(0, 255)
        val g = (currentColor.green * 255).toInt().coerceIn(0, 255)
        val b = (currentColor.blue * 255).toInt().coerceIn(0, 255)
        String.format("#%02X%02X%02X", r, g, b)
    }

    val palettePresets = listOf(
        Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759),
        Color(0xFF00C7BE), Color(0xFF30B0C7), Color(0xFF32ADE6), Color(0xFF007AFF),
        Color(0xFF5856D6), Color(0xFFAF52DE), Color(0xFFFF2D55), Color(0xFFA2845E),
        Color(0xFF8E8E93), Color(0xFF636366), Color(0xFF48484A), Color(0xFF1C1C1E)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 顶栏：标题与关闭
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.fts_palette_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 当前选色即时预览栏与十六进制代码
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(currentColor)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.fts_current_color),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = hexCode,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Button(
                            onClick = { onColorSelected(currentColor) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = currentColor),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_confirm),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // --- 色相调节滑块 (Hue 0..360) ---
                Text(
                    text = stringResource(R.string.fts_hue_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFF0000),
                                    Color(0xFFFFFF00),
                                    Color(0xFF00FF00),
                                    Color(0xFF00FFFF),
                                    Color(0xFF0000FF),
                                    Color(0xFFFF00FF),
                                    Color(0xFFFF0000),
                                )
                            )
                        )
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    modifier = Modifier.fillMaxWidth()
                )

                // --- 饱和度调节滑块 (Saturation 0..1) ---
                Text(
                    text = stringResource(R.string.fts_saturation_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                // --- 明度调节滑块 (Value 0..1) ---
                Text(
                    text = stringResource(R.string.fts_brightness_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0.1f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // --- 经典预设色彩矩阵 ---
                Text(
                    text = stringResource(R.string.fts_preset_matrix),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(palettePresets) { pColor ->
                        val isPicked = (currentColor == pColor)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(pColor)
                                .border(
                                    width = if (isPicked) 2.5.dp else 0.dp,
                                    color = if (isPicked) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    val newHsv = FloatArray(3)
                                    val argb = android.graphics.Color.argb(
                                        255,
                                        (pColor.red * 255).toInt(),
                                        (pColor.green * 255).toInt(),
                                        (pColor.blue * 255).toInt()
                                    )
                                    android.graphics.Color.colorToHSV(argb, newHsv)
                                    hue = newHsv[0]
                                    saturation = newHsv[1]
                                    value = newHsv[2]
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPicked) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 模板快捷选择器
 */
@Composable
private fun TemplateSelectionDialog(
    onDismiss: () -> Unit,
    onTemplateSelected: (String, String, Color) -> Unit,
) {
    val templates = listOf(
        Triple("编程", "Code", Color(0xFFAD1457)),
        Triple("学习", "School", Color(0xFF2E7D32)),
        Triple("工作", "Work", Color(0xFF1565C0)),
        Triple("阅读", "MenuBook", Color(0xFF33691E)),
        Triple("深度研究", "Lightbulb", Color(0xFF4527A0)),
        Triple("写作随笔", "Edit", Color(0xFFE65100)),
        Triple("运动健身", "FitnessCenter", Color(0xFF0097A7)),
        Triple("休闲时光", "Coffee", Color(0xFF8D6E63)),
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.fts_choose_template),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(templates) { (name, iconName, color) ->
                        val icon = FocusTypeIcons.ICON_MAP[iconName] ?: Icons.Default.Lightbulb
                        Surface(
                            onClick = { onTemplateSelected(name, iconName, color) },
                            shape = RoundedCornerShape(16.dp),
                            color = color.copy(alpha = 0.12f),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                                Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
                            }
                        }
                    }
                }
            }
        }
    }
}
