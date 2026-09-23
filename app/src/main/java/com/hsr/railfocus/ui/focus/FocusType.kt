package com.hsr.railfocus.ui.focus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import com.hsr.railfocus.domain.model.FocusType

typealias FocusType = com.hsr.railfocus.domain.model.FocusType

val FocusType.color: Color
    get() = Color(colorHex)

val FocusType.containerColor: Color
    get() = Color(containerColorHex)

val FocusType.icon: ImageVector
    get() = FocusTypeIcons.ICON_MAP[iconName] ?: Icons.Default.Lightbulb

val FocusType.Companion.ICON_MAP: Map<String, ImageVector>
    get() = FocusTypeIcons.ICON_MAP

/**
 * 专注类型图标映射表与 UI 扩展
 */
object FocusTypeIcons {
    val ICON_MAP: Map<String, ImageVector> = mapOf(
        "Code" to Icons.Default.Code,
        "Learn" to Icons.Default.School,
        "Work" to Icons.Default.Work,
        "Read" to Icons.AutoMirrored.Filled.MenuBook,
        "Research" to Icons.Default.Lightbulb,
        "Game" to Icons.Default.SportsEsports,
        "Food" to Icons.Default.Restaurant,
        "Fastfood" to Icons.Default.Fastfood,
        "Pizza" to Icons.Default.LocalPizza,
        "Movie" to Icons.Default.Movie,
        "Music" to Icons.Default.MusicNote,
        "Art" to Icons.Default.Brush,
        "Palette" to Icons.Default.Palette,
        "Chat" to Icons.AutoMirrored.Filled.Chat,
        "Fitness" to Icons.Default.FitnessCenter,
        "Yoga" to Icons.Default.SelfImprovement,
        "Mind" to Icons.Default.Psychology,
        "Heart" to Icons.Default.VolunteerActivism,
        "Travel" to Icons.Default.TravelExplore,
        "Explore" to Icons.Default.Explore,
        "Public" to Icons.Default.Public,
        "Coffee" to Icons.Default.Coffee,
        "Pet" to Icons.Default.Pets,
        "Phone" to Icons.Default.Phone,
        "Camera" to Icons.Default.PhotoCamera,
        "Photography" to Icons.Default.CameraAlt,
        "Laptop" to Icons.Default.Laptop,
        "Computer" to Icons.Default.Computer,
        "Terminal" to Icons.Default.Terminal,
        "Smartphone" to Icons.Default.Smartphone,
        "Shop" to Icons.Default.ShoppingBag,
        "Bike" to Icons.AutoMirrored.Filled.DirectionsBike,
        "Run" to Icons.AutoMirrored.Filled.DirectionsRun,
        "Ball" to Icons.Default.SportsBasketball,
        "Soccer" to Icons.Default.SportsSoccer,
        "Health" to Icons.Default.Favorite,
        "Star" to Icons.Default.Star,
        "Plane" to Icons.Default.Flight,
        "Train" to Icons.Default.Train,
        "Car" to Icons.Default.DirectionsCar,
        "Bus" to Icons.Default.DirectionsBus,
        "Boat" to Icons.Default.DirectionsBoat,
        "Home" to Icons.Default.Home,
        "Web" to Icons.Default.Language,
        "Pen" to Icons.Default.Edit,
        "Math" to Icons.Default.Calculate,
        "Eye" to Icons.Default.Visibility,
        "Magic" to Icons.Default.AutoAwesome,
        "Flower" to Icons.Default.LocalFlorist,
    )
}

fun FocusType(
    id: String,
    displayName: String,
    icon: ImageVector,
    iconName: String,
    color: Color,
    containerColor: Color,
    isRemovable: Boolean = true,
): FocusType = FocusType(
    id = id,
    displayName = displayName,
    iconName = iconName,
    colorHex = color.toArgb(),
    containerColorHex = containerColor.toArgb(),
    isRemovable = isRemovable,
)
