package com.qi.smb_share_android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 隐私政策页面
 * 展示应用的隐私政策内容
 * 
 * @param onBack 返回按钮点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                color = Color.Transparent,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "隐私政策",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 引言
            item {
                Text(
                    text = "感谢您使用 SMB 文件共享应用。我们非常重视您的隐私，本隐私政策将帮助您了解我们如何收集、使用和保护您的信息。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 数据收集说明
            item {
                PolicySection(
                    title = "1. 数据收集说明",
                    content = listOf(
                        "本应用不收集任何个人身份信息（PII）",
                        "本应用不会将您的任何数据上传到我们的服务器或第三方服务器",
                        "所有数据均存储在您的设备本地"
                    )
                )
            }
            
            // 本地存储的数据
            item {
                PolicySection(
                    title = "2. 本地存储的数据",
                    content = listOf(
                        "SMB 服务器连接配置（包括服务器地址、端口、共享名称、用户名和密码）",
                        "文件传输任务记录（包括文件名、远程路径、本地保存路径以及进度信息）",
                        "应用设置（如主题偏好、引导完成状态等）",
                        "最后访问的服务器和路径信息"
                    ),
                    note = "这些数据仅保存在您的设备本地，不会被上传或分享。"
                )
            }
            
            // 权限使用说明
            item {
                PolicySection(
                    title = "3. 权限使用说明",
                    content = listOf(
                        "网络权限（INTERNET）：用于连接 SMB 服务器，浏览和传输文件",
                        "存储权限（READ_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE / READ_MEDIA_*）：用于保存下载的文件到您的设备，以及读取您选择上传的文件",
                        "安装权限（REQUEST_INSTALL_PACKAGES）：用于安装您下载的 APK 文件（可选功能）"
                    ),
                    note = "应用仅在您执行相关操作时才会请求权限，您可以随时在系统设置中撤销这些权限。"
                )
            }
            
            // 数据安全
            item {
                PolicySection(
                    title = "4. 数据安全",
                    content = listOf(
                        "所有数据仅存储在您的设备本地，使用 Android DataStore 进行安全存储",
                        "应用不会将您的数据上传到任何服务器",
                        "应用不包含任何数据收集或分析代码",
                        "您的 SMB 连接密码以明文形式存储在本地（未来版本将改进为加密存储）"
                    ),
                    note = "建议您为设备设置锁屏密码，以保护本地存储的敏感信息。"
                )
            }
            
            // 用户权利
            item {
                PolicySection(
                    title = "5. 您的权利",
                    content = listOf(
                        "您可以随时在系统设置中撤销应用的权限",
                        "您可以在传输管理页面删除不再需要的传输记录，并在应用设置中清除缓存数据",
                        "您可以在系统设置中清除应用的所有数据",
                        "您可以随时卸载应用，所有本地数据将被删除"
                    )
                )
            }
            
            // 第三方服务
            item {
                PolicySection(
                    title = "6. 第三方服务",
                    content = listOf(
                        "本应用不使用任何第三方分析服务",
                        "本应用不包含任何广告",
                        "本应用不会与第三方分享您的任何信息"
                    )
                )
            }
            
            // 儿童隐私
            item {
                PolicySection(
                    title = "7. 儿童隐私",
                    content = listOf(
                        "本应用不会故意收集 13 岁以下儿童的个人信息",
                        "如果您是家长或监护人，发现您的孩子向我们提供了个人信息，请联系我们"
                    )
                )
            }
            
            // 隐私政策变更
            item {
                PolicySection(
                    title = "8. 隐私政策变更",
                    content = listOf(
                        "我们可能会不时更新本隐私政策",
                        "任何变更将在应用更新时通过更新日志通知您",
                        "继续使用应用即表示您接受更新后的隐私政策"
                    )
                )
            }
            
            // 联系我们
            item {
                PolicySection(
                    title = "9. 联系我们",
                    content = listOf(
                        "如果您对本隐私政策有任何疑问或建议，请通过以下方式联系我们：",
                        "• 在 GitHub 仓库提交 Issue",
                        "• 通过应用商店的开发者联系方式"
                    )
                )
            }
            
            // 最后更新时间
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "最后更新时间：2024年11月",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 隐私政策章节组件
 * 
 * @param title 章节标题
 * @param content 章节内容列表
 * @param note 可选的注释说明
 */
@Composable
private fun PolicySection(
    title: String,
    content: List<String>,
    note: String? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 标题
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        // 内容列表
        content.forEach { item ->
            Text(
                text = "• $item",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        // 注释
        if (note != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
