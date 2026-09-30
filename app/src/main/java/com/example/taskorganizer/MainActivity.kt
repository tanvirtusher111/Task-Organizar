package com.example.taskorganizer

import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Category(val label: String) {
    PERSONAL("Personal"),
    WORK("Work"),
    SHOPPING("Shopping"),
    STUDY("Study")
}

enum class Priority(
    val label: String,
    val badgeColor: Color,
    val cardBgColor: Color,
    val borderColor: Color,
    val weight: Int
) {
    HIGH("High", Color(0xFFFF5252), Color(0xFF2C1E21), Color(0xFFFF5252), 3),
    MEDIUM("Med", Color(0xFFFFB74D), Color(0xFF2D261E), Color(0xFFFFB74D), 2),
    LOW("Low", Color(0xFF81C784), Color(0xFF1E2B22), Color(0xFF81C784), 1)
}

enum class TaskFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    COMPLETED("Completed")
}

data class TaskItem(
    val id: Int,
    val title: String,
    val isCompleted: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val category: Category = Category.PERSONAL,
    val imageUriString: String? = null,
    val createdDate: String,
    val deadlineDate: String? = null
)

object TaskStorage {
    private const val PREFS_NAME = "task_organizer_prefs"
    private const val TASKS_KEY = "saved_tasks_list"

    fun saveTasks(context: Context, tasks: List<TaskItem>) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(tasks)
        sharedPreferences.edit().putString(TASKS_KEY, json).apply()
    }

    fun loadTasks(context: Context): List<TaskItem> {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sharedPreferences.getString(TASKS_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<TaskItem>>() {}.type
        return try {
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    primary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    TaskManagerApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskManagerApp() {
    val context = LocalContext.current

    var tasks by remember { mutableStateOf(TaskStorage.loadTasks(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSheetOpen by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var nextId by remember { mutableIntStateOf((tasks.maxOfOrNull { it.id } ?: 0) + 1) }

    // Input States for New Task
    var taskInputText by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(Priority.MEDIUM) }
    var selectedCategory by remember { mutableStateOf(Category.PERSONAL) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedDeadline by remember { mutableStateOf<String?>(null) }

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedDate = String.format(Locale.getDefault(), "%02d/%02d/%d", month + 1, dayOfMonth, year)
            selectedDeadline = formattedDate
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedImageUri = uri }

    LaunchedEffect(tasks) {
        TaskStorage.saveTasks(context, tasks)
    }

    val activeCount = tasks.count { !it.isCompleted }

    val filteredTasks = tasks.filter { task ->
        val matchesSearch = task.title.contains(searchQuery, ignoreCase = true)
        val matchesStatus = when (activeFilter) {
            TaskFilter.ALL -> true
            TaskFilter.PENDING -> !task.isCompleted
            TaskFilter.COMPLETED -> task.isCompleted
        }
        matchesSearch && matchesStatus
    }.sortedByDescending { it.priority.weight }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search...", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(0.85f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    } else {
                        Column {
                            Text("Task Organizer", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("$activeCount task(s) remaining", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search Toggle",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isSheetOpen = true },
                containerColor = Color.White,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Filter Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TaskFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = (activeFilter == filter),
                            onClick = { activeFilter = filter },
                            label = { Text(filter.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF444444),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E1E1E),
                                labelColor = Color.Gray
                            )
                        )
                    }
                }

                if (tasks.any { it.isCompleted }) {
                    TextButton(onClick = { tasks = tasks.filter { !it.isCompleted } }) {
                        Text("Clear Completed", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Task List
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matching tasks found!", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggleComplete = { toggledTask ->
                                tasks = tasks.map {
                                    if (it.id == toggledTask.id) it.copy(isCompleted = !it.isCompleted)
                                    else it
                                }
                            },
                            onDeleteTask = { taskToDelete ->
                                tasks = tasks.filter { it.id != taskToDelete.id }
                            },
                            onUpdateTitle = { updatedTask, newTitle ->
                                tasks = tasks.map {
                                    if (it.id == updatedTask.id) it.copy(title = newTitle)
                                    else it
                                }
                            }
                        )
                    }
                }
            }

            // Expandable Bottom Sheet / Popup for Adding Tasks
            if (isSheetOpen) {
                ModalBottomSheet(
                    onDismissRequest = { isSheetOpen = false },
                    containerColor = Color(0xFF1E1E1E)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Add New Task",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = taskInputText,
                            onValueChange = { taskInputText = it },
                            label = { Text("Enter task description...", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color(0xFF444444),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category Selection
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Category: ", fontSize = 13.sp, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Category.entries.forEach { cat ->
                                    FilterChip(
                                        selected = (selectedCategory == cat),
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat.label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.White,
                                            selectedLabelColor = Color.Black,
                                            containerColor = Color(0xFF2C2C2C),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Priority Selection
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Priority: ", fontSize = 13.sp, color = Color.White)
                            Priority.entries.forEach { p ->
                                FilterChip(
                                    selected = (selectedPriority == p),
                                    onClick = { selectedPriority = p },
                                    label = { Text(p.label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = p.badgeColor,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF2C2C2C),
                                        labelColor = Color.White
                                    ),
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Attachment Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444))
                            ) {
                                Text(if (selectedImageUri == null) "Photo" else "Change Photo", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { datePickerDialog.show() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444))
                            ) {
                                Text(if (selectedDeadline == null) "Set Deadline" else selectedDeadline!!, fontSize = 12.sp)
                            }

                            if (selectedDeadline != null) {
                                TextButton(onClick = { selectedDeadline = null }) {
                                    Text("Clear", color = Color(0xFFFF5252), fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (taskInputText.isNotBlank()) {
                                    val currentDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
                                    tasks = tasks + TaskItem(
                                        id = nextId++,
                                        title = taskInputText,
                                        priority = selectedPriority,
                                        category = selectedCategory,
                                        imageUriString = selectedImageUri?.toString(),
                                        createdDate = currentDate,
                                        deadlineDate = selectedDeadline
                                    )
                                    taskInputText = ""
                                    selectedImageUri = null
                                    selectedDeadline = null
                                    isSheetOpen = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                        ) {
                            Text("Save Task", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskRow(
    task: TaskItem,
    onToggleComplete: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onUpdateTitle: (TaskItem, String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedTitle by remember(task.title) { mutableStateOf(task.title) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (task.isCompleted) 0.4f else 1.0f)
            .border(
                width = 1.dp,
                color = if (task.isCompleted) Color(0xFF333333) else task.priority.borderColor,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) Color(0xFF1E1E1E) else task.priority.cardBgColor
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { onToggleComplete(task) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.White,
                        uncheckedColor = Color.Gray
                    )
                )

                Box(
                    modifier = Modifier
                        .size(width = 6.dp, height = 24.dp)
                        .background(task.priority.badgeColor, shape = RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedTitle,
                            onValueChange = { editedTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    } else {
                        Text(
                            text = task.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (task.isCompleted) Color.Gray else Color.White,
                            modifier = Modifier.clickable { isEditing = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF333333),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = task.category.label,
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "Created: ${task.createdDate}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )

                        task.deadlineDate?.let { deadline ->
                            Text(
                                text = "Due: $deadline",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8A80)
                            )
                        }
                    }
                }

                Row {
                    if (isEditing) {
                        TextButton(onClick = {
                            onUpdateTitle(task, editedTitle)
                            isEditing = false
                        }) {
                            Text("Save", color = Color(0xFF81C784))
                        }
                    }

                    TextButton(onClick = { onDeleteTask(task) }) {
                        Text("Delete", color = Color(0xFFFF5252))
                    }
                }
            }

            task.imageUriString?.let { uriStr ->
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = Uri.parse(uriStr),
                    contentDescription = "Task photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}