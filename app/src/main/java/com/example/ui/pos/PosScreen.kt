package com.example.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.CartItem
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.ShiftStatus
import com.example.security.AppPermission
import com.example.security.PermissionChecker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    currentUser: UserEntity,
    currentShift: ShiftEntity?,
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    cartItems: List<CartItem>,
    onAddToCart: (ProductEntity) -> Unit,
    onRemoveFromCart: (ProductEntity) -> Unit,
    onClearCart: () -> Unit,
    onOpenPaymentDialog: () -> Unit,
    onOpenStartShift: () -> Unit,
    onOpenCloseShift: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onLockSession: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredProducts = remember(products, selectedCategoryId, searchQuery) {
        products.filter { p ->
            val matchCat = selectedCategoryId == null || p.categoryId == selectedCategoryId
            val matchQuery = searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) || (p.barcode.isNotBlank() && p.barcode.contains(searchQuery, ignoreCase = true)) || (p.sku.isNotBlank() && p.sku.contains(searchQuery, ignoreCase = true))
            matchCat && matchQuery && p.isActive
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("POWER FEUL POS", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("المحاسب: ${currentUser.name} (${currentUser.role.titleAr})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    // Shift indicator
                    if (currentShift != null && currentShift.status == ShiftStatus.OPEN) {
                        AssistChip(
                            onClick = onOpenCloseShift,
                            label = { Text("شفت #${currentShift.shiftNumber} مفتوح", color = Color(0xFF2E7D32)) },
                            leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp)) }
                        )
                    } else {
                        Button(
                            onClick = onOpenStartShift,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text("فتح شفت جديد", fontSize = 12.sp)
                        }
                    }

                    // Admin Button (if has permission)
                    if (PermissionChecker.hasPermission(currentUser.role, AppPermission.ACCESS_ADMIN)) {
                        IconButton(onClick = onNavigateToAdmin) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "الإدارة")
                        }
                    }

                    // Lock Session
                    IconButton(onClick = onLockSession) {
                        Icon(Icons.Default.Lock, contentDescription = "قفل مؤقت")
                    }

                    // Logout
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "خروج")
                    }
                }
            )
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // LEFT PANEL: Product Catalog (2/3 width)
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث عن منتج...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Categories Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("الكل") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Product Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredProducts) { prod ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddToCart(prod) }
                                .testTag("product_card_${prod.id}"),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = prod.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${prod.price} ريال",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // RIGHT PANEL: Cart & Invoice Summary (1/3 width)
            Card(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("سلة الفاتورة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (cartItems.isNotEmpty()) {
                            TextButton(onClick = onClearCart) {
                                Text("إفراغ", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    if (cartItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("السلة فارغة، اختر منتجات للإضافة", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(cartItems) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.product.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("${item.unitPrice} × ${item.quantity} = ${item.totalPrice} ريال", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onRemoveFromCart(item.product) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "إنقاص", tint = MaterialTheme.colorScheme.error)
                                        }
                                        Text("${item.quantity}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                        IconButton(
                                            onClick = { onAddToCart(item.product) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = "زيادة", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    val total = cartItems.sumOf { it.totalPrice }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الإجمالي الصافي:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("$total ريال", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onOpenPaymentDialog,
                        enabled = cartItems.isNotEmpty() && currentShift?.status == ShiftStatus.OPEN,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("checkout_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("إتمام البيع والدفع", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    if (currentShift?.status != ShiftStatus.OPEN) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "يجب فتح شفت قبل إتمام عمليات البيع",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}
