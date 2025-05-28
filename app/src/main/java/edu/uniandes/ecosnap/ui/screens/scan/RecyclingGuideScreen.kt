package edu.uniandes.ecosnap.ui.screens.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.uniandes.ecosnap.Analytics
import edu.uniandes.ecosnap.domain.model.RecyclingGuideItem
import edu.uniandes.ecosnap.R

@Composable
fun RecyclingGuideScreen(
    onNavigateBack: () -> Unit,
    onCameraScanClick: () -> Unit,
    viewModel: RecyclingGuideViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(onCameraScanClick)
        TitleBar(
            onNavigateBack = onNavigateBack,
            isOfflineMode = uiState.isOfflineMode,
            onRefresh = { viewModel.refreshFromNetwork() }
        )

        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) }
        )

        CategorySelector(
            categories = uiState.categories,
            selectedCategory = uiState.selectedCategory,
            onCategorySelected = { viewModel.selectCategory(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error!!,
                        color = Color.Red,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                uiState.filteredItems.isEmpty() && uiState.searchQuery.isEmpty() && uiState.selectedCategory == null -> {
                    Text(
                        text = "No hay información de reciclaje disponible",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                uiState.filteredItems.isEmpty() -> {
                    Text(
                        text = "No se encontraron resultados para tu búsqueda",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    RecyclingGuideList(uiState.filteredItems)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Buscar guía de reciclaje...") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color(0xFF00C853)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    Analytics.buttonEvent("search", "recycling_guide_screen")
                    onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = Color.Gray
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = Color(0xFF00C853),
            unfocusedBorderColor = Color.LightGray
        )
    )
}

@Composable
private fun CategorySelector(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    if (categories.isEmpty()) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            CategoryChip(
                name = "Todos",
                isSelected = selectedCategory == null,
                onClick = {
                    Analytics.buttonEvent("category_selected", "recycling_guide_screen")
                    onCategorySelected(null) }
            )
        }

        items(categories) { category ->
            CategoryChip(
                name = category,
                isSelected = category == selectedCategory,
                onClick = {
                    Analytics.buttonEvent("category_selected", "recycling_guide_screen")
                    onCategorySelected(category) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF00C853) else Color.White
    val textColor = if (isSelected) Color.White else Color.Black
    val borderColor = if (isSelected) Color(0xFF00C853) else Color.LightGray

    SuggestionChip(
        onClick = onClick,
        label = {
            Text(
                text = name,
                color = textColor
            )
        },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = backgroundColor
        ),
        modifier = Modifier.height(32.dp)
    )
}

@Composable
private fun RecyclingGuideList(items: List<RecyclingGuideItem>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        items(items) { item ->
            RecyclingGuideCard(item)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RecyclingGuideCard(item: RecyclingGuideItem) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Waste type icon based on category
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = when (item.category) {
                                "Plástico" -> Color(0xFF00BCD4)
                                "Papel" -> Color(0xFFFFEB3B)
                                "Vidrio" -> Color(0xFF4CAF50)
                                "Metal" -> Color(0xFFFF9800)
                                "Orgánico" -> Color(0xFF8BC34A)
                                "Electrónico" -> Color(0xFF9C27B0)
                                else -> Color(0xFF757575)
                            },

                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Mostrar la primera letra de la categoría
                    Text(
                        text = item.category.first().toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = item.wasteType,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Mostrar menos" else "Mostrar más"
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))

                Divider()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.description,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (item.instructions.isNotEmpty()) {
                    Text(
                        text = "Instrucciones de reciclaje:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.instructions,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { /* Open external resource if available */ },
                        enabled = item.resourceUrl.isNotEmpty()
                    ) {
                        Text("Aprender más")
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleBar(
    onNavigateBack: () -> Unit,
    isOfflineMode: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.Black
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Guía de Reciclaje",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Text(
                text = "Aprende a reciclar correctamente",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        if (isOfflineMode) {
            Text(
                text = "Modo sin conexión",
                fontSize = 12.sp,
                color = Color(0xFFFF9800),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = Color(0xFF00C853)
            )
        }
    }
}

@Composable
private fun TopBar(onCameraScanClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF00C853))
            .padding(16.dp)
    ) {
        IconButton(
            onClick = { },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color.Black
            )
        }

        Button(
            onClick = onCameraScanClick,
            modifier = Modifier.align(Alignment.CenterEnd),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            )
        ) {
            Text(
                text = "Cámara",
                fontWeight = FontWeight.Bold
            )
        }
    }
}