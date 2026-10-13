package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.data.engine.*
import com.example.data.local.AppDatabase
import com.example.data.seed.DatabaseSeeder
import com.example.data.local.entity.*
import com.example.domain.model.*
import com.example.security.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val db: AppDatabase = AppDatabase.getInstance(application)
    val passwordHasher: PasswordHasher = PasswordHasher.DEFAULT
    val lockoutPolicy: LockoutPolicy = LockoutPolicy()
    val sessionManager: SessionManager = SessionManager()

    val costEngine = CostEngine(db.rawMaterialDao(), db.recipeDao(), db.mixtureDao())
    val inventoryEngine = InventoryEngine(db.rawMaterialDao(), db.recipeDao(), db.mixtureDao(), db.inventoryTransactionDao(), db.productDao())
    val salesEngine = SalesEngine(db, inventoryEngine)
    val shiftEngine = ShiftEngine(db)
    val purchaseEngine = PurchaseEngine(db, inventoryEngine)
    val powerAiEngine = PowerAiEngine(db)
    val walletMatchingEngine = WalletMatchingEngine(db)
    val backupRestoreEngine = BackupRestoreEngine(application, db)
    val gatewaySyncEngine = GatewaySyncEngine(db, viewModelScope, autoStartRetry = false)

    val isGatewayRunning = MutableStateFlow(false)
    val gatewayPort = MutableStateFlow(8080)
    val gatewayIp = MutableStateFlow(com.example.util.NetworkUtils.getLocalIpAddress())
    val autoLockMinutes = MutableStateFlow(SessionManager.DEFAULT_IDLE_TIMEOUT_MINUTES.toString())

    val pendingQueue: StateFlow<List<GatewayQueueEntity>> = gatewaySyncEngine.pendingQueue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingQueueCount: StateFlow<Int> = gatewaySyncEngine.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allQueueItems: StateFlow<List<GatewayQueueEntity>> = gatewaySyncEngine.allQueue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentUser = MutableStateFlow<UserEntity?>(null)

    init {
        // Seed the local owner and initial catalog before the login screen is used.
        // This is local-only and does not contact any cloud service.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DatabaseSeeder.seedIfNeeded(db)
                val configuredIdleMinutes = db.settingsDao()
                    .getSetting("auto_lock_minutes")
                    ?.toIntOrNull()
                    ?.takeIf { it in 1..SessionManager.MAX_IDLE_TIMEOUT_MINUTES }
                    ?: SessionManager.DEFAULT_IDLE_TIMEOUT_MINUTES
                sessionManager.setIdleTimeoutMinutes(configuredIdleMinutes)
                autoLockMinutes.value = configuredIdleMinutes.toString()

            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    loginErrorMessage.value = "تعذر تهيئة قاعدة البيانات: ${error.localizedMessage ?: "خطأ غير معروف"}"
                }
            }
        }

        // Real periodic enforcement: the session remains locked until explicit unlock.
        viewModelScope.launch {
            while (true) {
                if (_currentUser.value != null) {
                    sessionManager.checkTimeout()
                }
                delay(15_000L)
            }
        }

        // Read the real foreground-service state; starting a request is not success.
        viewModelScope.launch {
            while (true) {
                isGatewayRunning.value = com.example.gateway.GatewayService.isServerRunning
                if (isGatewayRunning.value) {
                    gatewayIp.value = com.example.util.NetworkUtils.getLocalIpAddress()
                }
                delay(750L)
            }
        }
    }

    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _activeScreen = MutableStateFlow(AppScreen.LOGIN)
    val activeScreen: StateFlow<AppScreen> = _activeScreen.asStateFlow()

    val isSessionLocked: StateFlow<Boolean> = sessionManager.isLocked

    val showStartShiftDialog = MutableStateFlow(false)
    val showCloseShiftDialog = MutableStateFlow(false)
    val showHandoverDialog = MutableStateFlow(false)
    val showPaymentDialog = MutableStateFlow(false)
    private val isExecutingSale = java.util.concurrent.atomic.AtomicBoolean(false)

    val lastCompletedSale = MutableStateFlow<SaleEntity?>(null)
    val snackbarMessage = MutableStateFlow<String?>(null)
    val loginErrorMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage = MutableStateFlow<String?>(null)

    val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    val userProfiles: StateFlow<List<UserProfile>> = db.userDao().getAllActiveUsers()
        .map { list ->
            list.map {
                val isLocked = lockoutPolicy.isCurrentlyLocked(it.lockedUntil)
                val remainingSec = lockoutPolicy.getRemainingSeconds(it.lockedUntil)
                UserProfile(
                    id = it.id,
                    name = it.name,
                    username = it.username,
                    role = it.role,
                    phone = it.phone,
                    isLocked = isLocked,
                    remainingLockSeconds = remainingSec,
                    requiresPinSetup = it.role == UserRole.OWNER &&
                        it.pinHash.isBlank() && it.pinSalt.isBlank()
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val users: StateFlow<List<UserEntity>> = db.userDao().getAllActiveUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<UserEntity>> = db.userDao().getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = db.categoryDao().getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = db.productDao().getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawMaterials: StateFlow<List<RawMaterialEntity>> = db.rawMaterialDao().getAllRawMaterials()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recipes: StateFlow<List<RecipeEntity>> = db.recipeDao().getAllRecipes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recipeItems: StateFlow<List<RecipeItemEntity>> = db.recipeDao().getAllRecipeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mixtures: StateFlow<List<MixtureEntity>> = db.mixtureDao().getAllMixtures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suppliers: StateFlow<List<SupplierEntity>> = db.supplierDao().getAllSuppliers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchases: StateFlow<List<PurchaseEntity>> = db.purchaseDao().getAllPurchases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = db.customerDao().getAllCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val eligibleDebtCustomers: StateFlow<List<CustomerEntity>> = db.customerDao().getEligibleDebtCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentShift: StateFlow<ShiftEntity?> = db.shiftDao().getCurrentOpenShift()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allShifts: StateFlow<List<ShiftEntity>> = db.shiftDao().getAllShifts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<SaleEntity>> = db.saleDao().getAllSales()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val saleItems: StateFlow<List<SaleItemEntity>> = db.saleDao().getAllSaleItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = db.expenseDao().getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = db.auditLogDao().getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wallets: StateFlow<List<WalletEntity>> = db.walletDao().getAllWallets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val walletTransactions: StateFlow<List<WalletTransactionEntity>> = db.walletDao().getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val smsInbox: StateFlow<List<WalletSmsEntity>> = db.walletDao().getAllSms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiInsights: StateFlow<List<AiInsight>> = flow {
        emit(powerAiEngine.generateRealInsights())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onUserActivity() {
        sessionManager.updateActivity()
    }

    fun unlockSession(pin: String): Boolean {
        val user = _currentUser.value ?: return false
        val isValid = passwordHasher.verify(pin, user.pinSalt, user.pinHash)
        if (isValid) {
            sessionManager.unlock()
            return true
        }
        return false
    }

    fun authenticate(userId: String, rawPin: String) {
        if (rawPin.length !in 4..6 || !rawPin.all(Char::isDigit)) {
            loginErrorMessage.value = "رمز PIN يجب أن يكون من 4 إلى 6 أرقام"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val user = db.userDao().getUserById(userId)
            if (user == null || !user.isActive) {
                loginErrorMessage.value = "المستخدم غير موجود أو غير نشط"
                return@launch
            }
            if (user.pinHash.isBlank() && user.pinSalt.isBlank()) {
                if (user.role != UserRole.OWNER) {
                    loginErrorMessage.value = "بيانات PIN غير مهيأة لهذا المستخدم. اطلب من المالك إصلاح الحساب."
                    return@launch
                }
                val initialized = try {
                    OwnerPinSetupManager(db, passwordHasher).setInitialPin(user.id, rawPin)
                } catch (error: Exception) {
                    loginErrorMessage.value = error.message ?: "تعذر إعداد PIN الأول للمالك"
                    return@launch
                }
                _currentUser.value = initialized
                sessionManager.unlock()
                db.auditLogDao().insertLog(
                    AuditLogEntity(
                        userId = initialized.id,
                        userName = initialized.name,
                        userRole = initialized.role.name,
                        action = "تسجيل دخول",
                        notes = "تم تسجيل الدخول بعد إعداد PIN الأول للمالك"
                    )
                )
                _activeScreen.value = AppScreen.POS
                loginErrorMessage.value = null
                snackbarMessage.value = "تم إنشاء PIN الخاص بالمالك. احفظه بأمان؛ لن يُعرض مرة أخرى."
                return@launch
            }
            if (user.pinHash.isBlank() || user.pinSalt.isBlank()) {
                loginErrorMessage.value = "بيانات PIN لهذا الحساب غير مكتملة. تواصل مع المالك."
                return@launch
            }
            if (lockoutPolicy.isCurrentlyLocked(user.lockedUntil)) {
                val sec = lockoutPolicy.getRemainingSeconds(user.lockedUntil)
                loginErrorMessage.value = "الحساب مقفل مؤقتاً بسبب تكرار الخطأ. انتظر $sec ثانية"
                return@launch
            }
            val valid = passwordHasher.verify(rawPin, user.pinSalt, user.pinHash)
            if (valid) {
                db.userDao().updateUser(user.copy(failedAttempts = 0, lockedUntil = null, lastLoginAt = System.currentTimeMillis()))
                _currentUser.value = user
                sessionManager.unlock()
                db.auditLogDao().insertLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        userRole = user.role.name,
                        action = "تسجيل دخول",
                        notes = "تم تسجيل الدخول بنجاح"
                    )
                )
                _activeScreen.value = AppScreen.POS
                loginErrorMessage.value = null
            } else {
                val newAttempts = user.failedAttempts + 1
                val lockedUntil = if (lockoutPolicy.shouldLock(newAttempts)) lockoutPolicy.calculateLockoutUntil() else null
                db.userDao().updateUser(user.copy(failedAttempts = newAttempts, lockedUntil = lockedUntil))
                val msg = if (lockedUntil != null) "تم قفل الحساب لمدة 5 دقائق لتكرار المحاولات الخاطئة" else "رمز PIN غير صحيح. محاولة $newAttempts من ${lockoutPolicy.maxAttempts}"
                loginErrorMessage.value = msg
            }
        }
    }

    fun logout() {
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch(Dispatchers.IO) {
                db.auditLogDao().insertLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        userRole = user.role.name,
                        action = "تسجيل خروج",
                        notes = "قام المستخدم بتسجيل الخروج"
                    )
                )
            }
        }
        sessionManager.lockManually()
        _currentUser.value = null
        _activeScreen.value = AppScreen.LOGIN
        clearCart()
    }

    suspend fun logUnauthorizedAttempt(user: UserEntity, permission: AppPermission, operationName: String) {
        db.auditLogDao().insertLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.name,
                userRole = user.role.name,
                action = "محاولة غير مصرح بها",
                notes = "المستخدم حاول تنفيذ $operationName دون صلاحية ${permission.name}"
            )
        )
    }

    private fun requirePermission(user: UserEntity, permission: AppPermission, operationName: String): Boolean {
        if (PermissionChecker.hasPermission(user.role, permission)) return true
        snackbarMessage.value = "ليس لديك صلاحية تنفيذ هذه العملية"
        viewModelScope.launch(Dispatchers.IO) {
            logUnauthorizedAttempt(user, permission, operationName)
        }
        return false
    }

    fun navigateTo(screen: AppScreen) {
        val user = _currentUser.value ?: return
        if (screen == AppScreen.ADMIN && !PermissionChecker.hasPermission(user.role, AppPermission.ACCESS_ADMIN)) {
            viewModelScope.launch(Dispatchers.IO) {
                logUnauthorizedAttempt(user, AppPermission.ACCESS_ADMIN, "فتح لوحة الإدارة")
            }
            snackbarMessage.value = "ليس لديك صلاحية الوصول إلى الإدارة"
            return
        }
        _activeScreen.value = screen
    }

    fun addToCart(product: ProductEntity) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            val item = current[index]
            current[index] = item.copy(quantity = item.quantity + 1)
        } else {
            current.add(CartItem(product = product, quantity = 1, unitPrice = product.price))
        }
        _cartItems.value = current
    }

    fun removeFromCart(product: ProductEntity) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            val item = current[index]
            if (item.quantity > 1) {
                current[index] = item.copy(quantity = item.quantity - 1)
            } else {
                current.removeAt(index)
            }
            _cartItems.value = current
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }

    fun startShift(openingCash: Double, notes: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.OPERATE_SHIFT, "فتح الشفت")) return
        viewModelScope.launch(Dispatchers.IO) {
            val res = shiftEngine.startShift(user, openingCash, notes)
            if (res is ShiftResult.Success) {
                showStartShiftDialog.value = false
                snackbarMessage.value = "تم فتح الشفت بنجاح"
            } else if (res is ShiftResult.Error) {
                snackbarMessage.value = res.message
            }
        }
    }

    fun closeShift(actualCash: Double, handedOverCash: Double, leftForNextShiftCash: Double, notes: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.OPERATE_SHIFT, "إغلاق الشفت")) return
        val shift = currentShift.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val res = shiftEngine.closeShift(shift.id, actualCash, handedOverCash, leftForNextShiftCash, user, notes)
            if (res is ShiftResult.Success) {
                showCloseShiftDialog.value = false
                snackbarMessage.value = "تم إغلاق الشفت وحفظ الحسابات بنجاح"
            } else if (res is ShiftResult.Error) {
                snackbarMessage.value = res.message
            }
        }
    }

    fun acceptHandover(actualReceivedCash: Double, notes: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.OPERATE_SHIFT, "استلام تسليم الشفت")) return
        val shift = currentShift.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val res = shiftEngine.acceptHandover(shift.id, user, actualReceivedCash, notes)
            if (res is ShiftResult.Success) {
                showHandoverDialog.value = false
                snackbarMessage.value = "تم استلام تسليم الشفت وتأكيد الصندوق"
            } else if (res is ShiftResult.Error) {
                snackbarMessage.value = res.message
            }
        }
    }

    fun executeSale(paymentMethod: PaymentMethod, cashReceived: Double, reference: String, customerId: String?) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.PROCESS_SALE, "تنفيذ المبيعات")) return
        val shift = currentShift.value
        val items = _cartItems.value
        if (items.isEmpty()) return
        if (shift == null) {
            snackbarMessage.value = "يجب فتح شفت أولاً قبل إتمام عمليات البيع"
            return
        }
        if (!isExecutingSale.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = salesEngine.executeSale(
                    shift = shift,
                    user = user,
                    items = items,
                    paymentMethod = paymentMethod,
                    cashReceived = cashReceived,
                    paymentReference = reference,
                    selectedCustomerId = customerId
                )
                if (res is SaleResult.Success) {
                    lastCompletedSale.value = res.sale
                    clearCart()
                    showPaymentDialog.value = false
                    snackbarMessage.value = "تمت عملية البيع بنجاح (فاتورة ${res.invoiceNumber})"
                } else if (res is SaleResult.Error) {
                    snackbarMessage.value = res.message
                }
            } finally {
                isExecutingSale.set(false)
            }
        }
    }

    fun toggleProductActive(productId: String, isActive: Boolean) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_PRODUCTS, "تغيير حالة المنتج")) return
        viewModelScope.launch(Dispatchers.IO) {
            val prod = db.productDao().getProductById(productId) ?: return@launch
            db.productDao().updateProduct(prod.copy(isActive = isActive))
            db.auditLogDao().insertLog(
                AuditLogEntity(userId = user.id, userName = user.name, userRole = user.role.name, action = "تعديل حالة منتج", notes = "تم ${if (isActive) "تفعيل" else "تعطيل"} المنتج ${prod.name}")
            )
            snackbarMessage.value = "تم تحديث حالة المنتج"
        }
    }

    fun addRawMaterial(name: String, unit: String, initialStock: Double, minStock: Double, costPerUnit: Double) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_INVENTORY, "إضافة مادة خام")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                InventoryAdjustmentEngine(db).addRawMaterial(
                    name = name,
                    sku = "",
                    baseUnit = unit,
                    initialStock = initialStock,
                    minStock = minStock,
                    unitCost = costPerUnit,
                    user = user
                )
                snackbarMessage.value = "تمت إضافة المادة الخام وحفظ حركتها الافتتاحية"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                snackbarMessage.value = error.message ?: "تعذر إضافة المادة الخام"
            }
        }
    }

    fun addRawMaterialStock(materialId: String, amount: Double, note: String = "") {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_INVENTORY, "إضافة رصيد مخزون")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                InventoryAdjustmentEngine(db).addStock(materialId, amount, user, note)
                snackbarMessage.value = "تم تحديث رصيد المخزون وسجل الحركة"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                snackbarMessage.value = error.message ?: "تعذر تحديث رصيد المخزون"
            }
        }
    }

    fun addRecipe(name: String, productId: String, items: List<RecipeItemEntity>, notes: String = "") {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_RECIPES, "إدارة الوصفات")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.data.engine.RecipeManager(db).saveRecipe(name, productId, items, notes, user)
                snackbarMessage.value = "تم حفظ الوصفة ومكوّناتها وربطها بالمنتج"
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                snackbarMessage.value = error.message ?: "تعذر حفظ الوصفة"
            }
        }
    }

    fun addExpense(title: String, amount: Double, category: String, paidTo: String, notes: String = "") {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_EXPENSES, "تسجيل مصروف")) return
        recordExpenseSafely(
            title = title,
            category = category,
            amount = amount,
            paymentMethod = PaymentMethod.CASH,
            paidTo = paidTo,
            notes = notes
        )
    }

    fun addPurchase(supplierId: String, supplierName: String, invoiceNumber: String, items: List<NewPurchaseItem>, notes: String = "") {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_PURCHASES, "تسجيل مشتريات")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                purchaseEngine.createPurchase(
                    supplierId = supplierId,
                    supplierName = supplierName,
                    invoiceNumber = invoiceNumber,
                    items = items,
                    user = user,
                    notes = notes
                )
                withContext(Dispatchers.Main) {
                    snackbarMessage.value = "تم تسجيل فاتورة الشراء وتحديث المخزون والتكلفة بنجاح"
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarMessage.value = error.message ?: "تعذر تسجيل فاتورة الشراء؛ لم تُحفظ التغييرات الجزئية"
                }
            }
        }
    }

    fun addSupplier(name: String, phone: String = "", address: String = "", notes: String = "") {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_PURCHASES, "إضافة مورد")) return
        val cleanedName = name.trim()
        if (cleanedName.isBlank()) {
            snackbarMessage.value = "اسم المورد مطلوب"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val supplier = db.withTransaction {
                    val duplicate = db.supplierDao().getAllSuppliersSync().any {
                        it.name.trim().equals(cleanedName, ignoreCase = true)
                    }
                    require(!duplicate) { "يوجد مورد بهذا الاسم بالفعل" }
                    val created = SupplierEntity(
                        name = cleanedName,
                        phone = phone.trim(),
                        address = address.trim(),
                        notes = notes.trim(),
                        isActive = true
                    )
                    db.supplierDao().insertSupplier(created)
                    db.auditLogDao().insertLog(
                        AuditLogEntity(
                            userId = user.id,
                            userName = user.name,
                            userRole = user.role.name,
                            action = "SUPPLIER_CREATED",
                            entityType = "SUPPLIER",
                            entityId = created.id,
                            newValue = created.name,
                            notes = "تم إنشاء مورد محلي لاستخدامه في فواتير المشتريات"
                        )
                    )
                    created
                }
                withContext(Dispatchers.Main) {
                    snackbarMessage.value = "تم حفظ المورد ${supplier.name}"
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarMessage.value = error.message ?: "تعذر حفظ المورد"
                }
            }
        }
    }

    fun addProduct(name: String, catId: String, price: Double, cost: Double = 0.0) {
        saveProduct(
            ProductEntity(
                name = name,
                categoryId = catId,
                price = price,
                costPrice = cost
            )
        )
    }

    fun saveProduct(product: ProductEntity) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_PRODUCTS, "حفظ المنتج")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existedBefore = db.productDao().getProductById(product.id) != null
                ProductManager(db).saveProduct(product, user)
                withContext(Dispatchers.Main) {
                    snackbarMessage.value = if (existedBefore) "تم تحديث المنتج بنجاح" else "تمت إضافة المنتج بنجاح"
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarMessage.value = error.message ?: "تعذر حفظ المنتج"
                }
            }
        }
    }

    fun updateProductPrice(productId: String, newPrice: Double) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_PRODUCTS, "تعديل سعر المنتج")) return
        if (!newPrice.isFinite() || newPrice < 0.0) {
            snackbarMessage.value = "السعر الجديد غير صالح"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val prod = db.productDao().getProductById(productId)
            if (prod != null) {
                db.productDao().updateProduct(prod.copy(price = newPrice))
                db.auditLogDao().insertLog(
                    AuditLogEntity(userId = user.id, userName = user.name, userRole = user.role.name, action = "تحديث سعر", notes = "تحديث سعر ${prod.name} إلى $newPrice")
                )
                snackbarMessage.value = "تم تحديث سعر المنتج"
            }
        }
    }

    fun toggleProductAvailable(productId: String, isAvailable: Boolean) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_PRODUCTS, "تغيير إتاحة المنتج")) return
        viewModelScope.launch(Dispatchers.IO) {
            val prod = db.productDao().getProductById(productId)
            if (prod != null) {
                db.productDao().updateProduct(prod.copy(isAvailable = isAvailable))
                snackbarMessage.value = "تم تحديث حالة المنتج"
            }
        }
    }

    fun addRawMaterial(name: String, sku: String, baseUnit: String, minStock: Double, price: Double) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_INVENTORY, "إضافة مادة خام")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                InventoryAdjustmentEngine(db).addRawMaterial(
                    name = name,
                    sku = sku,
                    baseUnit = baseUnit,
                    initialStock = 0.0,
                    minStock = minStock,
                    unitCost = price,
                    user = user
                )
                snackbarMessage.value = "تمت إضافة المادة الخام بنجاح"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                snackbarMessage.value = error.message ?: "تعذر إضافة المادة الخام"
            }
        }
    }

    fun addCustomer(name: String, phone: String, creditLimit: Double, allowDebt: Boolean) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_CUSTOMERS, "إضافة عميل")) return
        if (name.isBlank() || !creditLimit.isFinite() || creditLimit < 0.0) {
            snackbarMessage.value = "بيانات العميل أو السقف الائتماني غير صالحة"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val cust = CustomerEntity(
                name = name,
                phone = phone,
                creditLimit = creditLimit,
                allowDebt = allowDebt,
                currentDebt = 0.0,
                status = CustomerStatus.ACTIVE
            )
            db.customerDao().insertCustomer(cust)
            snackbarMessage.value = "تم تسجيل العميل بنجاح"
        }
    }

    fun addEmployee(name: String, username: String, pin: String, role: UserRole, phone: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_EMPLOYEES, "إضافة موظف")) return
        if (user.role == UserRole.ADMIN && role != UserRole.CASHIER) {
            snackbarMessage.value = "المدير يستطيع إنشاء حسابات الكاشير فقط؛ الأدوار الإشرافية متاحة للمالك"
            return
        }
        val cleanName = name.trim()
        val cleanUsername = username.trim()
        if (cleanName.isEmpty() || cleanUsername.isEmpty()) {
            snackbarMessage.value = "أدخل اسم الموظف واسم المستخدم"
            return
        }
        if (pin.length !in 4..6 || !pin.all(Char::isDigit)) {
            snackbarMessage.value = "رمز PIN يجب أن يكون من 4 إلى 6 أرقام"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (db.userDao().getUserByUsername(cleanUsername) != null) {
                snackbarMessage.value = "اسم المستخدم مستخدم بالفعل"
                return@launch
            }
            val hash = passwordHasher.hash(pin)
            val emp = UserEntity(
                name = cleanName,
                username = cleanUsername,
                pinHash = hash.hashHex,
                pinSalt = hash.saltHex,
                role = role,
                phone = phone.trim(),
                isActive = true
            )
            db.userDao().insertUser(emp)
            db.auditLogDao().insertLog(
                AuditLogEntity(userId = user.id, userName = user.name, userRole = user.role.name, action = "إضافة موظف", notes = "إضافة المستخدم $cleanName بصلاحية ${role.name}")
            )
            snackbarMessage.value = "تمت إضافة الموظف بنجاح"
        }
    }

    fun toggleUserActive(userId: String, isActive: Boolean) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_EMPLOYEES, "تغيير حالة مستخدم")) return
        viewModelScope.launch(Dispatchers.IO) {
            val target = db.userDao().getUserById(userId)
            if (target == null) return@launch
            if (target.role == UserRole.OWNER || target.id == user.id) {
                snackbarMessage.value = "لا يمكن تعطيل حساب المالك"
                return@launch
            }
            if (user.role == UserRole.ADMIN && target.role != UserRole.CASHIER) {
                snackbarMessage.value = "المدير يستطيع إدارة حسابات الكاشير فقط"
                return@launch
            }
            db.userDao().updateUser(target.copy(isActive = isActive, updatedAt = System.currentTimeMillis()))
            db.auditLogDao().insertLog(
                AuditLogEntity(userId = user.id, userName = user.name, userRole = user.role.name, action = "تغيير حالة مستخدم", notes = "المستخدم ${target.username}: ${if (isActive) "تفعيل" else "تعطيل"}")
            )
            snackbarMessage.value = "تم تحديث حالة المستخدم"
        }
    }

    fun deleteUser(userId: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_EMPLOYEES, "حذف مستخدم")) return
        viewModelScope.launch(Dispatchers.IO) {
            val target = db.userDao().getUserById(userId)
            if (target == null) return@launch
            if (target.role == UserRole.OWNER || target.id == user.id) {
                snackbarMessage.value = "لا يمكن حذف حساب المالك"
                return@launch
            }
            if (user.role == UserRole.ADMIN && target.role != UserRole.CASHIER) {
                snackbarMessage.value = "المدير يستطيع إدارة حسابات الكاشير فقط"
                return@launch
            }
            db.userDao().deleteUserById(target.id)
            db.auditLogDao().insertLog(
                AuditLogEntity(userId = user.id, userName = user.name, userRole = user.role.name, action = "حذف مستخدم", notes = "تم حذف المستخدم ${target.username}")
            )
            snackbarMessage.value = "تم حذف المستخدم"
        }
    }

    fun changeUserPin(userId: String, currentPin: String, newPin: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val user = _currentUser.value ?: return
        if (newPin.length !in 4..6 || !newPin.all(Char::isDigit)) {
            onResult(false, "رمز PIN الجديد يجب أن يكون من 4 إلى 6 أرقام")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val target = db.userDao().getUserById(userId)
            if (target == null) {
                onResult(false, "المستخدم غير موجود")
                return@launch
            }
            if (user.id != target.id && !PermissionChecker.hasPermission(user.role, AppPermission.MANAGE_EMPLOYEES)) {
                onResult(false, "ليس لديك صلاحية تغيير كلمة السر")
                return@launch
            }
            if (user.role == UserRole.ADMIN && user.id != target.id && target.role != UserRole.CASHIER) {
                onResult(false, "المدير يستطيع تغيير PIN للكاشير فقط")
                return@launch
            }
            if (user.id == target.id && !passwordHasher.verify(currentPin, target.pinSalt, target.pinHash)) {
                onResult(false, "كلمة السر الحالية غير صحيحة")
                return@launch
            }
            val hash = passwordHasher.hash(newPin)
            db.userDao().updateUser(target.copy(pinHash = hash.hashHex, pinSalt = hash.saltHex))
            onResult(true, "تم تغيير كلمة السر بنجاح")
        }
    }

    fun addExpense(title: String, category: String, amount: Double, isCash: Boolean, paidTo: String, notes: String) {
        recordExpenseSafely(
            title = title,
            category = category,
            amount = amount,
            paymentMethod = if (isCash) PaymentMethod.CASH else PaymentMethod.E_WALLET,
            paidTo = paidTo,
            notes = notes
        )
    }

    private fun recordExpenseSafely(
        title: String,
        category: String,
        amount: Double,
        paymentMethod: PaymentMethod,
        paidTo: String,
        notes: String
    ) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_EXPENSES, "تسجيل مصروف")) return
        val shiftId = currentShift.value?.id.orEmpty()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ExpenseEngine(db).recordExpense(
                    title = title,
                    amount = amount,
                    category = category,
                    paidTo = paidTo,
                    notes = notes,
                    paymentMethod = paymentMethod,
                    shiftId = shiftId,
                    user = user
                )
                snackbarMessage.value = "تم تسجيل المصروف وحفظ أثره على النقدية والشفت"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                snackbarMessage.value = error.message ?: "تعذر تسجيل المصروف"
            }
        }
    }

    fun createPurchase(supplierId: String, supplierName: String, invoiceNo: String, materialId: String, qty: Double, unit: String, unitPrice: Double) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_PURCHASES, "تسجيل فاتورة شراء")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val material = db.rawMaterialDao().getRawMaterialById(materialId)
                    ?: throw IllegalArgumentException("المادة الخام غير موجودة")
                val item = NewPurchaseItem(
                    rawMaterialId = materialId,
                    rawMaterialName = material.name,
                    unit = unit,
                    quantity = qty,
                    unitPrice = unitPrice
                )
                purchaseEngine.createPurchase(
                    supplierId = supplierId,
                    supplierName = supplierName,
                    invoiceNumber = invoiceNo,
                    items = listOf(item),
                    user = user
                )
                withContext(Dispatchers.Main) {
                    snackbarMessage.value = "تم تسجيل فاتورة الشراء وتحديث المخزون والتكلفة بنجاح"
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarMessage.value = error.message ?: "تعذر تسجيل فاتورة الشراء"
                }
            }
        }
    }

    fun recordDebtPayment(customerId: String, amount: Double, isCash: Boolean, notes: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.RECORD_DEBT_PAYMENT, "سداد دين")) return
        val shiftId = currentShift.value?.id.orEmpty()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DebtEngine(db).payDebt(
                    customerId = customerId,
                    amount = amount,
                    paymentMethod = if (isCash) PaymentMethod.CASH else PaymentMethod.E_WALLET,
                    shiftId = shiftId,
                    user = user,
                    notes = notes
                )
                snackbarMessage.value = "تم تسجيل سداد الدين وتحديث الرصيد والنقدية ذريًا"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                snackbarMessage.value = error.message ?: "تعذر تسجيل سداد الدين"
            }
        }
    }

    fun recordWaste(materialId: String, qty: Double, unit: String, reason: String, notes: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.RECORD_WASTE, "تسجيل الهدر")) return
        val wasteReason = when (reason.uppercase()) {
            "EXPIRED" -> WasteReason.EXPIRED
            "DAMAGED" -> WasteReason.DAMAGED
            "SPOILED" -> WasteReason.SPOILED
            else -> WasteReason.OTHER
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                InventoryAdjustmentEngine(db).recordWaste(
                    materialId = materialId,
                    quantity = qty,
                    unit = unit,
                    reason = wasteReason,
                    notes = notes,
                    user = user
                )
                snackbarMessage.value = "تم تسجيل الهدر وخصم الكمية من المخزون وسجل الحركات"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                snackbarMessage.value = error.message ?: "تعذر تسجيل الهدر"
            }
        }
    }

    fun applyStockAdjustment(materialId: String, actualStock: Double, reason: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.ADJUST_STOCK, "تسوية جردية")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                InventoryAdjustmentEngine(db).applyStockAdjustment(materialId, actualStock, reason, user)
                snackbarMessage.value = "تمت التسوية وتسجيل أثرها في حركات المخزون"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                snackbarMessage.value = error.message ?: "تعذر تنفيذ التسوية الجردية"
            }
        }
    }

    fun voidSale(saleId: String, reason: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.VOID_SALE, "إلغاء فاتورة")) return
        viewModelScope.launch(Dispatchers.IO) {
            val res = salesEngine.voidSale(saleId, reason, user)
            if (res.isSuccess) {
                snackbarMessage.value = "تم إلغاء الفاتورة واستعادة كميات المخزون"
            } else {
                snackbarMessage.value = res.exceptionOrNull()?.message ?: "فشل إلغاء الفاتورة"
            }
        }
    }

    fun exportSalesCsv(uri: Uri) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.BACKUP_DATA, "تصدير المبيعات")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = backupRestoreEngine.exportSalesCsv()
                val source = File(path)
                val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("تعذر فتح ملف التصدير للكتابة")
                source.inputStream().use { input ->
                    output.use { stream -> input.copyTo(stream) }
                }
                backupStatusMessage.value = "تم حفظ ملف المبيعات CSV في الموقع الذي اخترته"
                snackbarMessage.value = "تم تصدير سجل المبيعات بنجاح"
            } catch (e: Exception) {
                backupStatusMessage.value = "فشل تصدير المبيعات: ${e.localizedMessage ?: "خطأ غير معروف"}"
            }
        }
    }

    fun exportInventoryCsv(uri: Uri) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.BACKUP_DATA, "تصدير المخزون")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = backupRestoreEngine.exportInventoryCsv()
                val source = File(path)
                val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("تعذر فتح ملف التصدير للكتابة")
                source.inputStream().use { input ->
                    output.use { stream -> input.copyTo(stream) }
                }
                backupStatusMessage.value = "تم حفظ ملف المخزون CSV في الموقع الذي اخترته"
                snackbarMessage.value = "تم تصدير المخزون بنجاح"
            } catch (e: Exception) {
                backupStatusMessage.value = "فشل تصدير المخزون: ${e.localizedMessage ?: "خطأ غير معروف"}"
            }
        }
    }

    fun exportCustomersCsv(uri: Uri) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.BACKUP_DATA, "تصدير العملاء")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = File(backupRestoreEngine.exportCustomersCsv())
                val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("تعذر فتح ملف تصدير العملاء للكتابة")
                source.inputStream().use { input -> output.use { stream -> input.copyTo(stream) } }
                backupStatusMessage.value = "تم حفظ ملف العملاء والديون في الموقع الذي اخترته"
                snackbarMessage.value = "تم تصدير العملاء بنجاح"
            } catch (error: Exception) {
                backupStatusMessage.value = "فشل تصدير العملاء: " + (error.localizedMessage ?: "خطأ غير معروف")
            }
        }
    }

    fun exportExpensesCsv(uri: Uri) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.BACKUP_DATA, "تصدير المصروفات")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = File(backupRestoreEngine.exportExpensesCsv())
                val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("تعذر فتح ملف تصدير المصروفات للكتابة")
                source.inputStream().use { input -> output.use { stream -> input.copyTo(stream) } }
                backupStatusMessage.value = "تم حفظ ملف المصروفات في الموقع الذي اخترته"
                snackbarMessage.value = "تم تصدير المصروفات بنجاح"
            } catch (error: Exception) {
                backupStatusMessage.value = "فشل تصدير المصروفات: " + (error.localizedMessage ?: "خطأ غير معروف")
            }
        }
    }

    fun exportDebtTransactionsCsv(uri: Uri) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.BACKUP_DATA, "تصدير سجل الديون")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = File(backupRestoreEngine.exportDebtTransactionsCsv())
                val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("تعذر فتح ملف تصدير حركات الديون للكتابة")
                source.inputStream().use { input -> output.use { stream -> input.copyTo(stream) } }
                backupStatusMessage.value = "تم حفظ ملف حركات الديون والسداد في الموقع الذي اخترته"
                snackbarMessage.value = "تم تصدير سجل الديون بنجاح"
            } catch (error: Exception) {
                backupStatusMessage.value = "فشل تصدير سجل الديون: " + (error.localizedMessage ?: "خطأ غير معروف")
            }
        }
    }

    fun createBackup(uri: Uri, password: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.BACKUP_DATA, "إنشاء نسخة احتياطية")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = backupRestoreEngine.createFullBackupBytes(user.id, user.name, password)
                val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("تعذر فتح ملف النسخة الاحتياطية للكتابة")
                output.use { it.write(bytes) }
                backupStatusMessage.value = "تم إنشاء النسخة الاحتياطية المشفّرة وحفظها في الموقع الذي اخترته"
                snackbarMessage.value = "تم إنشاء النسخة الاحتياطية الكاملة بنجاح"
            } catch (e: Exception) {
                backupStatusMessage.value = "فشل النسخ الاحتياطي: ${e.localizedMessage ?: "خطأ غير معروف"}"
            }
        }
    }

    fun restoreBackup(uri: Uri, password: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.BACKUP_DATA, "استعادة النسخة الاحتياطية")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val input = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("تعذر فتح ملف النسخة الاحتياطية")
                val bytes = ByteArrayOutputStream()
                input.use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(bytes.size().toLong() + count <= BackupRestoreEngine.MAX_BACKUP_BYTES) {
                            "حجم ملف النسخة الاحتياطية أكبر من الحد المسموح"
                        }
                        bytes.write(buffer, 0, count)
                    }
                }
                val tableCount = backupRestoreEngine.restoreFullBackup(bytes.toByteArray(), password, user.id)
                withContext(Dispatchers.Main) {
                    backupStatusMessage.value = "تمت استعادة النسخة بنجاح ($tableCount جداول). سجّل الدخول مجددًا."
                    logout()
                }
            } catch (e: Exception) {
                backupStatusMessage.value = "فشلت الاستعادة ولم تُعتمد البيانات: ${e.localizedMessage ?: "خطأ غير معروف"}"
            }
        }
    }

    fun updateWalletTransactionStatus(txId: String, newStatus: WalletTransferStatus, reason: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_WALLETS, "تعديل حالة حوالة")) return
        viewModelScope.launch(Dispatchers.IO) {
            walletMatchingEngine.updateTransactionStatus(txId, newStatus, reason, user.id, user.name, user.role.name)
            snackbarMessage.value = "تم تحديث حالة الحوالة بنجاح"
        }
    }

    fun ingestSms(sender: String, body: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_WALLETS, "معالجة رسالة محفظة يدويًا")) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = walletMatchingEngine.ingestSms(sender, body)
            when (result) {
                is IngestResult.Created -> {
                    val statusText = if (result.isOutOfShift) "(خارج الدوام)" else "(مطابقة لشفت)"
                    snackbarMessage.value = "تمت معالجة الحوالة بنجاح: ${result.tx.amount} ريال $statusText"
                }
                is IngestResult.Duplicate -> {
                    snackbarMessage.value = "تنبيه: ${result.reason}"
                }
                is IngestResult.Unmatched -> {
                    snackbarMessage.value = "وصلت رسالة غير مطابقة لمحافظ جيب/فلوسك/جوالي، تم حفظها في الصندوق"
                }
            }
        }
    }

    fun reassignShift(txId: String, newShiftId: String, cashierId: String, cashierName: String, reason: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.REASSIGN_TRANSFER, "إعادة إسناد حوالة")) return
        viewModelScope.launch(Dispatchers.IO) {
            val res = walletMatchingEngine.reassignShift(
                txId = txId,
                newShiftId = newShiftId,
                cashierId = cashierId,
                cashierName = cashierName,
                userId = user.id,
                userName = user.name,
                userRole = user.role.name,
                reason = reason
            )
            if (res.isSuccess) {
                snackbarMessage.value = "تمت إعادة إسناد الحوالة للشفت بنجاح"
            } else {
                snackbarMessage.value = res.exceptionOrNull()?.message ?: "فشل إعادة الإسناد"
            }
        }
    }

    fun toggleGatewayServer() {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_SETTINGS, "تشغيل Gateway")) return
        if (com.example.gateway.GatewayService.isServerRunning) {
            com.example.gateway.GatewayService.stopGateway(getApplication())
            snackbarMessage.value = "أُرسل طلب إيقاف خادم Gateway"
        } else {
            com.example.gateway.GatewayService.startGateway(getApplication(), gatewayPort.value)
            snackbarMessage.value = "أُرسل طلب تشغيل خادم Gateway"
        }
    }
    fun retryQueueItem(id: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_WALLETS, "إعادة محاولة عنصر Gateway")) return
        viewModelScope.launch(Dispatchers.IO) {
            gatewaySyncEngine.retryNow(id)
            snackbarMessage.value = "جاري إعادة محاولة إرسال العنصر..."
        }
    }

    fun retryAllQueue() {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_WALLETS, "إعادة محاولة طابور Gateway")) return
        viewModelScope.launch(Dispatchers.IO) {
            gatewaySyncEngine.retryAllNow()
            snackbarMessage.value = "جاري إعادة محاولة جميع عناصر طابور الانتظار..."
        }
    }

    fun clearCompletedQueue() {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_WALLETS, "تنظيف طابور Gateway")) return
        viewModelScope.launch(Dispatchers.IO) {
            gatewaySyncEngine.clearCompleted()
            snackbarMessage.value = "تم تنظيف العناصر المكتملة"
        }
    }

    fun getSmsForTransaction(txId: String): Flow<List<WalletSmsEntity>> {
        return db.walletDao().getSmsForTransaction(txId)
    }

    fun saveGeminiApiKey(key: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_SETTINGS, "تغيير مفتاح AI")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                powerAiEngine.saveGeminiApiKey(key)
                withContext(Dispatchers.Main) {
                    snackbarMessage.value = "تم حفظ مفتاح Gemini API بنجاح"
                }
            } catch (_: Exception) {
                // Never expose key material or a crypto exception in UI/log output.
                withContext(Dispatchers.Main) {
                    snackbarMessage.value = "تعذر حفظ مفتاح Gemini API في التخزين الآمن"
                }
            }
        }
    }

    fun saveSystemSetting(key: String, value: String) {
        val user = _currentUser.value ?: return
        if (!requirePermission(user, AppPermission.MANAGE_SETTINGS, "تعديل إعدادات النظام")) return
        viewModelScope.launch(Dispatchers.IO) {
            if (key == "auto_lock_minutes") {
                val minutes = value.trim().toIntOrNull()
                if (minutes == null || minutes !in 1..SessionManager.MAX_IDLE_TIMEOUT_MINUTES) {
                    withContext(Dispatchers.Main) {
                        snackbarMessage.value = "مدة القفل يجب أن تكون بين 1 و60 دقيقة"
                    }
                    return@launch
                }
                db.settingsDao().setSetting(
                    com.example.data.local.entity.CafeteriaSettingEntity(key, minutes.toString())
                )
                sessionManager.setIdleTimeoutMinutes(minutes)
                autoLockMinutes.value = minutes.toString()
            } else if (key == "gateway_token") {
                val token = value.trim()
                if (token.length < com.example.gateway.GatewaySecretStore.MIN_TOKEN_LENGTH) {
                    snackbarMessage.value = "رمز Gateway يجب أن يكون 32 حرفًا على الأقل"
                    return@launch
                }
                if (!com.example.gateway.GatewaySecretStore(getApplication()).saveToken(token)) {
                    snackbarMessage.value = "تعذر حفظ رمز Gateway في التخزين الآمن"
                    return@launch
                }
                db.settingsDao().deleteSetting("gateway_token")
            } else {
                db.settingsDao().setSetting(com.example.data.local.entity.CafeteriaSettingEntity(key, value))
            }
            withContext(Dispatchers.Main) { snackbarMessage.value = "تم حفظ الإعداد بنجاح" }
        }
    }
    suspend fun getSystemSetting(key: String): String? {
        val user = _currentUser.value ?: return null
        if (!PermissionChecker.hasPermission(user.role, AppPermission.MANAGE_SETTINGS)) {
            withContext(Dispatchers.IO) { logUnauthorizedAttempt(user, AppPermission.MANAGE_SETTINGS, "قراءة إعدادات النظام") }
            return null
        }
        return withContext(Dispatchers.IO) {
        if (key == "gateway_token") {
            com.example.gateway.GatewaySecretStore(getApplication()).getToken()
        } else {
            db.settingsDao().getSetting(key)
        }
        }
    }

    suspend fun askAi(prompt: String): String {
        return powerAiEngine.processQuery(prompt)
    }
}
