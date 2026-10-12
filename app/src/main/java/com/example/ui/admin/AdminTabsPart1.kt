package com.example.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.*

@Composable
fun AdminProductsTab(
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    onAddProduct: (name: String, catId: String, price: Double, cost: Double) -> Unit,
    onToggleProductActive: (productId: String, isActive: Boolean) -> Unit = { _, _ -> }
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("إدارة المنتجات (${products.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة منتج")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(products) { p ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("السعر: ${p.price} ريال • التكلفة: ${p.costPrice} ريال • الحالة: ${if (p.isActive) "نشط (معروض للكاشير)" else "معطل"}", fontSize = 12.sp, color = if (p.isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        }
                        Switch(
                            checked = p.isActive,
                            onCheckedChange = { onToggleProductActive(p.id, it) }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("إضافة منتج جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المنتج") }, singleLine = true)
                    OutlinedTextField(value = priceText, onValueChange = { priceText = it }, label = { Text("سعر البيع (ريال)") }, singleLine = true)
                    OutlinedTextField(value = costText, onValueChange = { costText = it }, label = { Text("سعر التكلفة (ريال)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pr = priceText.toDoubleOrNull() ?: 0.0
                        val cs = costText.toDoubleOrNull() ?: 0.0
                        onAddProduct(name, selectedCatId, pr, cs)
                        showDialog = false
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun AdminRecipesTab(
    recipes: List<RecipeEntity>,
    rawMaterials: List<RawMaterialEntity>,
    products: List<ProductEntity> = emptyList(),
    onAddRecipe: (name: String, productId: String, items: List<RecipeItemEntity>, notes: String) -> Unit = { _, _, _, _ -> }
) {
    var showDialog by remember { mutableStateOf(false) }
    var recipeName by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf(products.firstOrNull()?.id ?: "") }
    var selectedMaterialId by remember { mutableStateOf(rawMaterials.firstOrNull()?.id ?: "") }
    var materialAmountText by remember { mutableStateOf("30") }
    var recipeNotes by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الوصفات والخلطات (${recipes.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة وصفة")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(recipes) { r ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(r.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("التكلفة المحسوبة: ${r.calculatedCost} ريال • ${if (r.notes.isNotEmpty()) r.notes else "وصفة تصنيع نشطة"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("إضافة وصفة جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = recipeName,
                        onValueChange = { recipeName = it },
                        label = { Text("اسم الوصفة (مثال: بروتين شيك فانيلا)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = materialAmountText,
                        onValueChange = { materialAmountText = it },
                        label = { Text("كمية المادة الخام المستهلكة (جرام/مل)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = recipeNotes,
                        onValueChange = { recipeNotes = it },
                        label = { Text("طريقة التحضير والتعليمات") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = materialAmountText.toDoubleOrNull() ?: 30.0
                        val chosenMat = rawMaterials.firstOrNull { it.id == selectedMaterialId } ?: rawMaterials.firstOrNull()
                        val unitCost = chosenMat?.avgCostPerUnit ?: 0.0
                        val items = if (chosenMat != null) {
                            listOf(
                                RecipeItemEntity(
                                    recipeId = "",
                                    rawMaterialId = chosenMat.id,
                                    name = chosenMat.name,
                                    amount = amount,
                                    quantity = amount,
                                    unit = chosenMat.baseUnit,
                                    costContribution = unitCost * amount
                                )
                            )
                        } else emptyList()
                        if (recipeName.isNotBlank()) {
                            onAddRecipe(recipeName, selectedProductId, items, recipeNotes)
                            showDialog = false
                            recipeName = ""
                        }
                    }
                ) {
                    Text("حفظ الوصفة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun AdminSalesTab(
    sales: List<SaleEntity>,
    onVoidSale: (saleId: String, reason: String) -> Unit
) {
    var voidingSaleId by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("سجل المبيعات والفواتير (${sales.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sales) { s ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("فاتورة: ${s.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("المبلغ: ${s.netAmount} ريال • ${s.paymentMethod.titleAr} • المحاسب: ${s.userName}", fontSize = 12.sp)
                            if (s.status == "VOIDED") {
                                Text("ملغاة: ${s.voidReason}", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                        if (s.status != "VOIDED") {
                            OutlinedButton(onClick = { voidingSaleId = s.id }) {
                                Text("إلغاء الفاتورة", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (voidingSaleId != null) {
        AlertDialog(
            onDismissRequest = { voidingSaleId = null },
            title = { Text("إلغاء واسترجاع الفاتورة") },
            text = {
                OutlinedTextField(
                    value = voidReason,
                    onValueChange = { voidReason = it },
                    label = { Text("سبب الإلغاء") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onVoidSale(voidingSaleId!!, voidReason)
                        voidingSaleId = null
                        voidReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("تأكيد الإلغاء واسترجاع المخزون")
                }
            },
            dismissButton = {
                TextButton(onClick = { voidingSaleId = null }) { Text("إلغاء") }
            }
        )
    }
}
