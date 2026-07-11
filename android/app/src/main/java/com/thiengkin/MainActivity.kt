package com.thiengkin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thiengkin.data.LocationRepository
import com.thiengkin.ui.screens.EmptyOfflineScreen
import com.thiengkin.ui.screens.FavoritesScreen
import com.thiengkin.ui.screens.LoadingScreen
import com.thiengkin.ui.screens.nearme.NearMeScreen
import com.thiengkin.ui.screens.travel.RestaurantDetailScreen
import com.thiengkin.ui.screens.travel.RouteResultScreen
import com.thiengkin.ui.screens.travel.TravelHomeScreen
import com.thiengkin.ui.theme.ThiengKinTheme

/**
 * MainActivity — single Compose host
 *
 * Navigation:
 * - BottomBar: 3 primary modes (Travel / Near-me / Favorites)
 * - Sub-screens stack on top: RouteResult, RestaurantDetail
 * - Special states: Loading, EmptyOffline (rendered conditionally)
 *
 * GPS permission flow:
 * - [requestPermissionLauncher] → รับผลจาก system dialog → เรียก LocationRepository
 * - [requestLocationPermission] → entry point จาก Composable
 *   - ถ้ามี permission แล้ว → เรียก [LocationRepository.requestLocation] ทันที
 *   - ถ้ายังไม่มี → launch system dialog
 */
class MainActivity : ComponentActivity() {

    private lateinit var locationRepository: LocationRepository

    /**
     * System permission dialog callback.
     * เรียกหลังจาก user กด Allow / Deny ใน dialog
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        Log.i(TAG, "FINE_LOCATION permission result: $isGranted")
        if (isGranted) {
            locationRepository.requestLocation()
        } else {
            locationRepository.markDenied()
        }
    }

    /**
     * Public entry point — Composable เรียกฟังก์ชันนี้เมื่อต้องการตำแหน่ง
     *
     * - ถ้ามี permission แล้ว → เรียก [LocationRepository.requestLocation] ตรงๆ (no-op dialog)
     * - ถ้ายังไม่มี → launch system permission dialog
     */
    fun requestLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "requestLocationPermission — already granted=$granted")
        if (granted) {
            locationRepository.requestLocation()
        } else {
            try {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } catch (e: Exception) {
                Log.e(TAG, "launch permission dialog failed", e)
                locationRepository.markDenied()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationRepository = ThiengKinApp.get().locationRepository

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ThiengKinTheme(
                darkTheme = isSystemInDarkTheme(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(
                        onRequestLocationPermission = { requestLocationPermission() },
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

// === Routes ===
sealed class Route(val path: String, val title: String) {
    data object TravelHome : Route("home", "ทาง")
    data object NearMe : Route("near_me", "ใกล้ฉัน")
    data object Favorites : Route("favorites", "รายการโปรด")
    data object RouteResult : Route("route_result", "จุดแวะ")
    data object Loading : Route("loading", "กำลังโหลด")
    data object EmptyOffline : Route("empty_offline", "Offline")

    object RestaurantDetail {
        const val PATH = "restaurant/{id}"
        fun build(id: String) = "restaurant/$id"
    }
}

private data class BottomTab(
    val route: Route,
    val icon: ImageVector,
)

private val bottomTabs = listOf(
    BottomTab(Route.TravelHome, Icons.Filled.Navigation),
    BottomTab(Route.NearMe, Icons.Filled.LocationOn),
    BottomTab(Route.Favorites, Icons.Filled.Bookmark),
)

@Composable
private fun AppRoot(
    onRequestLocationPermission: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    val isLoading = false  // Phase 1.5: bind to actual loading state from a ViewModel
    val isOffline = false  // Phase 1.5: bind to ConnectivityManager

    when {
        isLoading -> LoadingScreen()
        isOffline -> EmptyOfflineScreen(cachedCount = 47)
        else -> AppShell(
            navController = navController,
            currentRoute = currentRoute,
            onNavigate = { lat, lng, label -> openInGoogleMaps(context, lat, lng, label) },
            onRequestLocationPermission = onRequestLocationPermission,
        )
    }
}

@Composable
private fun AppShell(
    navController: NavHostController,
    currentRoute: String?,
    onNavigate: (Double, Double, String) -> Unit,
    onRequestLocationPermission: () -> Unit = {},
) {
    val showBottomBar = currentRoute in listOf(
        Route.TravelHome.path,
        Route.NearMe.path,
        Route.Favorites.path,
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomBar(currentRoute = currentRoute, navController = navController)
            }
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.TravelHome.path,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Route.TravelHome.path) {
                TravelHomeScreen(
                    onRestaurantClick = { id ->
                        navController.navigate(Route.RestaurantDetail.build(id))
                    },
                    onNavigate = onNavigate,
                    onRouteClick = {
                        navController.navigate(Route.RouteResult.path)
                    },
                    onRequestLocationPermission = onRequestLocationPermission,
                )
            }
            composable(Route.NearMe.path) {
                NearMeScreen(
                    onRestaurantClick = { id ->
                        navController.navigate(Route.RestaurantDetail.build(id))
                    },
                    onNavigate = onNavigate,
                    onRequestLocationPermission = onRequestLocationPermission,
                )
            }
            composable(Route.Favorites.path) {
                FavoritesScreen(
                    onRestaurantClick = { id ->
                        navController.navigate(Route.RestaurantDetail.build(id))
                    },
                    onNavigate = onNavigate,
                )
            }
            composable(Route.RouteResult.path) {
                RouteResultScreen(
                    onRestaurantClick = { id ->
                        navController.navigate(Route.RestaurantDetail.build(id))
                    },
                    onNavigate = onNavigate,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Route.RestaurantDetail.PATH,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry: NavBackStackEntry ->
                val id = entry.arguments?.getString("id").orEmpty()
                RestaurantDetailScreen(
                    restaurantId = id,
                    onBack = { navController.popBackStack() },
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    currentRoute: String?,
    navController: NavHostController,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        bottomTabs.forEach { tab ->
            val selected = currentRoute == tab.route.path
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(tab.route.path) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.route.title,
                    )
                },
                label = {
                    Text(
                        text = tab.route.title,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                alwaysShowLabel = true,
            )
        }
    }
}

/**
 * Open Google Maps with directions to (lat, lng).
 * Falls back to generic geo: URI if Google Maps app is not installed.
 */
private fun openInGoogleMaps(
    context: Context,
    lat: Double,
    lng: Double,
    label: String,
) {
    val mapsIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=$lat,$lng&mode=d"),
    ).apply { setPackage("com.google.android.apps.maps") }
    if (mapsIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(mapsIntent)
        return
    }
    val fallback = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"),
    )
    context.startActivity(fallback)
}
