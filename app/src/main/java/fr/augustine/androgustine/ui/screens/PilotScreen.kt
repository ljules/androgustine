package fr.augustine.androgustine.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration.Companion.Underline
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.augustine.androgustine.R
import fr.augustine.androgustine.data.imports.readSimAugustineImport
import fr.augustine.androgustine.ui.components.CircuitView
import fr.augustine.androgustine.ui.theme.OxaniumFontFamily
import fr.augustine.androgustine.ui.theme.ShellGrey
import fr.augustine.androgustine.ui.theme.ShellOrange
import fr.augustine.androgustine.ui.theme.FlagGreen
import fr.augustine.androgustine.viewmodel.RaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun PilotScreen(viewModel: RaceViewModel = viewModel()) {

    // Désactivation de la veille de l'écran quand la page est au 1er plan :
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val coroutineScope = rememberCoroutineScope()
    var importMessage by remember { mutableStateOf<String?>(null) }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.startHeartRateTracking()
    }

    LaunchedEffect(Unit) {
        bluetoothPermissionLauncher.launch(requiredBluetoothPermissions())
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        importMessage = "Import en cours..."
        coroutineScope.launch {
            importMessage = runCatching {
                withContext(Dispatchers.IO) {
                    readSimAugustineImport(contentResolver, uri)
                }
            }.fold(
                onSuccess = {
                    viewModel.useImportedCircuitIfAvailable()
                    null
                },
                onFailure = {
                    "Import impossible. Vérifie le fichier JSON."
                }
            )
        }
    }

    // Effet qui s'exécute quand l'écran est affiché
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Nettoyage : on retire le flag quand on quitte cet écran
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val hasImportedStrategy = uiState.circuitSource == "JSON Sim-Augustine"

    // Style de base pour la police :
    val textStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = OxaniumFontFamily,
        color = Color.White
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // Fond d'écran avec tachymètre
        Image(
            painter = painterResource(id = R.drawable.background_landscape_dark),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Grille principale :
        Column(modifier = Modifier.fillMaxSize()) {

            // --- LIGNE 1 (Tours, Circuit, Temps) ---
            Row(modifier = Modifier.weight(0.4f).fillMaxWidth()) {
                // Zone Tours (Haut Gauche)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ico_loop), null, Modifier.size(38.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.lapProgress,
                            style= textStyle.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            )
                    }
                    Spacer(Modifier.height(10.dp))
                    // TODO : Conditionner l'affichage de l'image ico_stands et du texte stands selon l'état de pitStopRequest,
                    // si true afficher icône et le texte, sinon ne rien afficher.
                    Image(painterResource(R.drawable.ico_stands), null, Modifier.size(60.dp))
                    Text(
                        text = "stands",
                        style = textStyle.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Zone Circuit (Centre)
                Box(
                    modifier = Modifier.weight(1.5f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasImportedStrategy) {
                        CircuitView(
                            points = uiState.circuitPoints,
                            currentLat = uiState.currentLat,
                            currentLon = uiState.currentLon,
                            strategyIntervals = uiState.activeStrategyIntervals,
                            ghostPoint = uiState.ghostPoint,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(
                                onClick = {
                                    filePickerLauncher.launch(
                                        arrayOf(
                                            "text/*",
                                            "application/json",
                                            "text/json"
                                        )
                                    )
                                }
                            ) {
                                Text("Importer stratégie Sim-Augustine")
                            }
                            importMessage?.let { message ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = message,
                                    style = textStyle.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                // Zone Chrono / Accélérer (Haut Droite)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    )
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ico_timer), null, Modifier.size(38.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.timer,
                            style = textStyle.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Image(painterResource(R.drawable.ico_speed_meter), null, Modifier.size(60.dp))
                    Text(
                        // TODO: Le texte doit prendre la valeur et la couleur dépend de la variable PilotPaceInstruction :
                        //  "accélérer" en rouge si ACCELERATE, "maintenir" en vert si MAINTAIN, "ralentir" en bleu si SLOW_DOWN
                        text = "accélérer",
                        style = textStyle.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // --- LIGNE 2 (Vitesse Centrale) ---
            // TODO : Découper en 3  colonnes, colonne 1 en 3 lignes info météo, colonne 2 vitesse, colone 3 en 2 lignes rythme cardiaque
            Row() {
                // Colonne des infos météo :
                Column(
                    modifier = Modifier
                        .weight(0.3f)
                        .padding(start = 10.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Entête météo
                    Text(
                        text = "Météo ☀ :",
                        style = textStyle.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ShellOrange,
                            textDecoration = Underline,
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    // Valeurs météo :
                    Column(
                        modifier = Modifier.padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "🌡 ${formatNullable(uiState.weatherTemperatureC)} °C",
                            style = textStyle.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )

                        Text(
                            text = " \uD83D\uDCA8 ${formatNullable(uiState.weatherWindKmh)} km/h",
                            style = textStyle.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )

                        Text(
                            text = "💧  ${uiState.weatherRainProbability?.toString() ?: "--"}%",
                            style = textStyle.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                // Colonne de la vitesse :
//                Column(
//
//                ) {
                    Box(
                        modifier = Modifier.weight(0.4f).fillMaxWidth().padding(end = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.Bottom, ) {
                            Text(
                                text = "%.0f".format(uiState.speed),
                                style = textStyle.copy(
                                    fontSize = 130.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-2).sp
                                )
                            )
                            Column(Modifier.padding(bottom = 20.dp, start = 8.dp)) {
                                Text(
                                    text = "(vit. GPS)",
                                    color = ShellGrey,
                                    style = textStyle.copy(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                )
                                Text(
                                    text = "km/h",
                                    color = ShellOrange,
                                    style = textStyle.copy(
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                //}

                // Colonne de la fréquence cardiaque :
                Column(
                    modifier = Modifier.padding(end = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(painterResource(R.drawable.heart), null, Modifier.size(60.dp))
                    Text(
                        text = "${uiState.heartRateBpm?.toString() ?: "--"} bpm",
                        style = textStyle.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // --- LIGNE 3 (Drapeaux / Instructions) ---
            // TODO: Découper en 3 colonnes : A gauche la consommation, au centre le statut de course avec les icônes des drapeaux, à droite info de l'application
            Row(
                modifier = Modifier.weight(0.2f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // TODO: Vérifier que l'affichage des drapeaux est bien conditionné à la variable raceStatusInstruction:
                // Si RACE alors ico_on_track, si NO_OVERTAKING alors flag_yellow et si STOP alors flag_red
                Image(
                    //painter = painterResource(id = if (uiState.flag == "jaune") R.drawable.flag_yellow else R.drawable.flag_red),
                    painter = painterResource(id = when (uiState.flag) {
                        "jaune" -> R.drawable.flag_yellow
                        "rouge" -> R.drawable.flag_red
                        else -> R.drawable.ico_on_track
                    }),
                    contentDescription = null,
                    modifier = Modifier.size(50.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = uiState.flag,
                    style = textStyle.copy(
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlagGreen
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .widthIn(min = 220.dp, max = 420.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Stratégie active :
            Text(
                text = "Stratégie active : ${uiState.activeStrategyName}",
                style = textStyle.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(4.dp))
            // Etat du véhicule ghost :
            Text(
                text = "Ghost : ${if (uiState.ghostPoint != null) "active" else "inactive"}",
                style = textStyle.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            // Affichage de l'écart véhicule <-> ghost car :
            uiState.ghostDeltaDistanceM?.let { delta ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Delta distance : ${formatNumber(delta.toDouble())} m",
                    style = textStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

private fun formatNumber(value: Double): String =
    String.format(Locale.US, "%.2f", value)

private fun formatNullable(value: Float?): String =
    value?.let { String.format(Locale.US, "%.1f", it) } ?: "--"

private fun requiredBluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
