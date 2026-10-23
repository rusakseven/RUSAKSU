package id.rskseven.rusaksu.ui.screen

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import id.rskseven.rusaksu.Natives
import id.rskseven.rusaksu.R
import id.rskseven.rusaksu.ksuApp
import id.rskseven.rusaksu.ui.LocalNavBarEnabled
import id.rskseven.rusaksu.ui.LocalScrollState
import id.rskseven.rusaksu.ui.rememberScrollConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val STEALTH_PREFS = "settings"
private const val STEALTH_TARGETS_KEY = "stealth_targets"

private val Emerald = Color(0xFF00B873)

private data class HideTarget(
    val packageName: String,
    val label: String,
    val uid: Int,
    val hidden: Boolean,
)

private data class CheckRow(
    val label: String,
    /** null = belum tersedia (Phase 3) */
    val passed: Boolean?,
)

/** Cek indikator root generik dari sudut pandang aplikasi (jujur, lokal). */
private fun rootIndicatorsAbsent(): Boolean {
    val suPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
    )
    if (suPaths.any { File(it).exists() }) return false
    if (Build.TAGS?.contains("test-keys") == true) return false
    return true
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun StealthScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) {
        context.getSharedPreferences(STEALTH_PREFS, Context.MODE_PRIVATE)
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var masterOn by remember { mutableStateOf(false) }
    var masterBusy by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf<List<CheckRow>>(emptyList()) }
    var targets by remember { mutableStateOf<List<HideTarget>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var testing by remember { mutableStateOf(false) }

    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        val selinuxHide = runCatching { Natives.isSelinuxHideEnabled() }.getOrDefault(false)
        val kernelUmount = runCatching { Natives.isKernelUmountEnabled() }.getOrDefault(false)
        val rootOk = rootIndicatorsAbsent()

        val newChecks = listOf(
            CheckRow(context.getString(R.string.stealth_check_selinux_hide), selinuxHide),
            CheckRow(context.getString(R.string.stealth_check_kernel_umount), kernelUmount),
            CheckRow(context.getString(R.string.stealth_check_root_indicators), rootOk),
            CheckRow(context.getString(R.string.stealth_check_play_integrity), null),
            CheckRow(context.getString(R.string.stealth_check_props), null),
        )

        val pm = context.packageManager
        val stored = prefs.getStringSet(STEALTH_TARGETS_KEY, emptySet()).orEmpty().sorted()
        val newTargets = stored.mapNotNull { pkg ->
            runCatching {
                val info = pm.getPackageInfo(pkg, 0)
                val uid = info.applicationInfo?.uid ?: return@runCatching null
                val profile = Natives.getAppProfile(pkg, uid)
                HideTarget(
                    packageName = pkg,
                    label = pm.getApplicationLabel(info.applicationInfo!!).toString(),
                    uid = uid,
                    hidden = profile.umountModules,
                )
            }.getOrNull()
        }

        withContext(Dispatchers.Main) {
            masterOn = selinuxHide && kernelUmount
            checks = newChecks
            targets = newTargets
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    fun setMaster(on: Boolean) {
        scope.launch {
            masterBusy = true
            withContext(Dispatchers.IO) {
                runCatching {
                    Natives.setSelinuxHideEnabled(on)
                    Natives.setKernelUmountEnabled(on)
                    Natives.setAvcSpoofEnabled(on)
                    Natives.setDefaultUmountModules(on)
                }
            }
            refreshAll()
            masterBusy = false
        }
    }

    fun setTargetHidden(pkg: String, uid: Int, on: Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val current = Natives.getAppProfile(pkg, uid)
                    val updated = current.copy(
                        nonRootUseDefault = false,
                        umountModules = on,
                    )
                    Natives.setAppProfile(updated)
                }.getOrDefault(false)
            }
            if (ok) {
                val set = prefs.getStringSet(STEALTH_TARGETS_KEY, emptySet()).orEmpty().toMutableSet()
                if (on) set.add(pkg) else set.remove(pkg)
                prefs.edit().putStringSet(STEALTH_TARGETS_KEY, set).apply()
                refreshAll()
            } else {
                Toast.makeText(context, "Gagal mengubah target", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun testAllTargets() {
        scope.launch {
            testing = true
            val results = withContext(Dispatchers.IO) {
                targets.associate { t ->
                    t.packageName to runCatching {
                        Natives.getAppProfile(t.packageName, t.uid).umountModules
                    }.getOrDefault(false)
                }
            }
            testResults = results
            testing = false
        }
    }

    // Bottom bar scroll tracking
    val bottomBarScrollState = LocalScrollState.current
    val bottomBarScrollConnection = if (bottomBarScrollState != null) {
        rememberScrollConnection(
            isScrollingDown = bottomBarScrollState.isScrollingDown,
            scrollOffset = bottomBarScrollState.scrollOffset,
            previousScrollOffset = bottomBarScrollState.previousScrollOffset,
            threshold = 30f
        )
    } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = Emerald,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.stealth),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val scrollState = LocalScrollState.current
        val navBarEnabled = LocalNavBarEnabled.current
        val isNavBarHidden =
            (scrollState?.isScrollingDown?.value ?: false) || (navBarEnabled?.value == false)
        val navBarPadding =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                if (isNavBarHidden) 0.dp else 112.dp

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .let { modifier ->
                    if (bottomBarScrollConnection != null) {
                        modifier
                            .nestedScroll(bottomBarScrollConnection)
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(bottom = navBarPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = Emerald)
                }
            } else {
                // Master switch card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = Emerald,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.stealth_mode),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.stealth_mode_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (masterBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Emerald,
                                strokeWidth = 3.dp,
                            )
                        } else {
                            Switch(
                                checked = masterOn,
                                onCheckedChange = { setMaster(it) },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Emerald,
                                ),
                            )
                        }
                    }
                }

                // Detection checks
                SectionLabel(stringResource(R.string.stealth_detection_check))
                Card {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        checks.forEach { check ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                when (check.passed) {
                                    true -> Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Emerald,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    false -> Icon(
                                        Icons.Filled.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    null -> Icon(
                                        Icons.Filled.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = check.label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                StatusChip(check.passed)
                            }
                        }
                    }
                }

                // Targets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel(
                        stringResource(R.string.stealth_targets),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.stealth_add_target))
                    }
                }
                Card {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        if (targets.isEmpty()) {
                            Text(
                                text = stringResource(R.string.stealth_no_targets),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        } else {
                            targets.forEach { target ->
                                TargetRow(
                                    target = target,
                                    onToggle = { on -> setTargetHidden(target.packageName, target.uid, on) },
                                )
                            }
                        }
                    }
                }

                // Test all button
                androidx.compose.material3.Button(
                    onClick = { testAllTargets() },
                    enabled = !testing && targets.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Emerald,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.Black,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Text(
                            stringResource(R.string.stealth_test_all),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showAddDialog) {
        AddTargetDialog(
            exclude = targets.map { it.packageName }.toSet(),
            onDismiss = { showAddDialog = false },
            onAdd = { pkg, uid ->
                showAddDialog = false
                setTargetHidden(pkg, uid, true)
            },
        )
    }

    testResults?.let { results ->
        AlertDialog(
            onDismissRequest = { testResults = null },
            title = { Text(stringResource(R.string.stealth_test_all)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    results.forEach { (pkg, passed) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = pkg,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            StatusChip(passed)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.stealth_test_result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { testResults = null }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusChip(passed: Boolean?) {
    val (text, container, content) = when (passed) {
        true -> Triple(
            stringResource(R.string.stealth_passed),
            Emerald,
            Color.Black,
        )
        false -> Triple(
            stringResource(R.string.stealth_failed),
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
        )
        null -> Triple(
            stringResource(R.string.stealth_coming_soon),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TargetRow(target: HideTarget, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val appIcon = remember(target.packageName) {
        runCatching { context.packageManager.getApplicationIcon(target.packageName) }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = appIcon,
            contentDescription = target.label,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = target.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = target.hidden,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTargetDialog(
    exclude: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (packageName: String, uid: Int) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledPackages(0)
                .filter { pkg ->
                    val name = pkg.packageName ?: return@filter false
                    name != ksuApp.packageName &&
                        name !in exclude &&
                        pm.getLaunchIntentForPackage(name) != null
                }
                .mapNotNull { pkg ->
                    val name = pkg.packageName ?: return@mapNotNull null
                    val label = runCatching {
                        pm.getApplicationLabel(pkg.applicationInfo!!).toString()
                    }.getOrDefault(name)
                    name to label
                }
                .sortedBy { it.second.lowercase() }
        }
        apps = list
        loadingApps = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stealth_add_target)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Cari aplikasi…") },
                )
                Spacer(Modifier.height(8.dp))
                if (loadingApps) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = Emerald, modifier = Modifier.size(28.dp))
                    }
                } else {
                    val filtered = apps.filter {
                        it.first.contains(query, ignoreCase = true) ||
                            it.second.contains(query, ignoreCase = true)
                    }
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(filtered, key = { it.first }) { (pkg, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val uid = runCatching {
                                            context.packageManager.getPackageInfo(pkg, 0)
                                                .applicationInfo!!.uid
                                        }.getOrDefault(-1)
                                        if (uid >= 0) onAdd(pkg, uid)
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        pkg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Emerald)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
    )
}
