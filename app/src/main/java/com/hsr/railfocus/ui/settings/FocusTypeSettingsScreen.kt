package com.hsr.railfocus.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.hsr.railfocus.R
import com.hsr.railfocus.ui.focus.FocusType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTypeSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val focusTypes by viewModel.focusTypes.collectAsState()
    var showAddDialog by remember { mutableStateOf(value = false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_focus_management)) },
                navigationIcon = {
                    // 移除返回按钮，使用手势返回
                    /*
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.selection_back))
                    }
                    */
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_scene))
                    }
                },
            )
        },
    ) { innerPadding ->
        val removableFocusTypes = focusTypes.filter { it.isRemovable }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(removableFocusTypes) { type ->
                FocusTypeItem(
                    type = type,
                ) { viewModel.deleteFocusType(type.id) }
            }
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
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = type.containerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = type.icon,
                    contentDescription = null,
                    tint = type.color,
                    modifier = Modifier.size(24.dp)
                )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFocusTypeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Color) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf("Code") }
    var selectedColor by remember { mutableStateOf(Color(0xFFAD1457)) }

    val presetColors = listOf(
        Color(0xFFAD1457), Color(0xFF2E7D32), Color(0xFF1565C0),
        Color(0xFF33691E), Color(0xFF4527A0), Color(0xFFE65100),
        Color(0xFF0097A7), Color(0xFFC2185B), Color(0xFF424242),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedIconName, selectedColor) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.home_start_journey).take(0) + "添加") // 借用已有的资源或者直接写，这里我统一一下
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
        title = null, // 移除“新增专注场景”标题，让预览卡片作为视觉重心
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // --- 1. 实时预览与名称输入一体化 ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FocusTypePreviewItemWithInput(
                        name = name,
                        onNameChange = { name = it },
                        icon = FocusType.ICON_MAP[selectedIconName] ?: Icons.Default.Lightbulb,
                        color = selectedColor,
                        containerColor = selectedColor.copy(alpha = 0.15f)
                    )
                }

                // --- 2. 图标选择 ---
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_scene_icon), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FocusType.ICON_MAP.keys.forEach { iconName ->
                                val isSelected = (selectedIconName == iconName)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) selectedColor else Color.Transparent)
                                        .clickable { selectedIconName = iconName },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = (FocusType.ICON_MAP[iconName] ?: Icons.Default.Lightbulb),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 3. 颜色选择 ---
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_scene_color), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        presetColors.forEach { color ->
                            val isSelected = (selectedColor == color)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = color }
                                    .border(
                                        width = (if (isSelected) 3.dp else 0.dp),
                                        color = (if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Transparent),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun FocusTypePreviewItemWithInput(
    name: String,
    onNameChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    containerColor: Color
) {
    Card(
        modifier = Modifier.width(260.dp), // 宽度从 220dp 增加到 260dp
        shape = RoundedCornerShape(24.dp), // 稍微调大圆角更显大气
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp), // 增加内边距从 12dp -> 16dp
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp) // 增加间距
        ) {
            Surface(
                modifier = Modifier.size(48.dp), // 图标容器从 40dp -> 48dp
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(28.dp) // 图标本身从 22dp -> 28dp
                    )
                }
            }
            
            Box(contentAlignment = Alignment.CenterStart) {
                if (name.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_scene_name),
                        style = MaterialTheme.typography.titleLarge, // 字体从 titleMedium -> titleLarge
                        color = color.copy(alpha = 0.5f),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    textStyle = MaterialTheme.typography.titleLarge.copy( // 同步增加输入字体大小
                        color = color,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    cursorBrush = SolidColor(color),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    )
                )
            }
        }
    }
}
