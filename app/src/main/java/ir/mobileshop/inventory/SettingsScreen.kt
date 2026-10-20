package ir.mobileshop.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsTab(vm: InventoryViewModel, contentPadding: PaddingValues) {
    val status by vm.syncStatus.collectAsState()
    var url by remember { mutableStateOf(vm.sync.serverUrl) }
    var token by remember { mutableStateOf(vm.sync.token) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            HeroHeader(height = 84.dp) {
                Row(
                    Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("اتصال به سرور", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            Column(
                Modifier.padding(16.dp).padding(bottom = contentPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WhiteCard {
                    SectionTitle("همگام‌سازی بین گوشی‌ها")
                    Text(
                        "روی همه‌ی گوشی‌ها یک آدرس و یک توکن وارد کنید تا موجودی بین آن‌ها یکی شود. " +
                            "بدون اینترنت هم می‌توانید کار کنید؛ تغییرات بعداً ارسال می‌شود.",
                        color = InkGrey,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("آدرس سرور (https://…)", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(12.dp),
                            colors = shopFieldColors()
                        )
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("توکن", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = shopFieldColors()
                        )
                        Button(
                            onClick = { vm.saveServer(url, token) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)
                        ) { Text("ذخیره و همگام‌سازی", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                    }
                    SyncChip(status, onClick = { vm.syncNow() })
                }
                WhiteCard {
                    Text("فروشگاه بلاد شاپور", fontWeight = FontWeight.Bold)
                    Text("نسخه‌ی ۱٫۰", color = InkGrey, fontSize = 12.sp)
                }
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
    }
}
