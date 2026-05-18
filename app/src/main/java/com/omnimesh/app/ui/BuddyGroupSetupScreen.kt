package omnimesh.command1.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import omnimesh.command1.command.BuddyGroup

@Composable
fun BuddyGroupSetupScreen(
    currentGroup: BuddyGroup?,
    joinCode: String? = null,
    onCreateGroup: (name: String, displayName: String) -> Unit,
    onJoinGroup: (code: String, displayName: String) -> Unit,
    onLeaveGroup: () -> Unit = {},
    onBack: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onBack).padding(bottom = 24.dp)
        ) {
            Text("←", color = Color(0xFF4285F4), fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text("BUDDY GROUPS", color = Color(0xFF4285F4), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }

        Icon(Icons.Filled.Group, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Family & Team Alerts", color = Color(0xFFE8EAED), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "When anyone in your group triggers an emergency, all members receive an immediate alert with their GPS location.",
            color = Color(0xFF9AA0A6),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(32.dp))

        if (currentGroup != null) {
            Text("YOUR GROUP", color = Color(0xFF9AA0A6), fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF174EA6).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF4285F4).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("JOIN CODE", color = Color(0xFF9AA0A6), fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(currentGroup.id, color = Color(0xFF4285F4), fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Share this code with others to join", color = Color(0xFF9AA0A6), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C2025), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF4285F4).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(currentGroup.name, color = Color(0xFFE8EAED), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("${currentGroup.members.size} members", color = Color(0xFF9AA0A6), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    currentGroup.members.forEach { member ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        if (member.isCurrentDevice) Color(0xFF4285F4).copy(alpha = 0.2f) else Color(0xFF1C2025),
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        if (member.isCurrentDevice) Color(0xFF4285F4) else Color(0xFF2C2C2E),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    member.displayName.take(1).uppercase(),
                                    color = if (member.isCurrentDevice) Color(0xFF4285F4) else Color(0xFF9AA0A6),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(member.displayName + if (member.isCurrentDevice) " (you)" else "", color = Color(0xFFE8EAED), fontSize = 13.sp)
                            member.phoneNumber?.let {
                                if (it.isNotBlank()) {
                                    Icon(Icons.Filled.Phone, contentDescription = "Call", tint = Color(0xFF34A853), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEA4335).copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFEA4335).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onLeaveGroup)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("LEAVE GROUP", color = Color(0xFFEA4335), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF174EA6), RoundedCornerShape(10.dp))
                        .clickable { showCreate = !showCreate; showJoin = false }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CREATE GROUP", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, Color(0xFF4285F4), RoundedCornerShape(10.dp))
                        .clickable { showJoin = !showJoin; showCreate = false }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("JOIN GROUP", color = Color(0xFF4285F4), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            AnimatedVisibility(visible = showCreate) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(Color(0xFF1C2025), RoundedCornerShape(10.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group name (e.g. Sharma Family)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = Color(0xFF2C2C2E),
                            focusedTextColor = Color(0xFFE8EAED),
                            unfocusedTextColor = Color(0xFFE8EAED),
                            focusedLabelColor = Color(0xFF9AA0A6),
                            unfocusedLabelColor = Color(0xFF5F6368),
                        )
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Your name in the group") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = Color(0xFF2C2C2E),
                            focusedTextColor = Color(0xFFE8EAED),
                            unfocusedTextColor = Color(0xFFE8EAED),
                            focusedLabelColor = Color(0xFF9AA0A6),
                            unfocusedLabelColor = Color(0xFF5F6368),
                        )
                    )
                    Button(
                        onClick = { onCreateGroup(groupName, displayName); showCreate = false },
                        enabled = groupName.isNotBlank() && displayName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF174EA6)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("CREATE", fontFamily = FontFamily.Monospace) }
                }
            }

            AnimatedVisibility(visible = showJoin) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(Color(0xFF1C2025), RoundedCornerShape(10.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase() },
                        label = { Text("6-letter join code") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = Color(0xFF2C2C2E),
                            focusedTextColor = Color(0xFFE8EAED),
                            unfocusedTextColor = Color(0xFFE8EAED),
                            focusedLabelColor = Color(0xFF9AA0A6),
                            unfocusedLabelColor = Color(0xFF5F6368),
                        )
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Your name in the group") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = Color(0xFF2C2C2E),
                            focusedTextColor = Color(0xFFE8EAED),
                            unfocusedTextColor = Color(0xFFE8EAED),
                            focusedLabelColor = Color(0xFF9AA0A6),
                            unfocusedLabelColor = Color(0xFF5F6368),
                        )
                    )
                    Button(
                        onClick = { onJoinGroup(joinCode, displayName); showJoin = false },
                        enabled = joinCode.length == 6 && displayName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF174EA6)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("JOIN", fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}
