package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

// Enums representing the stock items
enum class ShopItem(
    val idName: String,
    val displayName: String,
    val baseWholesalePrice: Int,
    val baseRetailPrice: Int,
    val xpValue: Int,
    val emoji: String,
    val unlockLevel: Int
) {
    CHAI("CHAI", "Hot Chai ☕", 10, 25, 5, "☕", 1),
    SAMOSA("SAMOSA", "Samosa Box 🥟", 20, 45, 8, "🥟", 1),
    SPICES("SPICES", "Spices Sack 🌶️", 50, 110, 15, "🌶️", 5),
    TOYS("TOYS", "Handmade Toy 🧸", 150, 300, 30, "🧸", 15),
    SAREE("SAREE", "Silk Saree 🧣", 500, 1100, 80, "🧣", 35),
    GADGET("GADGET", "Smart Phone 📱", 2000, 4200, 250, "📱", 60),
    JEWELS("JEWELS", "Royal Ruby Ring 💍", 8000, 18000, 750, "💍", 85)
}

// Data class for active customers
data class GameCustomer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatarEmoji: String,
    val wantedItem: ShopItem,
    val quantity: Int,
    val initialPatience: Float = 1.0f,
    val patience: Float = 1.0f, // 1.0 down to 0.0
    val isAngry: Boolean = false,
    val angryComplaint: String = ""
)

// Active Thief Alert
data class ThiefAlert(
    val thiefName: String,
    val targetItem: ShopItem,
    val quantity: Int,
    val secondsRemaining: Int
)

// Custom complaints
private val COMPLAINTS = listOf(
    "How long does it take to wrap Chai?!",
    "People behind me are getting served, what about me?!",
    "My hands are freezing waiting for these Samosas!",
    "Are these items handmade or is the helper asleep?",
    "Hurry up, I have a train to catch!"
)

private val EN_NAMES = listOf("Aarav", "Priya", "Rahul", "Ananya", "Rishi", "Meera", "Kabir", "Neha", "Dev", "Sanya", "Ajay", "Kiran")
private val AVATAR_EMOJIS = listOf("👨", "👩", "👱", "👵", "👴", "👧", "👨‍🦰", "👩‍🦱", "👩‍💼", "👨‍💻", "👩‍🎨")

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: GameRepository
    
    // Core game state observed by UI
    private val _gameSave = MutableStateFlow(GameSave())
    val gameSave: StateFlow<GameSave> = _gameSave.asStateFlow()

    // Transient UI State (not stored in DB directly but refreshed dynamically)
    private val _customers = MutableStateFlow<List<GameCustomer>>(emptyList())
    val customers: StateFlow<List<GameCustomer>> = _customers.asStateFlow()

    private val _activeThief = MutableStateFlow<ThiefAlert?>(null)
    val activeThief: StateFlow<ThiefAlert?> = _activeThief.asStateFlow()

    private val _activeComplaint = MutableStateFlow<GameCustomer?>(null)
    val activeComplaint: StateFlow<GameCustomer?> = _activeComplaint.asStateFlow()

    private val _journalLogs = MutableStateFlow<List<String>>(listOf("Welcome to your empty stall! Clean the stall to earn starting ₹ coins!"))
    val journalLogs: StateFlow<List<String>> = _journalLogs.asStateFlow()

    // High Level Game Constants
    val maxLevel = 100

    // Background job for the simulation tick
    private var gameLoopJob: Job? = null

    init {
        val database = GameDatabase.getDatabase(application)
        repository = GameRepository(database.gameDao)
        
        // Load or initialize game save
        viewModelScope.launch {
            val loadedSave = repository.gameSaveFlow.first()
            if (loadedSave == null) {
                // First launch - save empty stall
                val initial = GameSave()
                repository.saveGame(initial)
                _gameSave.value = initial
            } else {
                _gameSave.value = loadedSave
                addLog("Loaded existing shop progress! Level ${loadedSave.level} (${loadedSave.shopStage})")
                
                // Offline progress if helper is active and time passed
                val timePassedMs = System.currentTimeMillis() - loadedSave.lastSavedTimestamp
                if (loadedSave.hasHiredHelper && timePassedMs > 60000) {
                    val minutesOffline = (timePassedMs / 60000).coerceAtMost(180) // cap to 3 hours
                    val multiplier = if (loadedSave.hasAromaIncense) 1.15f else 1.0f
                    val earningsPerMinute = 12 + (loadedSave.level * 4)
                    val offlineEarnings = (minutesOffline * earningsPerMinute * multiplier).toInt()
                    
                    if (offlineEarnings > 0) {
                        addLog("While you were away, your Hired Helper served customers and earned ₹$offlineEarnings!")
                        val updated = loadedSave.copy(
                            cash = loadedSave.cash + offlineEarnings,
                            totalEarnings = loadedSave.totalEarnings + offlineEarnings,
                            lastSavedTimestamp = System.currentTimeMillis()
                        )
                        repository.saveGame(updated)
                        _gameSave.value = updated
                    }
                }
            }
            // Start simulation ticks
            startGameLoop()
        }
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (true) {
                delay(1000) // 1 second tick
                tickGame()
            }
        }
    }

    private suspend fun tickGame() {
        val currentSave = _gameSave.value
        
        // 1. Decay customer patience
        val updatedCustomers = _customers.value.map { customer ->
            val decayRate = if (currentSave.hasComfyStool) 0.02f else 0.04f
            val newPatience = (customer.patience - decayRate).coerceAtLeast(0.0f)
            val isAngryNow = newPatience < 0.25f && !customer.isAngry
            
            if (isAngryNow) {
                customer.copy(
                    patience = newPatience,
                    isAngry = true,
                    angryComplaint = COMPLAINTS.random()
                )
            } else {
                customer.copy(patience = newPatience)
            }
        }
        
        // Check if any angry customer lost all patience and left
        val timedOut = updatedCustomers.filter { it.patience <= 0.0f }
        val remaining = updatedCustomers.filter { it.patience > 0.0f }
        
        if (timedOut.isNotEmpty()) {
            val lostReputation = timedOut.size * 3
            val newPopularity = (currentSave.popularity - lostReputation).coerceIn(1, 100)
            addLog("${timedOut.size} customer(s) left angry! Gossip spread, Popularity -${lostReputation}%")
            
            updateStateAndDb(currentSave.copy(popularity = newPopularity))
        }

        // Trigger complaint popup randomly if someone is angry & no active complaint
        if (_activeComplaint.value == null && remaining.any { it.isAngry }) {
            val angryOne = remaining.filter { it.isAngry }.random()
            _activeComplaint.value = angryOne
        }

        _customers.value = remaining

        // 2. Automated Helper serves customers
        if (currentSave.hasHiredHelper && remaining.isNotEmpty()) {
            // Helper serves first customer in line if we have stock
            val firstCust = remaining.first()
            val availableStock = getStockCount(currentSave, firstCust.wantedItem)
            if (availableStock >= firstCust.quantity) {
                serveCustomer(firstCust)
            }
        }

        // 3. Spawns: New customer spawn rates
        // Popularity increases spawn chance. Active chalkboards or advertising increases it too.
        var spawnChance = 0.15f + (currentSave.popularity / 500.0f)
        if (currentSave.hasAdvertisingChalkboard) spawnChance += 0.10f
        
        if (Random.nextFloat() < spawnChance && _customers.value.size < getQueueLimit(currentSave.shopStage)) {
            spawnNewCustomer(currentSave.level)
        }

        // 4. Random Thief Event (Chance per tick: 2.5%, reduced by high security upgrades)
        var theftChance = 0.025f
        if (currentSave.hasStallGuardDog) theftChance -= 0.01f
        if (currentSave.hasSecurityCctv) theftChance -= 0.012f
        theftChance = theftChance.coerceAtLeast(0.002f)

        if (_activeThief.value == null && Random.nextFloat() < theftChance) {
            triggerThief(currentSave)
        }

        // Update active thief timers
        _activeThief.value?.let { alert ->
            if (alert.secondsRemaining <= 1) {
                // Thief escaped successfully!
                val target = alert.targetItem
                val qtyToSteal = alert.quantity
                val currentQty = getStockCount(currentSave, target)
                val stolenQty = qtyToSteal.coerceAtMost(currentQty)
                
                // Also steal some cash if they didn't have enough stock!
                val cashSnatched = if (stolenQty < qtyToSteal) {
                    ((qtyToSteal - stolenQty) * target.baseRetailPrice / 2).coerceAtMost(currentSave.cash)
                } else {
                    0
                }

                addLog("🚨 Oh no! ${alert.thiefName} ran away with $stolenQty ${target.displayName} and ₹$cashSnatched!")
                
                val updatedSave = deductStock(currentSave, target, stolenQty).copy(
                    cash = (currentSave.cash - cashSnatched).coerceAtLeast(0),
                    popularity = (currentSave.popularity - 5).coerceIn(1, 100)
                )
                
                _activeThief.value = null
                updateStateAndDb(updatedSave)
            } else {
                _activeThief.value = alert.copy(secondsRemaining = alert.secondsRemaining - 1)
            }
        }
        
        // Auto-save timestamp update periodically to track offline progression accurately
        if (Random.nextInt(10) == 0) {
            updateStateAndDb(_gameSave.value.copy(lastSavedTimestamp = System.currentTimeMillis()))
        }
    }

    private fun spawnNewCustomer(level: Int) {
        val availableItems = ShopItem.values().filter { level >= it.unlockLevel }
        if (availableItems.isEmpty()) return

        val chosenItem = availableItems.random()
        val name = EN_NAMES.random()
        val avatar = AVATAR_EMOJIS.random()
        
        // Quantity ranges from 1 to 2 at start, raises with level
        val maxQty = (1 + (level / 12)).coerceAtMost(5)
        val quantity = Random.nextInt(1, maxQty + 1)

        val newCustomer = GameCustomer(
            name = name,
            avatarEmoji = avatar,
            wantedItem = chosenItem,
            quantity = quantity
        )

        _customers.value = _customers.value + newCustomer
    }

    private fun triggerThief(save: GameSave) {
        val ownedItems = ShopItem.values().filter { getStockCount(save, it) > 0 }
        if (ownedItems.isEmpty()) return // Nothing to steal!

        val targetItem = ownedItems.random()
        val thiefName = listOf("Sneaky Babu", "Slick Raju", "Quick Dev", "Chor Pappu").random()
        
        // Level increases active countdown speed
        val baseTiming = 8
        val seconds = (baseTiming - (save.level / 20)).coerceAtLeast(3)

        val alert = ThiefAlert(
            thiefName = thiefName,
            targetItem = targetItem,
            quantity = Random.nextInt(1, 3),
            secondsRemaining = seconds
        )

        // 5. Upgrade passive defense check:
        // CCTV stops 90%, Guard Dog stops 40%
        var autoCatchChance = 0.0f
        if (save.hasSecurityCctv) autoCatchChance += 0.90f
        if (save.hasStallGuardDog) autoCatchChance += 0.40f

        if (Random.nextFloat() < autoCatchChance) {
            // Caught passively!
            val rewardMoney = Random.nextInt(10, 30) + (save.level * 2)
            addLog("🐕 GUARD: Caught $thiefName red-handed! Earned ₹$rewardMoney tip from onlookers!")
            viewModelScope.launch {
                updateStateAndDb(
                    save.copy(
                        cash = save.cash + rewardMoney,
                        popularity = (save.popularity + 2).coerceAtMost(100)
                    )
                )
            }
        } else {
            _activeThief.value = alert
            addLog("🚨 THIEF WARNING: $thiefName is pocketing items! Tap immediately!")
        }
    }

    fun catchThiefManual() {
        val alert = _activeThief.value ?: return
        _activeThief.value = null

        val save = _gameSave.value
        val bounty = 15 + (save.level * 3)
        val repReward = 3
        
        addLog("💥 BAM! You caught ${alert.thiefName}! Recovered items & got a bounty of ₹$bounty!")
        
        viewModelScope.launch {
            updateStateAndDb(
                save.copy(
                    cash = save.cash + bounty,
                    popularity = (save.popularity + repReward).coerceAtMost(100),
                    experience = save.experience + 10
                )
            ).also {
                checkLevelUp()
            }
        }
    }

    // Sell stock to customer
    fun serveCustomer(customer: GameCustomer) {
        val save = _gameSave.value
        val stockCount = getStockCount(save, customer.wantedItem)
        
        if (stockCount < customer.quantity) {
            addLog("❌ Cannot serve ${customer.name}: need ${customer.quantity} but only have $stockCount of ${customer.wantedItem.displayName}!")
            return
        }

        // Calculate transaction
        val basePrice = customer.wantedItem.baseRetailPrice
        val quantity = customer.quantity
        var priceGained = basePrice * quantity

        // Incense upgrade gives bonus tips
        if (save.hasAromaIncense) {
            val tip = (priceGained * 0.15f).toInt()
            priceGained += tip
        }

        val totalEarnings = priceGained
        val experienceGained = customer.wantedItem.xpValue * quantity

        // Update save
        val updatedStockSave = deductStock(save, customer.wantedItem, quantity)
        val finalSave = updatedStockSave.copy(
            cash = save.cash + totalEarnings,
            experience = save.experience + experienceGained,
            totalEarnings = save.totalEarnings + totalEarnings,
            popularity = (save.popularity + 1).coerceAtMost(100)
        )

        // Remote patient logs
        _customers.value = _customers.value.filter { it.id != customer.id }
        addLog("🤝 Served ${customer.name}: Sold $quantity ${customer.wantedItem.displayName} for ₹$totalEarnings! (+${experienceGained} XP)")

        viewModelScope.launch {
            updateStateAndDb(finalSave)
            checkLevelUp()
        }
    }

    fun refuseCustomer(customer: GameCustomer) {
        _customers.value = _customers.value.filter { it.id != customer.id }
        val save = _gameSave.value
        val updatedPopularity = (save.popularity - 1).coerceAtLeast(1)
        
        addLog("⚠️ Refused order from ${customer.name}. Popularity decreased slightly.")
        viewModelScope.launch {
            updateStateAndDb(save.copy(popularity = updatedPopularity))
        }
    }

    // Handles the Angry customer complaints
    fun resolveComplaint(refund: Boolean, freeTea: Boolean) {
        val angryCustomer = _activeComplaint.value ?: return
        _activeComplaint.value = null

        val currentSave = _gameSave.value
        viewModelScope.launch {
            if (refund) {
                // Costs ₹5
                if (currentSave.cash >= 5) {
                    addLog("🤝 Complimented ${angryCustomer.name} with ₹5 refund. They smiled and left positive press!")
                    updateStateAndDb(
                        currentSave.copy(
                            cash = currentSave.cash - 5,
                            popularity = (currentSave.popularity + 4).coerceAtMost(100)
                        )
                    )
                } else {
                    addLog("❌ You couldn't afford a refunder! ${angryCustomer.name} screamed!")
                    updateStateAndDb(currentSave.copy(popularity = (currentSave.popularity - 4).coerceAtLeast(1)))
                }
            } else if (freeTea) {
                // Costs ₹2
                if (currentSave.cash >= 2) {
                    val lucky = Random.nextBoolean()
                    if (lucky) {
                        addLog("☕ Gave free hot tea! ${angryCustomer.name} felt respected. Popularity +2!")
                        updateStateAndDb(
                            currentSave.copy(
                                cash = currentSave.cash - 2,
                                popularity = (currentSave.popularity + 2).coerceAtMost(100)
                            )
                        )
                    } else {
                        addLog("☕ Gave free tea but ${angryCustomer.name} took it and still left grumpy!")
                        updateStateAndDb(currentSave.copy(cash = currentSave.saveCashStateDecreased(2)))
                    }
                } else {
                    addLog("❌ No tea available! Customer stormed out.")
                }
            } else {
                // Ban customer
                addLog("🚫 Banned ${angryCustomer.name} from the stall! \"Get out!\" Popularity -5%!")
                updateStateAndDb(currentSave.copy(popularity = (currentSave.popularity - 5).coerceAtLeast(1)))
            }

            // Remove consumer from line
            _customers.value = _customers.value.filter { it.id != angryCustomer.id }
        }
    }

    // Buy inventory item wholesale
    fun buyWholesaleStock(item: ShopItem, count: Int) {
        val save = _gameSave.value
        val baseCost = item.baseWholesalePrice
        var singleCost = baseCost
        
        // 15% wholesale contract discount
        if (save.hasWholesaleContract) {
            singleCost = (baseCost * 0.85f).toInt()
        }

        val totalCost = singleCost * count
        
        // Space validations
        val spaceLeft = save.maxStock - getTotalInventoryCount(save)
        if (spaceLeft < count) {
            addLog("❌ Not enough storage space! Max Capacity is ${save.maxStock}. Pack inventory or upgrade stall!")
            return
        }

        if (save.cash < totalCost) {
            addLog("❌ Out of budget! Need ₹$totalCost but only have ₹${save.cash}.")
            return
        }

        val updatedStockSave = addStock(save, item, count)
        val finalSave = updatedStockSave.copy(
            cash = save.cash - totalCost
        )

        addLog("📦 Purchased $count bulk ${item.displayName} wholesale for ₹$totalCost!")
        viewModelScope.launch {
            updateStateAndDb(finalSave)
        }
    }

    // Clean stall mini-game
    fun cleanStallOddJob() {
        val save = _gameSave.value
        val coinEarned = Random.nextInt(2, 8)
        
        val finalSave = save.copy(
            cash = save.cash + coinEarned,
            experience = save.experience + 5
        )

        addLog("🧹 Swept and polished the empty stall: Found ₹$coinEarned inside floorboards! (+5 XP)")
        viewModelScope.launch {
            updateStateAndDb(finalSave)
            checkLevelUp()
        }
    }

    // Advertise stall manually
    fun runAdvertisingBoost() {
        val save = _gameSave.value
        val adCost = 10 + (save.level * 2)
        if (save.cash < adCost) {
            addLog("❌ Ad campaign costs ₹$adCost. You are short on cash.")
            return
        }

        viewModelScope.launch {
            val boostPop = (save.popularity + 15).coerceAtMost(100)
            updateStateAndDb(save.copy(cash = save.cash - adCost, popularity = boostPop))
            addLog("📣 Distributed flyers and shouted deals! Popularity boosted (+15%)!")
            
            // Queue immediate extra spawns
            repeat(3) {
                delay(1200)
                if (_customers.value.size < getQueueLimit(_gameSave.value.shopStage)) {
                    spawnNewCustomer(_gameSave.value.level)
                }
            }
        }
    }

    // Purchase shop upgrade
    fun buyUpgrade(upgradeName: String, cost: Int) {
        val save = _gameSave.value
        if (save.cash < cost) {
            addLog("❌ Cannot buy $upgradeName: Needs ₹$cost, but you have ₹${save.cash}!")
            return
        }

        val updatedSave = when (upgradeName) {
            "Comfy Stool" -> save.copy(cash = save.cash - cost, hasComfyStool = true)
            "Aroma Incense" -> save.copy(cash = save.cash - cost, hasAromaIncense = true)
            "Advertising Chalkboard" -> save.copy(cash = save.cash - cost, hasAdvertisingChalkboard = true)
            "Stall Guard Dog" -> save.copy(cash = save.cash - cost, hasStallGuardDog = true)
            "Wholesale Contract" -> save.copy(cash = save.cash - cost, hasWholesaleContract = true)
            "Hired Helper" -> save.copy(cash = save.cash - cost, hasHiredHelper = true)
            "Security CCTV Camera" -> save.copy(cash = save.cash - cost, hasSecurityCctv = true)
            else -> save
        }

        addLog("🎉 Upgraded Successfully: Unlocked \"$upgradeName\"!")
        viewModelScope.launch {
            updateStateAndDb(updatedSave)
        }
    }

    // Expand Stall / Shop Locations
    fun expandShopStage(nextStage: String, cost: Int, minLevelRequired: Int) {
        val save = _gameSave.value
        if (save.level < minLevelRequired) {
            addLog("❌ Expansion Locked: Requires Shop Level $minLevelRequired!")
            return
        }
        if (save.cash < cost) {
            addLog("❌ Budget shortfall! Need ₹$cost for location buildout.")
            return
        }

        val newMaxStock = when (nextStage) {
            "BAZAAR" -> 35
            "BRICK" -> 80
            "SHOWROOM" -> 180
            "MALL" -> 450
            else -> save.maxStock
        }

        val updated = save.copy(
            cash = save.cash - cost,
            shopStage = nextStage,
            maxStock = newMaxStock,
            popularity = 90
        )

        val stageTitle = getStageTitle(nextStage)
        addLog("🏗️ MEGA MILESTONE: Expanded to **$stageTitle**! Max Warehousing capacity increased to $newMaxStock, popularity surged!")
        
        viewModelScope.launch {
            updateStateAndDb(updated)
        }
    }

    // Master Reset Game Save
    fun triggerHardReset() {
        viewModelScope.launch {
            _customers.value = emptyList()
            _activeThief.value = null
            _activeComplaint.value = null
            _journalLogs.value = listOf("Shop resetted back to scratch! Sweep floor to begin again.")
            
            repository.resetGame()
            val fresh = repository.getGameSaveSync() ?: GameSave()
            _gameSave.value = fresh
        }
    }

    // Level-up loop calculations
    private suspend fun checkLevelUp() {
        var save = _gameSave.value
        var curLevel = save.level
        var curXp = save.experience
        var xpRequired = getXpRequired(curLevel)
        var leveledUp = false

        while (curXp >= xpRequired && curLevel < maxLevel) {
            curXp -= xpRequired
            curLevel += 1
            xpRequired = getXpRequired(curLevel)
            leveledUp = true
        }

        if (leveledUp) {
            val isVictory = curLevel >= 100 && !save.completedMainQuest
            val cashBonus = curLevel * 40
            
            save = save.copy(
                level = curLevel,
                experience = curXp,
                cash = save.cash + cashBonus,
                completedMainQuest = save.completedMainQuest || isVictory
            )
            
            addLog("⚡ LEVEL UP! You reached Shopkeeper Level $curLevel! Bonus ₹$cashBonus awarded!")
            
            if (isVictory) {
                addLog("🏆 UNBELIEVABLE GRIND! You conquered Level 100 Shopkeeper Master! Feel proud of your retail legacy!")
            }
            
            updateStateAndDb(save)
        }
    }

    // Direct helper mapping of Room fields
    fun getStockCount(save: GameSave, item: ShopItem): Int {
        return when (item) {
            ShopItem.CHAI -> save.inventoryChai
            ShopItem.SAMOSA -> save.inventorySamosa
            ShopItem.SPICES -> save.inventorySpices
            ShopItem.TOYS -> save.inventoryToys
            ShopItem.SAREE -> save.inventorySarees
            ShopItem.GADGET -> save.inventoryGadgets
            ShopItem.JEWELS -> save.inventoryJewelry
        }
    }

    fun getTotalInventoryCount(save: GameSave): Int {
        return save.inventoryChai + save.inventorySamosa + save.inventorySpices +
                save.inventoryToys + save.inventorySarees + save.inventoryGadgets +
                save.inventoryJewelry
    }

    private fun addStock(save: GameSave, item: ShopItem, count: Int): GameSave {
        return when (item) {
            ShopItem.CHAI -> save.copy(inventoryChai = save.inventoryChai + count)
            ShopItem.SAMOSA -> save.copy(inventorySamosa = save.inventorySamosa + count)
            ShopItem.SPICES -> save.copy(inventorySpices = save.inventorySpices + count)
            ShopItem.TOYS -> save.copy(inventoryToys = save.inventoryToys + count)
            ShopItem.SAREE -> save.copy(inventorySarees = save.inventorySarees + count)
            ShopItem.GADGET -> save.copy(inventoryGadgets = save.inventoryGadgets + count)
            ShopItem.JEWELS -> save.copy(inventoryJewelry = save.inventoryJewelry + count)
        }
    }

    private fun deductStock(save: GameSave, item: ShopItem, count: Int): GameSave {
        return when (item) {
            ShopItem.CHAI -> save.copy(inventoryChai = (save.inventoryChai - count).coerceAtLeast(0))
            ShopItem.SAMOSA -> save.copy(inventorySamosa = (save.inventorySamosa - count).coerceAtLeast(0))
            ShopItem.SPICES -> save.copy(inventorySpices = (save.inventorySpices - count).coerceAtLeast(0))
            ShopItem.TOYS -> save.copy(inventoryToys = (save.inventoryToys - count).coerceAtLeast(0))
            ShopItem.SAREE -> save.copy(inventorySarees = (save.inventorySarees - count).coerceAtLeast(0))
            ShopItem.GADGET -> save.copy(inventoryGadgets = (save.inventoryGadgets - count).coerceAtLeast(0))
            ShopItem.JEWELS -> save.copy(inventoryJewelry = (save.inventoryJewelry - count).coerceAtLeast(0))
        }
    }

    fun getXpRequired(level: Int): Int {
        return level * 100 // Level 1 needs 100 XP, Level 2 needs 200 XP, etc.
    }

    private fun GameSave.saveCashStateDecreased(amount: Int): Int {
        return (this.cash - amount).coerceAtLeast(0)
    }

    private suspend fun updateStateAndDb(newSave: GameSave) {
        _gameSave.value = newSave
        repository.saveGame(newSave)
    }

    private fun addLog(message: String) {
        val currentLogs = _journalLogs.value.toMutableList()
        currentLogs.add(0, message) // newer logs at top
        if (currentLogs.size > 25) {
            currentLogs.removeLast()
        }
        _journalLogs.value = currentLogs
    }

    fun getStageTitle(stage: String): String {
        return when (stage) {
            "EMPTY_STALL" -> "Wooden Stall"
            "BAZAAR" -> "Bazaar Canopy"
            "BRICK" -> "Brick Shop"
            "SHOWROOM" -> "High Street Showroom"
            "MALL" -> "Mega Mall Plaza"
            else -> "Market Stall"
        }
    }

    fun getQueueLimit(stage: String): Int {
        return when (stage) {
            "EMPTY_STALL" -> 3
            "BAZAAR" -> 4
            "BRICK" -> 5
            "SHOWROOM" -> 6
            "MALL" -> 8
            else -> 3
        }
    }
}
