package com.graphene.geburtstagfinal


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }

        }

        BirthdayAlarmScheduler.scheduleNext(this)

        setContent {
            BirthdayApp()
        }

        WorkManager.getInstance(this).cancelUniqueWork("birthday_work")

    }

}


/** ---------- Datenmodell ---------- */
data class BirthdayEntry(
    val id: Long,
    val fullName: String,
    val birthDate: LocalDate
)

/** ---------- Theme (Schwarz/Weiß) ---------- */
@Composable
fun MonoTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black
        )
    }

    MaterialTheme(colorScheme = scheme, content = content)
}

/** ---------- App ---------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var duplicateMsg by remember { mutableStateOf<String?>(null) }

    duplicateMsg?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast
                .makeText(context, msg, android.widget.Toast.LENGTH_SHORT)
                .show()
            duplicateMsg = null
        }
    }

    var exportJson by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(exportJson.toByteArray())
        }
    }


    var notifyHour by remember { mutableIntStateOf(8) }
    var notifyMin by remember { mutableIntStateOf(0) }
    var showTimePicker by remember { mutableStateOf(false) }


    var tab by remember { mutableIntStateOf(0) } // 0 Heute, 1 Demnächst, 2 Gesamt

    // In-Memory Liste (Speichern kommt als nächster Schritt)
    var entries by remember { mutableStateOf(emptyList<BirthdayEntry>()) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        val json =
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@rememberLauncherForActivityResult

        val imported = BirthdayExport.fromJsonString(json)
        entries = imported

        scope.launch {
            BirthdayStore.save(context, entries = imported)
        }

        // speichern
        scope.launch {
            BirthdayStore.save(context, imported)
        }

        // Alarme/Benachrichtigungen neu planen
        BirthdayAlarmScheduler.scheduleNext(context)
    }

    var showAdd by remember { mutableStateOf(false) }
    var deleteMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var notifDaily by remember { mutableStateOf(false) }
    var notif7DaysBefore by remember { mutableStateOf(false) }
    var notifMonthStart by remember { mutableStateOf(false) }

    var showSettings by remember { mutableStateOf(false) }
    var darkModeEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entries = BirthdayStore.load(context)

        notifDaily = BirthdayStore.loadNotifyDaily(context)
        notif7DaysBefore = BirthdayStore.loadNotify7Days(context)
        notifMonthStart = BirthdayStore.loadNotifyMonthStart(context)
        darkModeEnabled = BirthdayStore.loadDarkMode(context)

        val (h, m) = BirthdayStore.loadNotifyTime(context)
        notifyHour = h
        notifyMin = m

        scheduleDailyWorker(context)
    }

    MonoTheme(dark = darkModeEnabled) {


        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Geburtstage") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Einstellungen"
                            )
                        }
                    }
                )
            },

            floatingActionButtonPosition = FabPosition.Center,

            floatingActionButton = {
                if (tab == 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        // Minus Button (Löschmodus)
                        FloatingActionButton(
                            onClick = {
                                deleteMode = !deleteMode
                                if (!deleteMode) selectedIds.clear()
                            },
                            containerColor = Color(0xFFFFB6B6),
                            contentColor = Color.Black
                        ) {
                            Text("-")
                        }

                        // Plus Button (Hinzufügen)
                        FloatingActionButton(
                            onClick = { showAdd = true },
                            containerColor = Color(0xFFB6E6C6),
                            contentColor = Color.Black
                        ) {
                            Text("+")
                        }
                    }
                }
            },

            // 🔴 Fester Lösch-Button unten
            bottomBar = {
                if (tab == 2 && deleteMode) {
                    Button(
                        onClick = { showDeleteConfirm = true },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Ausgewählte löschen")
                    }
                }
            }

        ) { pad ->

            val focusManager = LocalFocusManager.current

            Column(
                modifier = Modifier
                    .padding(pad)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                        })
                    }
            ) {
                var searchText by remember { mutableStateOf("") }

                LaunchedEffect(tab) {
                    if (tab != 2) searchText = ""
                }

                TabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text("Heute") }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text("Demnächst") }
                    )
                    Tab(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        text = { Text("Gesamt") }
                    )
                }

                AnimatedVisibility(
                    visible = tab == 2,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Color(0xFF555555)

                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("Suche nach Personen") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Suche") },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Leeren")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF555555),
                            unfocusedBorderColor = Color(0xFF555555),
                            focusedContainerColor = Color(0xFFF2F2F2),
                            unfocusedContainerColor = Color(0xFFF2F2F2)
                        )
                    )
                }

                val filteredEntries = entries.filter {
                    it.toString().contains(searchText, ignoreCase = true)
                }

                when (tab) {
                    0 -> TodayScreen(filteredEntries)
                    1 -> UpcomingScreen(filteredEntries)
                    2 -> AllScreen(
                        entries = filteredEntries,
                        deleteMode = deleteMode,
                        selectedIds = selectedIds.toSet(),
                        onToggle = { id ->
                            if (selectedIds.contains(id)) {
                                selectedIds.remove(id)
                            } else {
                                selectedIds.add(id)
                            }
                        },

                    )
                }
            }
        }


        if (showAdd) {
            AddDialog(
                onDismiss = { showAdd = false },
                onAdd = { name, date ->

                    val exists = entries.any {
                        it.fullName == name && it.birthDate == date
                    }

                    if (exists) {
                        duplicateMsg = "Eintrag existiert bereits"
                        return@AddDialog
                    }

                    val id = System.currentTimeMillis()
                    entries = entries + BirthdayEntry(id, name, date)

                    scope.launch {
                        BirthdayStore.save(context, entries)
                    }

                    showAdd = false
                }
            )
        }
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) {
                        Text("Schließen")
                    }
                },
                title = { Text("Einstellungen") },
                text = {
                    Column {

                        val timeText = String.format("%02d:%02d", notifyHour, notifyMin)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { showTimePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )

                                Text(
                                    "Uhrzeit für Benachrichtigungen",
                                    modifier = Modifier.weight(1f)
                                )

                                Text(timeText)
                            }
                        }




                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Erinnerung",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = notifDaily,
                                onCheckedChange = {
                                    notifDaily = it
                                    scope.launch {
                                        BirthdayStore.saveNotifyDaily(context, it)
                                    }
                                }
                            )

                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "7 Tage vorher erinnern",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = notif7DaysBefore,
                                onCheckedChange = {
                                    notif7DaysBefore = it
                                    scope.launch {
                                        BirthdayStore.saveNotify7Days(context, it)
                                    }
                                }
                            )

                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "am Monatsbeginn erinnern",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = notifMonthStart,
                                onCheckedChange = {
                                    notifMonthStart = it
                                    scope.launch {
                                        BirthdayStore.saveNotifyMonthStart(context, enabled = it)
                                    }
                                }

                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Dark Mode",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = darkModeEnabled,
                                onCheckedChange = {
                                    darkModeEnabled = it
                                    scope.launch {
                                        BirthdayStore.saveDarkMode(context, enabled = it)
                                    }
                                }

                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text("Daten", style = MaterialTheme.typography.titleMedium)

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                exportJson = BirthdayExport.toJsonString(entries)
                                exportLauncher.launch("geburtstage.json")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Exportieren")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                importLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Importieren")
                        }

                    }
                }

            )
        }

        if (showTimePicker) {
            val dialog = android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    notifyHour = hourOfDay
                    notifyMin = minute

                    scope.launch {
                        BirthdayStore.saveNotifyTime(context, notifyHour, notifyMin)
                        scheduleDailyWorker(context)
                    }
                    showTimePicker = false

                },
                notifyHour,
                notifyMin,
                true
            )
            dialog.setOnDismissListener { showTimePicker = false }
            dialog.show()
        }


        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Löschen bestätigen") },
                text = { Text("Möchtest du ${selectedIds.size} Eintrag(e) wirklich löschen?") },
                confirmButton = {
                    TextButton(onClick = {
                        val idsToDelete = selectedIds.toSet()

                        val newEntries = entries.filterNot { it.id in idsToDelete }
                        entries = newEntries

                        selectedIds.clear()
                        deleteMode = false
                        showDeleteConfirm = false

                        scope.launch {
                            BirthdayStore.save(context, newEntries)
                        }

                    }) {
                        Text("Löschen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Abbrechen")
                    }
                }
            )
        }

    }

}

/** ---------- Screens ---------- */

private val displayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
fun TodayScreen(entries: List<BirthdayEntry>) {
    val today = LocalDate.now()
    val todays = entries
        .filter { nextOccurrence(it.birthDate, today) == today }
        .sortedBy { it.fullName.lowercase() }

    if (todays.isEmpty()) {
        Text("Heute hat niemand Geburtstag.", modifier = Modifier.padding(16.dp))
        return
    }

    LazyColumn {
        items(todays.size) { idx ->
            val e = todays[idx]
            val age = today.year - e.birthDate.year
            ListItem(
                headlineContent = { Text(e.fullName) },
                supportingContent = { Text("${e.birthDate.format(displayFmt)}  •  wird $age") }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun UpcomingScreen(entries: List<BirthdayEntry>) {
    val today = LocalDate.now()
    val upcoming = entries
        .map { it to daysUntil(it.birthDate, today) }
        .filter { (_, d) -> d in 1..7 }
        .sortedBy { it.second }

    if (upcoming.isEmpty()) {
        Text("In den nächsten 7 Tagen hat niemand Geburtstag.", modifier = Modifier.padding(16.dp))
        return
    }

    LazyColumn {
        items(upcoming.size) { idx ->
            val (e, d) = upcoming[idx]
            ListItem(
                headlineContent = { Text(e.fullName) },
                supportingContent = { Text("${e.birthDate.format(displayFmt)}  •  in $d Tag(en)") }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun AllScreen(
    entries: List<BirthdayEntry>,
    deleteMode: Boolean,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit
) {
    val today = LocalDate.now()
    val monthNames = listOf(
        "", "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    )

    // Gruppiert nach Monat 1..12, sortiert nach Tag
    val grouped = (1..12).associateWith { m ->
        entries
            .filter { it.birthDate.monthValue == m }
            .sortedWith(compareBy({ it.birthDate.dayOfMonth }, { it.fullName.lowercase() }))
            .map { e -> e to daysUntil(e.birthDate, today) }
    }

    LazyColumn {
        (1..12).forEach { m ->
            item {
                Text(
                    monthNames[m],
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            val list = grouped[m].orEmpty()
            items(list.size) { idx ->
                val (e, d) = list[idx]
                ListItem(
                    headlineContent = { Text(e.fullName) },
                    supportingContent = { Text("${e.birthDate.format(displayFmt)}  •  in $d Tag(en)") },
                    trailingContent = {
                        if (deleteMode) {
                            Checkbox(
                                checked = selectedIds.contains(e.id),
                                onCheckedChange = { onToggle(e.id) }
                            )
                        }
                    },
                    modifier = if (deleteMode)
                        Modifier.clickable { onToggle(e.id) }
                    else Modifier
                )
                HorizontalDivider()
            }

        }
    }

}

/** ---------- Add Dialog ---------- */
@Composable
fun AddDialog(onDismiss: () -> Unit, onAdd: (String, LocalDate) -> Unit) {
    var name by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("01.01.2000") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Geburtstag hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Vollständiger Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; error = null },
                    label = { Text("Geburtstag (tt.mm.jjjj)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val date = runCatching { LocalDate.parse(dateText, displayFmt) }.getOrNull()
                if (name.isBlank() || date == null) {
                    error = "Bitte Name und gültiges Datum eingeben."
                    return@TextButton
                }
                onAdd(name.trim(), date)
            }) {
                Text("Hinzufügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/** ---------- Helpers ---------- */
fun nextOccurrence(birthDate: LocalDate, today: LocalDate): LocalDate {
    val thisYear = birthDate.withYear(today.year)
    return if (!thisYear.isBefore(today)) thisYear else thisYear.plusYears(1)
}

fun daysUntil(birthDate: LocalDate, today: LocalDate): Long {
    val next = nextOccurrence(birthDate, today)
    return ChronoUnit.DAYS.between(today, next)
}

fun scheduleDailyWorker(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
        val (hour, min) = BirthdayStore.loadNotifyTime(context)

        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(min).withSecond(0)

        if (next.isBefore(now)) next = next.plusDays(1)

        val delayMs = Duration.between(now, next).toMillis()

        val req = PeriodicWorkRequestBuilder<BirthdayWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "birthday_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }
}