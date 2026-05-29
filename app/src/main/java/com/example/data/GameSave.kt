package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_save")
data class GameSave(
    @PrimaryKey val id: Int = 1,
    val level: Int = 1,
    val cash: Int = 0,
    val experience: Int = 0,
    val popularity: Int = 50,
    val maxStock: Int = 10,
    
    // Inventory Counts
    val inventoryChai: Int = 0,
    val inventorySamosa: Int = 0,
    val inventorySpices: Int = 0,
    val inventoryToys: Int = 0,
    val inventorySarees: Int = 0,
    val inventoryGadgets: Int = 0,
    val inventoryJewelry: Int = 0,
    
    // Upgrades
    val hasComfyStool: Boolean = false,
    val hasAromaIncense: Boolean = false,
    val hasAdvertisingChalkboard: Boolean = false,
    val hasStallGuardDog: Boolean = false,
    val hasWholesaleContract: Boolean = false,
    val hasHiredHelper: Boolean = false,
    val hasSecurityCctv: Boolean = false,
    
    // Expansion Stage
    val shopStage: String = "EMPTY_STALL",
    
    // Milestones
    val completedMainQuest: Boolean = false,
    val totalEarnings: Int = 0,
    val lastSavedTimestamp: Long = System.currentTimeMillis()
)
