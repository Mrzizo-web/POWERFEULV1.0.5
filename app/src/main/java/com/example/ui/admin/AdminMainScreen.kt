package com.example.ui.admin

import android.net.Uri

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.*
import com.example.data.engine.NewPurchaseItem
import com.example.domain.model.UserRole
import com.example.domain.model.WalletTransferStatus
import com.example.security.AppPermission
import com.example.security.PermissionChecker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMainScreen(
    currentUser: UserEntity,
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    rawMaterials: List<RawMaterialEntity>,
    recipes: List<RecipeEntity>,
    mixtures: List<MixtureEntity> = emptyList(),
    recipeItems: List<RecipeItemEntity> = emptyList(),
    sales: List<SaleEntity>,
    saleItems: List<SaleItemEntity> = emptyList(),
    purchases: List<PurchaseEntity>,
    suppliers: List<SupplierEntity>,
    customers: List<CustomerEntity>,
    expenses: List<ExpenseEntity>,
    shifts: List<ShiftEntity>,
    users: List<UserEntity>,
    logs: List<AuditLogEntity>,
    wallets: List<WalletEntity>,
    walletTransactions: List<WalletTransactionEntity>,
    smsInbox: List<WalletSmsEntity>,
    queueItems: List<GatewayQueueEntity> = emptyList(),
    isGatewayRunning: Boolean = false,
    gatewayIp: String = "127.0.0.1",
    gatewayPort: Int = 8080,
    backupStatusMessage: String?,
    onBack: () -> Unit,
    onSaveProduct: (ProductEntity) -> Unit,
    onCreatePurchase: (supplierId: String, supplierName: String, invoiceNumber: String, items: List<NewPurchaseItem>, notes: String) -> Unit,
    onAddSupplier: (name: String, phone: String, address: String, notes: String) -> Unit,
    onAddRecipe: (name: String, productId: String, items: List<RecipeItemEntity>, notes: String) -> Unit = { _, _, _, _ -> },
    onAddCustomer: (name: String, phone: String, limit: Double, allowDebt: Boolean) -> Unit = { _, _, _, _ -> },
    hasOpenShift: Boolean = false,
    onPayDebt: (customerId: String, amount: Double, isCash: Boolean, notes: String) -> Unit = { _, _, _, _ -> },
    onAddExpense: (title: String, amount: Double, category: String, paidTo: String, notes: String) -> Unit = { _, _, _, _, _ -> },
    onAddRawMaterial: (name: String, unit: String, stock: Double, minStock: Double, cost: Double) -> Unit = { _, _, _, _, _ -> },
    onAddStock: (materialId: String, amount: Double) -> Unit = { _, _ -> },
    onRecordWaste: (materialId: String, quantity: Double, unit: String, reason: String, notes: String) -> Unit = { _, _, _, _, _ -> },
    onApplyStockAdjustment: (materialId: String, actualStock: Double, reason: String) -> Unit = { _, _, _ -> },
    onToggleProductActive: (productId: String, active: Boolean) -> Unit = { _, _ -> },
    onToggleProductAvailability: (productId: String, available: Boolean) -> Unit = { _, _ -> },
    onVoidSale: (saleId: String, reason: String) -> Unit,
    onAddEmployee: (name: String, username: String, pin: String, role: UserRole, phone: String) -> Unit,
    onToggleActive: (userId: String, active: Boolean) -> Unit,
    onRemoveUser: (userId: String) -> Unit = {},
    onUpdateWalletStatus: (txId: String, newStatus: WalletTransferStatus, reason: String) -> Unit,
    onReassignShift: (txId: String, newShiftId: String, cashierId: String, cashierName: String, reason: String) -> Unit = { _, _, _, _, _ -> },
    onToggleGateway: () -> Unit = {},
    onRetryQueueItem: (String) -> Unit = {},
    onRetryAllQueue: () -> Unit = {},
    onClearCompletedQueue: () -> Unit = {},
    onIngestTestSms: (sender: String, body: String) -> Unit = { _, _ -> },
    onExportSalesCsv: (Uri) -> Unit,
    onExportInventoryCsv: (Uri) -> Unit,
    onExportCustomersCsv: (Uri) -> Unit = {},
    onExportExpensesCsv: (Uri) -> Unit = {},
    onExportDebtTransactionsCsv: (Uri) -> Unit = {},
    onCreateBackup: (Uri, String) -> Unit,
    onRestoreBackup: (Uri, String) -> Unit,
    insights: List<com.example.data.engine.AiInsight> = emptyList(),
    onAskAi: suspend (String) -> String = { "" },
    onSaveApiKey: (String) -> Unit = {},
    onSaveSetting: (key: String, value: String) -> Unit = { _, _ -> },
    autoLockMinutes: String = "5"
) {
    var selectedTab by remember { mutableStateOf(AdminTab.PRODUCTS) }
    val visibleTabs = visibleAdminTabs(currentUser.role)
    val effectiveTab = visibleTabs.firstOrNull { it == selectedTab } ?: visibleTabs.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("لوحة الإدارة الشاملة — POWER FEUL POS", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع إلى POS")
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
            // SIDEBAR NAVIGATION RAIL / TABS
            NavigationRail(
                modifier = Modifier
                    .width(160.dp)
                    .fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                visibleTabs.forEach { tab ->
                    NavigationRailItem(
                        selected = effectiveTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.titleAr, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        icon = {
                            Icon(
                                when (tab) {
                                    AdminTab.DASHBOARD -> Icons.Default.Home
                                    AdminTab.PRODUCTS -> Icons.Default.Category
                                    AdminTab.RECIPES -> Icons.Default.Restaurant
                                    AdminTab.INVENTORY -> Icons.Default.Inventory
                                    AdminTab.SALES -> Icons.Default.PointOfSale
                                    AdminTab.PURCHASES -> Icons.Default.ShoppingCart
                                    AdminTab.DEBTS -> Icons.Default.People
                                    AdminTab.EXPENSES -> Icons.Default.MoneyOff
                                    AdminTab.SHIFTS -> Icons.Default.AccessTime
                                    AdminTab.WALLETS -> Icons.Default.AccountBalanceWallet
                                    AdminTab.AI_ASSISTANT -> Icons.Default.AutoAwesome
                                    AdminTab.BACKUP -> Icons.Default.Backup
                                    AdminTab.EMPLOYEES -> Icons.Default.Badge
                                    AdminTab.REPORTS -> Icons.Default.Assessment
                                    AdminTab.AUDIT_LOG -> Icons.Default.Security
                                    AdminTab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = tab.titleAr
                            )
                        }
                    )
                }
                }
            }

            // MAIN CONTENT PANE
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (effectiveTab == null) {
                    Text(
                        text = "ليس لديك صلاحية الوصول إلى صفحات الإدارة",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp)
                    )
                } else when (effectiveTab) {
                    AdminTab.DASHBOARD -> AdminDashboardTab(
                        sales = sales,
                        saleItems = saleItems,
                        expenses = expenses,
                        rawMaterials = rawMaterials,
                        customers = customers,
                        shifts = shifts
                    )
                    AdminTab.PRODUCTS -> AdminProductsTab(
                        categories = categories,
                        products = products,
                        onSaveProduct = onSaveProduct,
                        onToggleProductActive = onToggleProductActive,
                        onToggleProductAvailability = onToggleProductAvailability
                    )
                    AdminTab.RECIPES -> AdminRecipesTab(recipes, rawMaterials, products, onAddRecipe, mixtures, recipeItems)
                    AdminTab.INVENTORY -> AdminInventoryTab(
                        rawMaterials = rawMaterials,
                        onAddStock = onAddStock,
                        onAddRawMaterial = onAddRawMaterial,
                        onRecordWaste = onRecordWaste,
                        onApplyStockAdjustment = onApplyStockAdjustment
                    )
                    AdminTab.SALES -> AdminSalesTab(sales, onVoidSale)
                    AdminTab.PURCHASES -> AdminPurchasesTab(
                        purchases = purchases,
                        suppliers = suppliers,
                        rawMaterials = rawMaterials,
                        onCreatePurchase = onCreatePurchase,
                        onAddSupplier = onAddSupplier
                    )
                    AdminTab.DEBTS -> AdminDebtsTab(
                        customers = customers,
                        hasOpenShift = hasOpenShift,
                        onAddCustomer = onAddCustomer,
                        onPayDebt = onPayDebt
                    )
                    AdminTab.EXPENSES -> AdminExpensesTab(expenses, onAddExpense)
                    AdminTab.SHIFTS -> AdminShiftsTab(shifts)
                    AdminTab.WALLETS -> AdminWalletTab(
                        wallets = wallets,
                        transactions = walletTransactions,
                        smsInbox = smsInbox,
                        queueItems = queueItems,
                        isGatewayRunning = isGatewayRunning,
                        gatewayIp = gatewayIp,
                        gatewayPort = gatewayPort,
                        shifts = shifts,
                        onToggleGateway = onToggleGateway,
                        onRetryQueueItem = onRetryQueueItem,
                        onRetryAllQueue = onRetryAllQueue,
                        onClearCompletedQueue = onClearCompletedQueue,
                        onIngestTestSms = onIngestTestSms,
                        onUpdateStatus = onUpdateWalletStatus,
                        onReassignShift = onReassignShift,
                        onSaveSetting = onSaveSetting
                    )
                    AdminTab.AI_ASSISTANT -> AdminAiTab(insights, onAskAi, onSaveApiKey)
                    AdminTab.BACKUP -> AdminBackupTab(
                        onExportSalesCsv = onExportSalesCsv,
                        onExportInventoryCsv = onExportInventoryCsv,
                        onExportCustomersCsv = onExportCustomersCsv,
                        onExportExpensesCsv = onExportExpensesCsv,
                        onExportDebtTransactionsCsv = onExportDebtTransactionsCsv,
                        onCreateBackup = onCreateBackup,
                        onRestoreBackup = onRestoreBackup,
                        backupStatusMessage = backupStatusMessage
                    )
                    AdminTab.EMPLOYEES -> AdminEmployeesTab(
                        users = users,
                        onAddEmployee = onAddEmployee,
                        onToggleActive = onToggleActive,
                        onRemoveUser = onRemoveUser,
                        canManageUsers = PermissionChecker.hasPermission(currentUser.role, AppPermission.MANAGE_EMPLOYEES),
                        allowedRoles = if (currentUser.role == UserRole.ADMIN) {
                            listOf(UserRole.CASHIER)
                        } else {
                            UserRole.values().filter { it != UserRole.OWNER }
                        }
                    )
                    AdminTab.REPORTS -> AdminReportsTab(sales, expenses, saleItems)
                    AdminTab.AUDIT_LOG -> AdminAuditLogTab(logs)
                    AdminTab.SETTINGS -> AdminSettingsTab(users, onSaveSetting, autoLockMinutes)
                }
            }
        }
    }
}
