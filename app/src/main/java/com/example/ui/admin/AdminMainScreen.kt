package com.example.ui.admin

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
import com.example.domain.model.UserRole
import com.example.domain.model.WalletTransferStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMainScreen(
    currentUser: UserEntity,
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    rawMaterials: List<RawMaterialEntity>,
    recipes: List<RecipeEntity>,
    mixtures: List<MixtureEntity> = emptyList(),
    sales: List<SaleEntity>,
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
    onAddProduct: (name: String, catId: String, price: Double, cost: Double) -> Unit,
    onAddRecipe: (name: String, productId: String, items: List<RecipeItemEntity>, notes: String) -> Unit = { _, _, _, _ -> },
    onAddCustomer: (name: String, phone: String, limit: Double, allowDebt: Boolean) -> Unit = { _, _, _, _ -> },
    onAddExpense: (title: String, amount: Double, category: String, paidTo: String, notes: String) -> Unit = { _, _, _, _, _ -> },
    onAddRawMaterial: (name: String, unit: String, stock: Double, minStock: Double, cost: Double) -> Unit = { _, _, _, _, _ -> },
    onAddStock: (materialId: String, amount: Double) -> Unit = { _, _ -> },
    onToggleProductActive: (productId: String, active: Boolean) -> Unit = { _, _ -> },
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
    onExportSalesCsv: () -> Unit,
    onCreateBackupJson: () -> Unit,
    insights: List<com.example.data.engine.AiInsight> = emptyList(),
    onAskAi: suspend (String) -> String = { "" },
    onSaveApiKey: (String) -> Unit = {},
    onSaveSetting: (key: String, value: String) -> Unit = { _, _ -> }
) {
    var selectedTab by remember { mutableStateOf(AdminTab.PRODUCTS) }

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
                AdminTab.values().forEach { tab ->
                    NavigationRailItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.titleAr, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        icon = {
                            Icon(
                                when (tab) {
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
                when (selectedTab) {
                    AdminTab.PRODUCTS -> AdminProductsTab(categories, products, onAddProduct, onToggleProductActive)
                    AdminTab.RECIPES -> AdminRecipesTab(recipes, rawMaterials, products, onAddRecipe, mixtures)
                    AdminTab.INVENTORY -> AdminInventoryTab(rawMaterials, onAddStock, onAddRawMaterial)
                    AdminTab.SALES -> AdminSalesTab(sales, onVoidSale)
                    AdminTab.PURCHASES -> AdminPurchasesTab(purchases, suppliers)
                    AdminTab.DEBTS -> AdminDebtsTab(customers, onAddCustomer)
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
                    AdminTab.BACKUP -> AdminBackupTab(onExportSalesCsv, onCreateBackupJson, backupStatusMessage)
                    AdminTab.EMPLOYEES -> AdminEmployeesTab(
                        users = users,
                        onAddEmployee = onAddEmployee,
                        onToggleActive = onToggleActive,
                        onRemoveUser = onRemoveUser,
                        canManageUsers = currentUser.role == UserRole.OWNER
                    )
                    AdminTab.REPORTS -> AdminReportsTab(sales, expenses)
                    AdminTab.AUDIT_LOG -> AdminAuditLogTab(logs)
                    AdminTab.SETTINGS -> AdminSettingsTab(users, onSaveSetting)
                }
            }
        }
    }
}
