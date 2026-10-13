package com.example.ui.admin

import com.example.data.engine.UnitConverter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private data class RecipeComponentDraft(
    val sourceId: String,
    val amountText: String,
    val sourceType: String = "RAW",
    val unit: String = ""
)

@Composable
fun AdminRecipesTab(
    recipes: List<RecipeEntity>,
    rawMaterials: List<RawMaterialEntity>,
    products: List<ProductEntity> = emptyList(),
    onAddRecipe: (name: String, productId: String, items: List<RecipeItemEntity>, notes: String) -> Unit = { _, _, _, _ -> },
    mixtures: List<MixtureEntity> = emptyList()
) {
    fun initialComponent(): RecipeComponentDraft = when {
        rawMaterials.isNotEmpty() -> RecipeComponentDraft(rawMaterials.first().id, "30", "RAW", rawMaterials.first().baseUnit)
        mixtures.isNotEmpty() -> RecipeComponentDraft(mixtures.first().id, "30", "MIXTURE", mixtures.first().unit)
        else -> RecipeComponentDraft("", "30", "RAW")
    }

    var showDialog by remember { mutableStateOf(false) }
    var recipeName by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf(products.firstOrNull()?.id ?: "") }
    var recipeNotes by remember { mutableStateOf("") }
    var recipeError by remember { mutableStateOf<String?>(null) }
    var components by remember {
        mutableStateOf(listOf(initialComponent()))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الوصفات والخلطات (" + recipes.size + ")", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(
                onClick = {
                    recipeName = ""
                    recipeNotes = ""
                    recipeError = null
                    selectedProductId = products.firstOrNull()?.id.orEmpty()
                    components = listOf(initialComponent())
                    showDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة وصفة")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(recipes) { recipe ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(recipe.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "المنتج: " + (products.firstOrNull { it.id == recipe.productId }?.name ?: "غير معروف") +
                                " • الإصدار " + recipe.version +
                                " • التكلفة: " + recipe.calculatedCost + " ريال",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (recipe.notes.isNotBlank()) {
                            Text(recipe.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var productMenuExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("إنشاء وصفة متعددة المكونات") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = recipeName,
                        onValueChange = { recipeName = it; recipeError = null },
                        label = { Text("اسم الوصفة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("المنتج المرتبط بالوصفة", fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { productMenuExpanded = true },
                            enabled = products.any { it.isActive && it.isAvailable },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(products.firstOrNull { it.id == selectedProductId }?.name ?: "اختر المنتج")
                        }
                        DropdownMenu(
                            expanded = productMenuExpanded,
                            onDismissRequest = { productMenuExpanded = false }
                        ) {
                            products.filter { it.isActive && it.isAvailable }.forEach { product ->
                                DropdownMenuItem(
                                    text = { Text(product.name) },
                                    onClick = {
                                        selectedProductId = product.id
                                        productMenuExpanded = false
                                        recipeError = null
                                    }
                                )
                            }
                        }
                    }

                    Divider()
                    Text("مكونات الوصفة", fontWeight = FontWeight.SemiBold)
                    components.forEachIndexed { index, draft ->
                        var sourceMenuExpanded by remember(index) { mutableStateOf(false) }
                        val chosenMaterial = if (draft.sourceType == "RAW") {
                            rawMaterials.firstOrNull { it.id == draft.sourceId }
                        } else null
                        val chosenMixture = if (draft.sourceType == "MIXTURE") {
                            mixtures.firstOrNull { it.id == draft.sourceId }
                        } else null
                        val hasSources = if (draft.sourceType == "RAW") rawMaterials.isNotEmpty() else mixtures.isNotEmpty()
                        val chosenName = chosenMaterial?.name ?: chosenMixture?.name ?: "اختر المكوّن"
                        val chosenBaseUnit = chosenMaterial?.baseUnit ?: chosenMixture?.unit ?: "وحدة"
                        val selectedUnit = draft.unit.ifBlank { chosenBaseUnit }
                        var unitMenuExpanded by remember(index) { mutableStateOf(false) }
                        val unitOptions = runCatching { UnitConverter.compatibleUnits(chosenBaseUnit) }
                            .getOrElse { listOf(chosenBaseUnit) }

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val nextType = if (draft.sourceType == "RAW") "MIXTURE" else "RAW"
                                        val nextId = if (nextType == "RAW") {
                                            rawMaterials.firstOrNull()?.id.orEmpty()
                                        } else {
                                            mixtures.firstOrNull()?.id.orEmpty()
                                        }
                                        components = components.mapIndexed { i, old ->
                                            if (i == index) {
                                                val nextUnit = if (nextType == "RAW") rawMaterials.firstOrNull()?.baseUnit.orEmpty()
                                                else mixtures.firstOrNull()?.unit.orEmpty()
                                                old.copy(sourceId = nextId, sourceType = nextType, unit = nextUnit)
                                            } else old
                                        }
                                        recipeError = null
                                    },
                                    enabled = if (draft.sourceType == "RAW") mixtures.isNotEmpty() else rawMaterials.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (draft.sourceType == "RAW") "المكوّن الحالي: مادة خام • اضغط لاختيار خلطة" else "المكوّن الحالي: خلطة • اضغط لاختيار مادة خام")
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedButton(
                                            onClick = { sourceMenuExpanded = true },
                                            enabled = hasSources,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(chosenName + " (" + selectedUnit + ")")
                                        }
                                        DropdownMenu(
                                            expanded = sourceMenuExpanded,
                                            onDismissRequest = { sourceMenuExpanded = false }
                                        ) {
                                            if (draft.sourceType == "RAW") {
                                                rawMaterials.forEach { material ->
                                                    DropdownMenuItem(
                                                        text = { Text(material.name + " (" + material.baseUnit + ")") },
                                                        onClick = {
                                                            components = components.mapIndexed { i, old ->
                                                                if (i == index) old.copy(sourceId = material.id, unit = material.baseUnit) else old
                                                            }
                                                            sourceMenuExpanded = false
                                                            recipeError = null
                                                        }
                                                    )
                                                }
                                            } else {
                                                mixtures.forEach { mixture ->
                                                    DropdownMenuItem(
                                                        text = { Text(mixture.name + " (" + mixture.unit + ")") },
                                                        onClick = {
                                                            components = components.mapIndexed { i, old ->
                                                                if (i == index) old.copy(sourceId = mixture.id, unit = mixture.unit) else old
                                                            }
                                                            sourceMenuExpanded = false
                                                            recipeError = null
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    IconButton(
                                        enabled = components.size > 1,
                                        onClick = {
                                            components = components.filterIndexed { i, _ -> i != index }
                                            recipeError = null
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف المكوّن")
                                    }
                                }

                                OutlinedTextField(
                                    value = draft.amountText,
                                    onValueChange = { value ->
                                        components = components.mapIndexed { i, old ->
                                            if (i == index) old.copy(amountText = value) else old
                                        }
                                        recipeError = null
                                    },
                                    label = { Text("الكمية لكل حصة (" + selectedUnit + ")") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { unitMenuExpanded = true },
                                        enabled = chosenMaterial != null || chosenMixture != null,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("وحدة قياس الكمية: " + selectedUnit)
                                    }
                                    DropdownMenu(
                                        expanded = unitMenuExpanded,
                                        onDismissRequest = { unitMenuExpanded = false }
                                    ) {
                                        unitOptions.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option) },
                                                onClick = {
                                                    components = components.mapIndexed { i, old ->
                                                        if (i == index) old.copy(unit = option) else old
                                                    }
                                                    unitMenuExpanded = false
                                                    recipeError = null
                                                }
                                            )
                                        }
                                    }
                                }
                                val amount = draft.amountText.toDoubleOrNull() ?: 0.0
                                val estimate = runCatching {
                                    when {
                                        chosenMaterial != null ->
                                            UnitConverter.convert(amount, selectedUnit, chosenMaterial.baseUnit) * chosenMaterial.avgCostPerUnit
                                        chosenMixture != null && chosenMixture.unitCost.isFinite() ->
                                            UnitConverter.convert(amount, selectedUnit, chosenMixture.unit) * chosenMixture.unitCost
                                        else -> 0.0
                                    }
                                }.getOrDefault(0.0)
                                if (chosenMaterial != null || chosenMixture != null) {
                                    Text(
                                        "تكلفة تقديرية للمكوّن: " + estimate + " ريال",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            when {
                                rawMaterials.isNotEmpty() -> {
                                    components = components + RecipeComponentDraft(rawMaterials.first().id, "30", "RAW", rawMaterials.first().baseUnit)
                                    recipeError = null
                                }
                                mixtures.isNotEmpty() -> {
                                    components = components + RecipeComponentDraft(mixtures.first().id, "30", "MIXTURE", mixtures.first().unit)
                                    recipeError = null
                                }
                                else -> recipeError = "أضف مادة خام أو خلطة أولًا"
                            }
                        },
                        enabled = rawMaterials.isNotEmpty() || mixtures.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة مكوّن آخر")
                    }

                    OutlinedTextField(
                        value = recipeNotes,
                        onValueChange = { recipeNotes = it },
                        label = { Text("طريقة التحضير والملاحظات") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (recipeError != null) {
                        Text(recipeError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedItems = components.mapNotNull { draft ->
                            val amount = draft.amountText.toDoubleOrNull()
                            if (amount == null || !amount.isFinite() || amount <= 0.0) {
                                null
                            } else if (draft.sourceType == "RAW") {
                                val material = rawMaterials.firstOrNull { it.id == draft.sourceId }
                                if (material == null) {
                                    recipeError = "اختر مادة خام صالحة لكل مكوّن"
                                    null
                                } else {
                                    RecipeItemEntity(
                                        recipeId = "",
                                        rawMaterialId = material.id,
                                        mixtureId = "",
                                        name = material.name,
                                        amount = amount,
                                        quantity = amount,
                                        unit = draft.unit.ifBlank { material.baseUnit },
                                        costContribution = runCatching {
                                            UnitConverter.convert(amount, draft.unit.ifBlank { material.baseUnit }, material.baseUnit) * material.avgCostPerUnit
                                        }.getOrDefault(0.0)
                                    )
                                }
                            } else {
                                val mixture = mixtures.firstOrNull { it.id == draft.sourceId }
                                if (mixture == null) {
                                    recipeError = "اختر خلطة صالحة لكل مكوّن"
                                    null
                                } else {
                                    RecipeItemEntity(
                                        recipeId = "",
                                        rawMaterialId = "",
                                        mixtureId = mixture.id,
                                        name = mixture.name,
                                        amount = amount,
                                        quantity = amount,
                                        unit = draft.unit.ifBlank { mixture.unit },
                                        costContribution = runCatching {
                                            UnitConverter.convert(amount, draft.unit.ifBlank { mixture.unit }, mixture.unit) * mixture.unitCost
                                        }.getOrDefault(0.0)
                                    )
                                }
                            }
                        }
                        when {
                            recipeName.isBlank() -> recipeError = "اكتب اسم الوصفة"
                            selectedProductId.isBlank() -> recipeError = "اختر المنتج المرتبط بالوصفة"
                            rawMaterials.isEmpty() && mixtures.isEmpty() -> recipeError = "أضف المواد الخام أو الخلطات أولًا"
                            parsedItems.size != components.size -> recipeError = "راجع كميات ومكونات الوصفة؛ يجب أن تكون كل الكميات أكبر من صفر"
                            else -> {
                                onAddRecipe(recipeName.trim(), selectedProductId, parsedItems, recipeNotes.trim())
                                showDialog = false
                            }
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
