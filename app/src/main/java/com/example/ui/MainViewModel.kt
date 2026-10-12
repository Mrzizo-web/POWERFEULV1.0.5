package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
                    requiresPinSetup = it.id == OwnerPinSetupManager.INITIAL_OWNER_ID &&
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
                if (user.id != OwnerPinSetupManager.INITIAL_OWNER_ID || user.role != UserRole.OWNER) {
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

    fun navigateTo(screen: AppScreen) {
        val user = _currentUser.value ?: return
        if (screen == AppScreen.ADMIN && !PermissionChecker.hasPermission(user.role, AppPermission.VIEW_REPORTS)) {
            viewModelScope.launch(Dispatchers.IO) {
                logUnauthorizedAttempt(user, AppPermission.VIEW_REPORTS, "فتح لوحة الإدارة")
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
        viewModelScope.launch(Dispatchers.IO) {
            val material = RawMaterialEntity(
                name = name,
                baseUnit = unit,
                currentStock = initialStock,
                minStock = minStock,
                avgCostPerUnit = costPerUnit,
                lastPurchasePrice = costPerUnit
            )
            db.rawMaterialDao().insertRawMaterial(material)
            db.auditLogDao().insertLog(
                AuditLogEntity(userId = user.id, userName = user.name, userRole = user.role.name, action = "إضافة مادة خام", notes = "تمت إضافة المادة الخام $name برصيد $initialStock $unit")
            )
            snackbarMessage.value = "تمت إضافة المادة الخام بنجاح"
        }
    }

    fun addRawMaterialStock(materialId: String, amount: Double, note: String = "") {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val mat = db.rawMaterialDao().getRawMaterialById(materialId) ?: return@launch
            inventoryEngine.addRawMaterialStock(
                materialId = materialId,
                amount = amount,
                unit = mat.baseUnit,
                referenceId = "MANUAL_ADJUST",
                userId = user.id,
                userName = user.name,
                txType = com.example.domain.model.InventoryTxType.PURCHASE,
                note = if (note.isBlank()) "إضافة مخزون يدوية" else note
            )
            snackbarMessage.value = "تم تحديث رصيد المخزون بنجاح"
        }
    }

    fun addRecipe(name: String, productId: String, items: List<RecipeItemEntity>, notes: String = "") {
        val user = _currentUser.value ?: return
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
        viewModelScope.launch(Dispatchers.IO) {
            val expense = ExpenseEntity(
                shiftId = currentShift.value?.id ?: "",
                userId = user.id,
                userName = user.name,
                title = title,
                amount = amount,
                category = category,
                paidTo = paidTo,
                notes = notes
            )
            db.expenseDao().insertExpense(expense)
            db.auditLogDao().insertLog(
                AuditLogEntity(userId = user.id, userName = user.name, userRole = user.role.name, action = "تسجيل مصروف", notes = "مصروف $title بقيمة $amount")
            )
            snackbarMessage.value = "تم تسجيل المصروف بنجاح"
        }
    }

    fun addPurchase(supplierId: String, supplierName: String, invoiceNumber: String, items: List<NewPurchaseItem>, notes: String = "") {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            purchaseEngine.createPurchase(
                supplierId = supplierId,
                supplierName = supplierName,
                invoiceNumber = invoiceNumber,
                items = items,
                user = user,
                notes = notes
            )
            db.auditLogDao().insertLog(
                AuditLogEntity(userId = user.id, userName = user.name, userRole = user.role.name, action = "تسجيل فاتورة شراء", notes = "فاتورة شراء $invoiceNumber من المورد $supplierName")
            )
            snackbarMessage.value = "تم تسجيل فاتورة الشراء وتحديث المخزون بنجاح"
        }
    }

    fun addProduct(name: String, catId: String, price: Double, cost: Double = 0.0) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val prod = ProductEntity(
                name = name,
                categoryId = catId,
                price = price,
                costPrice = cost
            )
            db.productDao().insertProduct(prod)
            db.auditLogDao().insertLog(
                AuditLogEntity(userId = user.id, userName = user.name, userRole = user.role.name, action = "إضافة منتج", notes = "تمت إضافة المنتج $name بسعر $price")
            )
            snackbarMessage.value = "تمت إضافة المنتج بنجاح"
        }
    }

    fun updateProductPrice(productId: String, newPrice: Double) {
        val user = _currentUser.value ?: return
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
        viewModelScope.launch(Dispatchers.IO) {
            val mat = RawMaterialEntity(
                name = name,
                sku = sku,
                baseUnit = baseUnit,
                minStock = minStock,
                avgCostPerUnit = price,
                lastPurchasePrice = price,
                currentStock = 0.0
            )
            db.rawMaterialDao().insertRawMaterial(mat)
            snackbarMessage.value = "تمت إضافة المادة الخام بنجاح"
        }
    }

    fun addCustomer(name: String, phone: String, creditLimit: Double, allowDebt: Boolean) {
        val user = _currentUser.value ?: return
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
        if (user.role != UserRole.OWNER) {
            snackbarMessage.value = "إضافة المستخدمين متاحة للمالك فقط"
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
        if (user.role != UserRole.OWNER) {
            snackbarMessage.value = "إدارة المستخدمين متاحة للمالك فقط"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val target = db.userDao().getUserById(userId)
            if (target == null) return@launch
            if (target.role == UserRole.OWNER || target.id == user.id) {
                snackbarMessage.value = "لا يمكن تعطيل حساب المالك"
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
        if (user.role != UserRole.OWNER) {
            snackbarMessage.value = "حذف المستخدمين متاح للمالك فقط"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val target = db.userDao().getUserById(userId)
            if (target == null) return@launch
            if (target.role == UserRole.OWNER || target.id == user.id) {
                snackbarMessage.value = "لا يمكن حذف حساب المالك"
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
            if (user.id != target.id && user.role != UserRole.OWNER) {
                onResult(false, "ليس لديك صلاحية تغيير كلمة السر")
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
        val user = _currentUser.value ?: return
        val shift = currentShift.value
        viewModelScope.launch(Dispatchers.IO) {
            val exp = ExpenseEntity(
                shiftId = shift?.id ?: "",
                userId = user.id,
                userName = user.name,
                title = title,
                category = category,
                amount = amount,
                paidTo = paidTo,
                notes = notes,
                paymentMethod = if (isCash) PaymentMethod.CASH else PaymentMethod.E_WALLET
            )
            db.expenseDao().insertExpense(exp)
            if (shift != null && isCash) {
                db.shiftDao().insertCashMovement(
                    ShiftCashMovementEntity(
                        shiftId = shift.id,
                        type = "EXPENSE",
                        amount = amount,
                        notes = "مصروف: $title"
                    )
                )
            }
            snackbarMessage.value = "تم تسجيل المصروف بنجاح"
        }
    }

    fun createPurchase(supplierId: String, supplierName: String, invoiceNo: String, materialId: String, qty: Double, unit: String, unitPrice: Double) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val item = NewPurchaseItem(
                rawMaterialId = materialId,
                rawMaterialName = "",
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
            snackbarMessage.value = "تم تسجيل فاتورة الشراء وتحديث المخزون"
        }
    }

    fun recordDebtPayment(customerId: String, amount: Double, isCash: Boolean, notes: String) {
        val user = _currentUser.value ?: return
        val shift = currentShift.value
        viewModelScope.launch(Dispatchers.IO) {
            val cust = db.customerDao().getCustomerById(customerId) ?: return@launch
            val tx = DebtTransactionEntity(
                customerId = customerId,
                customerName = cust.name,
                userId = user.id,
                userName = user.name,
                type = "PAYMENT",
                amount = amount,
                balanceAfter = (cust.currentDebt - amount).coerceAtLeast(0.0),
                notes = notes
            )
            db.debtTransactionDao().insertDebtTransaction(tx)
            db.customerDao().updateDebt(customerId, (cust.currentDebt - amount).coerceAtLeast(0.0), CustomerStatus.ACTIVE)
            if (shift != null && isCash) {
                db.shiftDao().insertCashMovement(
                    ShiftCashMovementEntity(
                        shiftId = shift.id,
                        type = "PAYOUT",
                        amount = amount,
                        notes = "سداد دين: ${cust.name}"
                    )
                )
            }
            snackbarMessage.value = "تم تسجيل سداد الدين بنجاح"
        }
    }

    fun recordWaste(materialId: String, qty: Double, unit: String, reason: String, notes: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val mat = db.rawMaterialDao().getRawMaterialById(materialId)
            val wasteReason = when (reason) {
                "EXPIRED" -> WasteReason.EXPIRED
                "DAMAGED" -> WasteReason.DAMAGED
                else -> WasteReason.OTHER
            }
            val waste = WasteTransactionEntity(
                rawMaterialId = materialId,
                rawMaterialName = mat?.name ?: "",
                quantity = qty,
                unit = unit,
                estimatedCost = (mat?.avgCostPerUnit ?: 0.0) * qty,
                reason = wasteReason,
                notes = notes,
                userId = user.id,
                userName = user.name
            )
            db.wasteDao().insertWaste(waste)
            snackbarMessage.value = "تم تسجيل الهدر وتحديث المخزون"
        }
    }

    fun applyStockAdjustment(materialId: String, actualStock: Double, reason: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val mat = db.rawMaterialDao().getRawMaterialById(materialId) ?: return@launch
            val diff = actualStock - mat.currentStock
            db.rawMaterialDao().updateRawMaterial(mat.copy(currentStock = actualStock))
            val adj = StockAdjustmentEntity(
                stockCountId = "",
                rawMaterialId = materialId,
                rawMaterialName = mat.name,
                systemStock = mat.currentStock,
                actualStock = actualStock,
                discrepancy = diff,
                unit = mat.baseUnit,
                reason = reason,
                userId = user.id,
                userName = user.name
            )
            db.stockAdjustmentDao().insertStockAdjustment(adj)
            snackbarMessage.value = "تمت تسوية رصيد المخزون"
        }
    }

    fun voidSale(saleId: String, reason: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val res = salesEngine.voidSale(saleId, reason, user)
            if (res.isSuccess) {
                snackbarMessage.value = "تم إلغاء الفاتورة واستعادة كميات المخزون"
            } else {
                snackbarMessage.value = res.exceptionOrNull()?.message ?: "فشل إلغاء الفاتورة"
            }
        }
    }

    fun exportSalesCsv() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = backupRestoreEngine.exportSalesCsv()
                backupStatusMessage.value = "تم تصدير ملف المبيعات بنجاح في: $path"
                snackbarMessage.value = "تم تصدير ملف المبيعات CSV"
            } catch (e: Exception) {
                backupStatusMessage.value = "فشل التصدير: ${e.message}"
            }
        }
    }

    fun createBackupJson() {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = backupRestoreEngine.createFullBackupJson(user.id, user.name)
                backupStatusMessage.value = "تم إنشاء النسخة الاحتياطية بنجاح في: $path"
                snackbarMessage.value = "تم إنشاء النسخة الاحتياطية الكاملة بنجاح"
            } catch (e: Exception) {
                backupStatusMessage.value = "فشل النسخ الاحتياطي: ${e.message}"
            }
        }
    }

    fun updateWalletTransactionStatus(txId: String, newStatus: WalletTransferStatus, reason: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            walletMatchingEngine.updateTransactionStatus(txId, newStatus, reason, user.id, user.name, user.role.name)
            snackbarMessage.value = "تم تحديث حالة الحوالة بنجاح"
        }
    }

    fun ingestSms(sender: String, body: String) {
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
        if (com.example.gateway.GatewayService.isServerRunning) {
            com.example.gateway.GatewayService.stopGateway(getApplication())
            snackbarMessage.value = "أُرسل طلب إيقاف خادم Gateway"
        } else {
            com.example.gateway.GatewayService.startGateway(getApplication(), gatewayPort.value)
            snackbarMessage.value = "أُرسل طلب تشغيل خادم Gateway"
        }
    }
    fun retryQueueItem(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gatewaySyncEngine.retryNow(id)
            snackbarMessage.value = "جاري إعادة محاولة إرسال العنصر..."
        }
    }

    fun retryAllQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            gatewaySyncEngine.retryAllNow()
            snackbarMessage.value = "جاري إعادة محاولة جميع عناصر طابور الانتظار..."
        }
    }

    fun clearCompletedQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            gatewaySyncEngine.clearCompleted()
            snackbarMessage.value = "تم تنظيف العناصر المكتملة"
        }
    }

    fun getSmsForTransaction(txId: String): Flow<List<WalletSmsEntity>> {
        return db.walletDao().getSmsForTransaction(txId)
    }

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            powerAiEngine.saveGeminiApiKey(key)
            snackbarMessage.value = "تم حفظ مفتاح Gemini API بنجاح"
        }
    }

    fun saveSystemSetting(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (key == "gateway_token") {
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
            snackbarMessage.value = "تم حفظ الإعداد بنجاح"
        }
    }

    suspend fun getSystemSetting(key: String): String? = withContext(Dispatchers.IO) {
        if (key == "gateway_token") {
            com.example.gateway.GatewaySecretStore(getApplication()).getToken()
        } else {
            db.settingsDao().getSetting(key)
        }
    }

    suspend fun askAi(prompt: String): String {
        return powerAiEngine.processQuery(prompt)
    }
}
