package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.admin.AdminMainScreen
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.SessionLockDialog
import com.example.ui.pos.PaymentDialog
import com.example.ui.pos.PosScreen
import com.example.ui.pos.ReceiptDialog
import com.example.ui.shift.CloseShiftDialog
import com.example.ui.shift.HandoverDialog
import com.example.ui.shift.StartShiftDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.wallet.WalletScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val activeScreen by viewModel.activeScreen.collectAsStateWithLifecycle()
                    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
                    val isSessionLocked by viewModel.isSessionLocked.collectAsStateWithLifecycle()

                    val currentShift by viewModel.currentShift.collectAsStateWithLifecycle()
                    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
                    val categories by viewModel.categories.collectAsStateWithLifecycle()
                    val products by viewModel.products.collectAsStateWithLifecycle()
                    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
                    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
                    val mixtures by viewModel.mixtures.collectAsStateWithLifecycle()
                    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
                    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
                    val customers by viewModel.customers.collectAsStateWithLifecycle()
                    val eligibleDebtCustomers by viewModel.eligibleDebtCustomers.collectAsStateWithLifecycle()
                    val allShifts by viewModel.allShifts.collectAsStateWithLifecycle()
                    val sales by viewModel.sales.collectAsStateWithLifecycle()
                    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
                    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
                    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
                    val userProfiles by viewModel.userProfiles.collectAsStateWithLifecycle()
                    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
                    val walletTransactions by viewModel.walletTransactions.collectAsStateWithLifecycle()
                    val smsInbox by viewModel.smsInbox.collectAsStateWithLifecycle()
                    val aiInsights by viewModel.aiInsights.collectAsStateWithLifecycle()
                    val queueItems by viewModel.allQueueItems.collectAsStateWithLifecycle()
                    val isGatewayRunning by viewModel.isGatewayRunning.collectAsStateWithLifecycle()
                    val gatewayIp by viewModel.gatewayIp.collectAsStateWithLifecycle()
                    val gatewayPort by viewModel.gatewayPort.collectAsStateWithLifecycle()
                    val autoLockMinutes by viewModel.autoLockMinutes.collectAsStateWithLifecycle()

                    val showStartShiftDialog by viewModel.showStartShiftDialog.collectAsStateWithLifecycle()
                    val showCloseShiftDialog by viewModel.showCloseShiftDialog.collectAsStateWithLifecycle()
                    val showHandoverDialog by viewModel.showHandoverDialog.collectAsStateWithLifecycle()
                    val showPaymentDialog by viewModel.showPaymentDialog.collectAsStateWithLifecycle()
                    val lastCompletedSale by viewModel.lastCompletedSale.collectAsStateWithLifecycle()
                    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
                    val loginErrorMessage by viewModel.loginErrorMessage.collectAsStateWithLifecycle()
                    val backupStatusMessage by viewModel.backupStatusMessage.collectAsStateWithLifecycle()

                    val snackbarHostState = remember { SnackbarHostState() }

                    LaunchedEffect(snackbarMessage) {
                        val msg = snackbarMessage
                        if (!msg.isNullOrEmpty()) {
                            snackbarHostState.showSnackbar(msg)
                            viewModel.snackbarMessage.value = null
                        }
                    }

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { paddingValues ->
                        Surface(modifier = Modifier.padding(paddingValues)) {
                            when (activeScreen) {
                                AppScreen.LOGIN -> {
                                    LoginScreen(
                                        userProfiles = userProfiles,
                                        errorMessage = loginErrorMessage,
                                        onAuthenticate = { uId, pin ->
                                            viewModel.authenticate(uId, pin)
                                        },
                                        onClearError = {
                                            viewModel.loginErrorMessage.value = null
                                        }
                                    )
                                }
                                AppScreen.POS -> {
                                    val user = currentUser
                                    if (user != null) {
                                        PosScreen(
                                            currentUser = user,
                                            currentShift = currentShift,
                                            categories = categories,
                                            products = products,
                                            cartItems = cartItems,
                                            onAddToCart = { prod ->
                                                viewModel.onUserActivity()
                                                viewModel.addToCart(prod)
                                            },
                                            onRemoveFromCart = { prod ->
                                                viewModel.onUserActivity()
                                                viewModel.removeFromCart(prod)
                                            },
                                            onClearCart = {
                                                viewModel.onUserActivity()
                                                viewModel.clearCart()
                                            },
                                            onOpenStartShift = {
                                                viewModel.onUserActivity()
                                                viewModel.showStartShiftDialog.value = true
                                            },
                                            onOpenCloseShift = {
                                                viewModel.onUserActivity()
                                                viewModel.showCloseShiftDialog.value = true
                                            },
                                            onNavigateToAdmin = {
                                                viewModel.onUserActivity()
                                                viewModel.navigateTo(AppScreen.ADMIN)
                                            },
                                            onNavigateToWallets = {
                                                viewModel.onUserActivity()
                                                viewModel.navigateTo(AppScreen.WALLETS)
                                            },
                                            onLockSession = {
                                                viewModel.sessionManager.lockManually()
                                            },
                                            onOpenPaymentDialog = {
                                                viewModel.onUserActivity()
                                                viewModel.showPaymentDialog.value = true
                                            },
                                            onLogout = { viewModel.logout() }
                                        )
                                    }
                                }
                                AppScreen.ADMIN -> {
                                    val user = currentUser
                                    if (user != null) {
                                        AdminMainScreen(
                                            currentUser = user,
                                            categories = categories,
                                            products = products,
                                            rawMaterials = rawMaterials,
                                            recipes = recipes,
                                            mixtures = mixtures,
                                            sales = sales,
                                            purchases = purchases,
                                            suppliers = suppliers,
                                            customers = customers,
                                            expenses = expenses,
                                            shifts = allShifts,
                                            users = allUsers,
                                            logs = auditLogs,
                                            wallets = wallets,
                                            walletTransactions = walletTransactions,
                                            smsInbox = smsInbox,
                                            queueItems = queueItems,
                                            isGatewayRunning = isGatewayRunning,
                                            gatewayIp = gatewayIp,
                                            gatewayPort = gatewayPort,
                                            backupStatusMessage = backupStatusMessage,
                                            onBack = {
                                                viewModel.onUserActivity()
                                                viewModel.navigateTo(AppScreen.POS)
                                            },
                                            onAddProduct = { name, catId, price, cost ->
                                                viewModel.onUserActivity()
                                                viewModel.addProduct(name, catId, price, cost)
                                            },
                                            onToggleProductActive = { productId, active ->
                                                viewModel.onUserActivity()
                                                viewModel.toggleProductActive(productId, active)
                                            },
                                            onCreatePurchase = { supplierId, supplierName, invoiceNumber, items, notes ->
                                                viewModel.onUserActivity()
                                                viewModel.addPurchase(supplierId, supplierName, invoiceNumber, items, notes)
                                            },
                                            onAddSupplier = { name, phone, address, notes ->
                                                viewModel.onUserActivity()
                                                viewModel.addSupplier(name, phone, address, notes)
                                            },
                                            onAddRawMaterial = { name, unit, stock, min, cost ->
                                                viewModel.onUserActivity()
                                                viewModel.addRawMaterial(name, unit, stock, min, cost)
                                            },
                                            onAddStock = { matId, amount ->
                                                viewModel.onUserActivity()
                                                viewModel.addRawMaterialStock(matId, amount)
                                            },
                                            onAddRecipe = { name, prodId, items, notes ->
                                                viewModel.onUserActivity()
                                                viewModel.addRecipe(name, prodId, items, notes)
                                            },
                                            onAddCustomer = { name, phone, limit, allowDebt ->
                                                viewModel.onUserActivity()
                                                viewModel.addCustomer(name, phone, limit, allowDebt)
                                            },
                                            hasOpenShift = currentShift != null,
                                            onPayDebt = { customerId, amount, isCash, notes ->
                                                viewModel.onUserActivity()
                                                viewModel.recordDebtPayment(customerId, amount, isCash, notes)
                                            },
                                            onAddExpense = { title, amount, category, paidTo, notes ->
                                                viewModel.onUserActivity()
                                                viewModel.addExpense(title, amount, category, paidTo, notes)
                                            },
                                            onVoidSale = { saleId, reason ->
                                                viewModel.onUserActivity()
                                                viewModel.voidSale(saleId, reason)
                                            },
                                            onAddEmployee = { name, uname, pin, role, phone ->
                                                viewModel.onUserActivity()
                                                viewModel.addEmployee(name, uname, pin, role, phone)
                                            },
                                            onToggleActive = { uId, active ->
                                                viewModel.onUserActivity()
                                                viewModel.toggleUserActive(uId, active)
                                            },
                                            onRemoveUser = { uId ->
                                                viewModel.onUserActivity()
                                                viewModel.deleteUser(uId)
                                            },
                                            onUpdateWalletStatus = { txId, st, reason ->
                                                viewModel.onUserActivity()
                                                viewModel.updateWalletTransactionStatus(txId, st, reason)
                                            },
                                            onReassignShift = { txId, newShiftId, cashierId, cashierName, reason ->
                                                viewModel.onUserActivity()
                                                viewModel.reassignShift(txId, newShiftId, cashierId, cashierName, reason)
                                            },
                                            onToggleGateway = {
                                                viewModel.onUserActivity()
                                                viewModel.toggleGatewayServer()
                                            },
                                            onRetryQueueItem = { id ->
                                                viewModel.onUserActivity()
                                                viewModel.retryQueueItem(id)
                                            },
                                            onRetryAllQueue = {
                                                viewModel.onUserActivity()
                                                viewModel.retryAllQueue()
                                            },
                                            onClearCompletedQueue = {
                                                viewModel.onUserActivity()
                                                viewModel.clearCompletedQueue()
                                            },
                                            onIngestTestSms = { sender, body ->
                                                viewModel.onUserActivity()
                                                viewModel.ingestSms(sender, body)
                                            },
                                            onExportSalesCsv = { uri ->
                                                viewModel.onUserActivity()
                                                viewModel.exportSalesCsv(uri)
                                            },
                                            onExportInventoryCsv = { uri ->
                                                viewModel.onUserActivity()
                                                viewModel.exportInventoryCsv(uri)
                                            },
                                            onCreateBackup = { uri, password ->
                                                viewModel.onUserActivity()
                                                viewModel.createBackup(uri, password)
                                            },
                                            onRestoreBackup = { uri, password ->
                                                viewModel.onUserActivity()
                                                viewModel.restoreBackup(uri, password)
                                            },
                                            insights = aiInsights,
                                            onAskAi = { q -> viewModel.askAi(q) },
                                            onSaveApiKey = { k -> viewModel.saveGeminiApiKey(k) },
                                            onSaveSetting = { k, v -> viewModel.saveSystemSetting(k, v) },
                                            autoLockMinutes = autoLockMinutes
                                        )
                                    }
                                }
                                AppScreen.WALLETS -> {
                                    val user = currentUser
                                    if (user != null) {
                                        WalletScreen(
                                            currentUser = user,
                                            currentShift = currentShift,
                                            wallets = wallets,
                                            transactions = walletTransactions,
                                            onBack = {
                                                viewModel.onUserActivity()
                                                viewModel.navigateTo(AppScreen.POS)
                                            },
                                            onUpdateStatus = { txId, status, reason ->
                                                viewModel.onUserActivity()
                                                viewModel.updateWalletTransactionStatus(txId, status, reason)
                                            },
                                            onIngestTestSms = { sender: String, body: String ->
                                                viewModel.onUserActivity()
                                                viewModel.ingestSms(sender, body)
                                            },
                                            onReassignShift = { txId, newShiftId, cashierId, cashierName, reason ->
                                                viewModel.onUserActivity()
                                                viewModel.reassignShift(txId, newShiftId, cashierId, cashierName, reason)
                                            }
                                        )
                                    }
                                }
                            }

                            // SESSION AUTO-LOCK DIALOG
                            if (isSessionLocked && currentUser != null) {
                                SessionLockDialog(
                                    userName = currentUser?.name ?: "",
                                    onUnlock = { pin ->
                                        viewModel.unlockSession(pin)
                                    },
                                    onLogout = {
                                        viewModel.logout()
                                    }
                                )
                            }

                            // START SHIFT DIALOG
                            if (showStartShiftDialog) {
                                StartShiftDialog(
                                    onDismiss = { viewModel.showStartShiftDialog.value = false },
                                    onConfirm = { cash, notes ->
                                        viewModel.startShift(cash, notes)
                                    }
                                )
                            }

                            // CLOSE SHIFT DIALOG
                            val shiftToClose = currentShift
                            if (showCloseShiftDialog && shiftToClose != null) {
                                CloseShiftDialog(
                                    shift = shiftToClose,
                                    onDismiss = { viewModel.showCloseShiftDialog.value = false },
                                    onConfirm = { actual: Double, handed: Double, left: Double, notes: String ->
                                        viewModel.closeShift(actual, handed, left, notes)
                                    }
                                )
                            }

                            // HANDOVER DIALOG
                            if (showHandoverDialog) {
                                HandoverDialog(
                                    lastShift = allShifts.firstOrNull(),
                                    onDismiss = { viewModel.showHandoverDialog.value = false },
                                    onConfirm = { actual: Double, notes: String ->
                                        viewModel.acceptHandover(actual, notes)
                                    }
                                )
                            }

                            // PAYMENT DIALOG
                            if (showPaymentDialog) {
                                val total = cartItems.sumOf { it.product.price * it.quantity }
                                PaymentDialog(
                                    totalAmount = total,
                                    customers = eligibleDebtCustomers,
                                    onDismiss = { viewModel.showPaymentDialog.value = false },
                                    onConfirmSale = { method, cash, ref, custId ->
                                        viewModel.executeSale(method, cash, ref, custId)
                                    }
                                )
                            }

                            // RECEIPT DIALOG
                            val completed = lastCompletedSale
                            if (completed != null) {
                                ReceiptDialog(
                                    sale = completed,
                                    items = cartItems,
                                    onPrint = {
                                        viewModel.snackbarMessage.value = "جاري إرسال الفاتورة إلى طابعة البلوتوث/الشبكة..."
                                    },
                                    onDismiss = {
                                        viewModel.lastCompletedSale.value = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
